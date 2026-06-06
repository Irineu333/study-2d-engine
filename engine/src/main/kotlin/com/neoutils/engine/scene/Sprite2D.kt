package com.neoutils.engine.scene

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.render.Texture
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Draws a texture centered on the node's local origin (Godot `Sprite2D`
 * convention). The handle is resolved once on enter from `tree.textures`; the
 * whole image is drawn, scaled/rotated/translated by the node's world transform
 * via the stack `SceneTree.render` pushes around [onDraw].
 *
 * With no texture backend (`tree.textures == null`, e.g. headless tests) the
 * handle stays `null`: [onDraw] is a no-op and [localBounds] is empty — the
 * sprite is invisible but never throws.
 */
@Serializable
open class Sprite2D : Node2D() {

    /** Classpath path of the image asset (e.g. `"demos/sprites/idle.png"`). */
    @Inspect
    var texturePath: String = ""

    /** Mirrors the image horizontally (visual only — never a negative scale). */
    @Inspect
    var flipH: Boolean = false

    @Transient
    private var texture: Texture? = null

    override fun onEnter() {
        super.onEnter()
        // Resolve through the tree's backend; null backend ⇒ null handle ⇒
        // invisible-but-safe (no draw, empty bounds).
        texture = tree?.textures?.load(texturePath)
    }

    override fun onExit() {
        // Drop only the local reference — the backend owns the cache and frees
        // every texture on tree.stop(); disposing here would break other nodes
        // sharing the same cached handle.
        texture = null
        super.onExit()
    }

    override fun localBounds(): Rect {
        val tex = texture ?: return Rect(Vec2.ZERO, Vec2.ZERO)
        val w = tex.width.toFloat()
        val h = tex.height.toFloat()
        return Rect(Vec2(-w / 2f, -h / 2f), Vec2(w, h))
    }

    override fun onDraw(renderer: Renderer) {
        val tex = texture
        if (tex != null) {
            val w = tex.width.toFloat()
            val h = tex.height.toFloat()
            renderer.drawImage(
                texture = tex,
                src = Rect(Vec2.ZERO, Vec2(w, h)),
                dst = Rect(Vec2(-w / 2f, -h / 2f), Vec2(w, h)),
                flipH = flipH,
            )
        }
        super.onDraw(renderer)
    }
}
