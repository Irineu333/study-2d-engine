package com.neoutils.engine.loop

import com.neoutils.engine.input.Input
import com.neoutils.engine.physics.PhysicsSystem
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Scene

class GameLoop(
    val scene: Scene,
    val renderer: Renderer,
    val input: Input,
    val physics: PhysicsSystem = PhysicsSystem(),
) {

    /**
     * Maximum dt (seconds) emitted to nodes. Clamps the first frame and any
     * stalls (debugger pause, GC) so a single tick never advances simulation
     * beyond ~50 ms.
     */
    var maxDt: Float = 0.05f

    fun tick(dtNanos: Long) {
        if (!scene.isLive) scene.start()
        scene.input = input
        val dt = (dtNanos / 1_000_000_000f).coerceAtMost(maxDt).coerceAtLeast(0f)
        scene.applyPending()
        scene.update(dt)
        scene.applyPending()
        physics.step(scene)
        scene.applyPending()
        scene.render(renderer)
    }
}
