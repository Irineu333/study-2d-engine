package com.neoutils.engine.debug

/**
 * Per-`SceneTree` immediate-mode drawing facade, reached via
 * `tree.debug.draw`. Exposes two symmetric [DebugCanvas] surfaces — [world]
 * (drawn under the active `Camera2D` view transform) and [screen] (screen
 * pixels) — each mirroring `Renderer`'s primitives.
 *
 * Commands are single-frame: a verb called during `process` /
 * `physicsProcess` is flushed on that tick's render and discarded by
 * [clearFrame] at the render tail — no accumulation across frames, no manual
 * cleanup.
 *
 * [enabled] defaults to `false`, gating both canvases' verbs into no-ops so
 * production frames stay clean. The single `"Debug Draw"` HUD row
 * ([DebugDrawToggle]) flips it; game code wanting always-on gizmos sets
 * `tree.debug.draw.enabled = true` at setup.
 *
 * Not a `Node`, never `@Serializable`, never shared across trees.
 */
class DebugDraw {

    var enabled: Boolean = false

    val world: DebugCanvas = DebugCanvas { enabled }
    val screen: DebugCanvas = DebugCanvas { enabled }

    /** Empties both canvas buffers. Called at the tail of `SceneTree.render`. */
    fun clearFrame() {
        world.clear()
        screen.clear()
    }
}
