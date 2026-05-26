package com.neoutils.engine.games.demos

import com.neoutils.engine.scene.Node2D
import kotlinx.serialization.Serializable

@Serializable
class Rotator : Node2D() {

    var angularVelocity: Float = 1f

    override fun onProcess(dt: Float) {
        transform = transform.copy(
            rotation = transform.rotation + angularVelocity * dt
        )
    }
}
