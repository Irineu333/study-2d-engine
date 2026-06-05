package com.neoutils.engine.scene

import com.neoutils.engine.math.Vec2
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

/**
 * How the UI stretch maps `SceneTree.designSize` onto the surface.
 *
 * - [FIT] uniform scale = `min(sx, sy)` — design fully visible, letterbox bars.
 * - [FILL] uniform scale = `max(sx, sy)` — surface fully covered, design cropped.
 * - [STRETCH] independent per-axis scale — design distorted to match the surface.
 * - [DISABLED] no stretch — UI renders in raw screen pixels.
 */
@Serializable
enum class UiStretchMode { FIT, FILL, STRETCH, DISABLED }

/**
 * The resolution-fit [AspectMode] this stretch mode maps to, or `null` for
 * [UiStretchMode.DISABLED] (which short-circuits to no transform).
 */
fun UiStretchMode.toAspectMode(): AspectMode? = when (this) {
    UiStretchMode.FIT -> AspectMode.FIT
    UiStretchMode.FILL -> AspectMode.FILL
    UiStretchMode.STRETCH -> AspectMode.STRETCH
    UiStretchMode.DISABLED -> null
}

/**
 * Pure resolution-fit transform: the `(translation, scale)` pair that maps the
 * design rect `Rect(Vec2.ZERO, designSize)` onto the surface rect
 * `Rect(Vec2.ZERO, surfaceSize)` under [mode]. This is **only** the
 * scale plus the letterbox **centering** translation — it carries no
 * origin/pan term, so both `Camera2D` (which adds its pan on top) and the UI
 * stretch pass can share the math and have their letterbox bars coincide.
 *
 * Returns `null` for the degenerate case (any [designSize] component `<= 0`,
 * or a resulting scale component `<= 0` from a degenerate surface) and for the
 * identity case (translation `0` and scale `1`), so callers can short-circuit
 * to "no push".
 */
fun fitTransform(designSize: Vec2, surfaceSize: Vec2, mode: AspectMode): Pair<Vec2, Vec2>? {
    val dw = designSize.x
    val dh = designSize.y
    if (dw <= 0f || dh <= 0f) return null

    val scale = when (mode) {
        AspectMode.FIT -> {
            val s = min(surfaceSize.x / dw, surfaceSize.y / dh)
            Vec2(s, s)
        }
        AspectMode.FILL -> {
            val s = max(surfaceSize.x / dw, surfaceSize.y / dh)
            Vec2(s, s)
        }
        AspectMode.STRETCH -> Vec2(surfaceSize.x / dw, surfaceSize.y / dh)
    }
    if (scale.x <= 0f || scale.y <= 0f) return null

    val translation = Vec2(
        (surfaceSize.x - dw * scale.x) * 0.5f,
        (surfaceSize.y - dh * scale.y) * 0.5f,
    )
    if (translation == Vec2.ZERO && scale == Vec2.ONE) return null
    return translation to scale
}
