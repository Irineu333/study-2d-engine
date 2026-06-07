package com.neoutils.engine.scene

import com.neoutils.engine.dx.Log
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.render.Texture
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Cycles the frames of a **horizontal sprite sheet** over time, drawing the
 * current frame centered on the node's local origin (Godot `AnimatedSprite2D`
 * convention). The sheet is `frameCount` equally-sized frames laid side by side:
 * each frame is `texture.width / frameCount` wide and `texture.height` tall.
 *
 * The engine — not the script — advances the frame: [onProcess] accumulates
 * frame-time and steps [currentFrame] at [fps] frames/second. With [loop] off it
 * stops on the last frame and clears [playing]. The gameplay script only decides
 * *which* animation (by swapping [texturePath] + [frameCount]) and *when* to play.
 *
 * With no texture backend (`tree.textures == null`, e.g. headless tests) the
 * handle stays `null`: [onDraw] is a no-op and [localBounds] is empty — the
 * sprite is invisible but never throws.
 */
@Serializable
open class AnimatedSprite2D : Node2D() {

    /** Classpath path of the sheet asset (e.g. `"demos/sprites/run.png"`). */
    @Inspect
    var texturePath: String = ""

    /** Number of equally-sized frames laid side by side in the sheet (>= 1). */
    @Inspect
    var frameCount: Int = 1

    /** Frame advance rate, frames per second. */
    @Inspect
    var fps: Float = 10f

    /** When `true`, wraps to frame 0 after the last; otherwise stops on it. */
    @Inspect
    var loop: Boolean = true

    /** When `true`, the engine advances frames; flip to pause in place. */
    @Inspect
    var playing: Boolean = true

    /** Index of the frame currently shown. */
    @Inspect
    var currentFrame: Int = 0

    /** Mirrors the frame horizontally (visual only — never a negative scale). */
    @Inspect
    var flipH: Boolean = false

    @Transient
    private var texture: Texture? = null

    // Tracks the path the current handle was resolved from so a runtime
    // texturePath swap (e.g. idle -> run) re-resolves through the cached backend.
    @Transient
    private var resolvedPath: String? = null

    // Frame-time carried between advances; one frame fires per 1/fps seconds.
    @Transient
    private var elapsed: Float = 0f

    /** Starts (or resumes) frame advance. */
    fun play() {
        playing = true
    }

    /** Pauses frame advance, holding the current frame. */
    fun pause() {
        playing = false
    }

    override fun onEnter() {
        super.onEnter()
        ensureTexture()
    }

    override fun onExit() {
        // Drop only the local reference — the backend owns the cache and frees
        // every texture on tree.stop(); disposing here would break other nodes
        // sharing the same cached handle.
        texture = null
        resolvedPath = null
        super.onExit()
    }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        if (!playing || fps <= 0f || frameCount <= 1) return
        elapsed += dt
        val frameDuration = 1f / fps
        while (elapsed >= frameDuration) {
            elapsed -= frameDuration
            val next = currentFrame + 1
            if (next < frameCount) {
                currentFrame = next
            } else if (loop) {
                currentFrame = next % frameCount
            } else {
                currentFrame = frameCount - 1
                playing = false
                elapsed = 0f
                break
            }
        }
    }

    override fun localBounds(): Rect {
        ensureTexture()
        val tex = texture ?: return Rect(Vec2.ZERO, Vec2.ZERO)
        val fw = frameWidth(tex)
        val fh = tex.height.toFloat()
        return Rect(Vec2(-fw / 2f, -fh / 2f), Vec2(fw, fh))
    }

    override fun onDraw(renderer: Renderer) {
        ensureTexture()
        val tex = texture
        if (tex != null) {
            val fw = frameWidth(tex)
            val fh = tex.height.toFloat()
            val frame = currentFrame.coerceIn(0, frameCount.coerceAtLeast(1) - 1)
            renderer.drawImage(
                texture = tex,
                src = Rect(Vec2(frame * fw, 0f), Vec2(fw, fh)),
                dst = Rect(Vec2(-fw / 2f, -fh / 2f), Vec2(fw, fh)),
                flipH = flipH,
            )
        }
        super.onDraw(renderer)
    }

    // Pixel width of one frame; integer division keeps frame boundaries on
    // exact pixel columns (nearest-neighbor must not straddle a texel).
    private fun frameWidth(tex: Texture): Float =
        (tex.width / frameCount.coerceAtLeast(1)).toFloat()

    private fun ensureTexture() {
        if (resolvedPath == texturePath) return
        val tex = tree?.textures?.load(texturePath)
        texture = tex
        resolvedPath = texturePath
        if (tex != null) {
            if (frameCount < 1) {
                Log.w(TAG, "frameCount=$frameCount for '$texturePath'; clamping to 1")
            } else if (tex.width % frameCount != 0) {
                Log.w(
                    TAG,
                    "sheet '$texturePath' width ${tex.width} is not divisible by frameCount $frameCount; " +
                        "frames will be truncated",
                )
            }
        }
    }

    private companion object {
        const val TAG = "AnimatedSprite2D"
    }
}
