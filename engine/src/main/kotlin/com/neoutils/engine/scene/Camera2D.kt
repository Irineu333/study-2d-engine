package com.neoutils.engine.scene

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

/**
 * 2D camera node. The first `Camera2D` whose `current` is `true` discovered
 * via a pre-order walk from the scene root becomes the scene's active camera
 * and exposes `bounds` as `Scene.viewport`. With no current camera the scene
 * falls back to `Rect(Vec2.ZERO, scene.size)`.
 */
@Serializable
class Camera2D : Node2D() {

    @Inspect
    var bounds: Rect = Rect(Vec2.ZERO, Vec2.ZERO)

    @Inspect
    var current: Boolean = false
}
