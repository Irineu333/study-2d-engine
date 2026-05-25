package com.neoutils.engine.scene

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

/**
 * How `Camera2D.bounds` is mapped onto a surface whose aspect ratio differs
 * from the bounds.
 *
 * - [FIT] uniform scale = `min(sx, sy)` — world fully visible, letterbox bars.
 * - [FILL] uniform scale = `max(sx, sy)` — surface fully covered, world cropped.
 * - [STRETCH] independent per-axis scale — world distorted to match the surface.
 */
@Serializable
enum class AspectMode { FIT, FILL, STRETCH }

/**
 * 2D camera node. The first `Camera2D` whose `current` is `true` discovered
 * via a pre-order walk from the tree root becomes the tree's active camera
 * and exposes `bounds` as `SceneTree.viewport`. With no current camera the
 * tree falls back to `Rect(Vec2.ZERO, tree.size)`.
 *
 * When `current` is `true`, `SceneTree.render` projects `bounds` onto the
 * surface via `Renderer.pushTransform` using the [aspectMode] policy. Helpers
 * [screenToWorld] / [worldToScreen] expose the same projection for input
 * conversion.
 */
@Serializable
class Camera2D : Node2D() {

    @Inspect
    var bounds: Rect = Rect(Vec2.ZERO, Vec2.ZERO)

    @Inspect
    var current: Boolean = false

    @Inspect
    var aspectMode: AspectMode = AspectMode.FIT

    /**
     * Computes the `(translation, scale)` pair the renderer needs to map
     * [bounds] onto a surface of [sceneSize] under [aspectMode]. Returns
     * `null` when [bounds] is degenerate (any size component `<= 0`) so the
     * caller can short-circuit to identity (no push).
     */
    fun computeViewTransform(sceneSize: Vec2): Pair<Vec2, Vec2>? {
        val bw = bounds.size.x
        val bh = bounds.size.y
        if (bw <= 0f || bh <= 0f) return null

        val scale = when (aspectMode) {
            AspectMode.FIT -> {
                val s = min(sceneSize.x / bw, sceneSize.y / bh)
                Vec2(s, s)
            }
            AspectMode.FILL -> {
                val s = max(sceneSize.x / bw, sceneSize.y / bh)
                Vec2(s, s)
            }
            AspectMode.STRETCH -> Vec2(sceneSize.x / bw, sceneSize.y / bh)
        }

        // Center the projected world on the surface; shift bounds.origin so a
        // world point at bounds.origin maps to (offset.x, offset.y) on screen.
        val projectedW = bw * scale.x
        val projectedH = bh * scale.y
        val offsetX = (sceneSize.x - projectedW) * 0.5f
        val offsetY = (sceneSize.y - projectedH) * 0.5f
        val translation = Vec2(
            offsetX - bounds.origin.x * scale.x,
            offsetY - bounds.origin.y * scale.y,
        )
        return translation to scale
    }

    fun screenToWorld(screenPosition: Vec2, sceneSize: Vec2): Vec2 {
        val view = computeViewTransform(sceneSize) ?: return screenPosition
        val (t, s) = view
        return Vec2((screenPosition.x - t.x) / s.x, (screenPosition.y - t.y) / s.y)
    }

    fun worldToScreen(worldPosition: Vec2, sceneSize: Vec2): Vec2 {
        val view = computeViewTransform(sceneSize) ?: return worldPosition
        val (t, s) = view
        return Vec2(t.x + worldPosition.x * s.x, t.y + worldPosition.y * s.y)
    }
}
