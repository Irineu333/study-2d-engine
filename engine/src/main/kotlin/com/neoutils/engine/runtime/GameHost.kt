package com.neoutils.engine.runtime

import com.neoutils.engine.tree.SceneTree

/**
 * Host of execution of a game: owns a window/surface, drives the per-frame
 * pulse, wires `Input` events from the platform, and runs the game loop until
 * the host is closed. Implementations live in backend modules (e.g.
 * `:engine-compose`, `:engine-skiko`).
 */
interface GameHost {

    /** Runs the game blocking until the host window closes. */
    fun run(tree: SceneTree, config: GameConfig = GameConfig())
}
