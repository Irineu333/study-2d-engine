package com.neoutils.engine.debug

import com.neoutils.engine.input.Key
import com.neoutils.engine.scene.Node

/**
 * Engine-internal `Node` that polls the collider-mode shortcut each tick and,
 * on the [cycleKey] edge, cycles `tree.debug.colliders.mode` through
 * `AABB → REAL → BOTH → AABB` — the runtime control for the merged collider
 * gizmo (real geometry / broad-phase envelope / both).
 *
 * Lives under `ScreenDebugCanvas`, mirroring [DebugToggleNode] /
 * [TimeControlShortcutNode] / [DebugLayoutShortcutNode]: not a `DebugWidget`
 * (no HUD row, no `drawDebug`). Gated on `tree.debug.colliders.enabled` so the
 * default binding never collides with gameplay input while the collider gizmo
 * is off. [cycleKey] is a public `var` so a game can rebind it.
 */
internal class ColliderModeShortcutNode : Node() {

    var cycleKey: Key = Key.C

    init { name = "ColliderModeShortcutNode" }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        val owningTree = tree ?: return
        val colliders = owningTree.debug.colliders
        if (!colliders.enabled) return
        val input = owningTree.input ?: return
        if (input.wasKeyPressed(cycleKey)) {
            val modes = ColliderDrawMode.entries
            colliders.mode = modes[(colliders.mode.ordinal + 1) % modes.size]
        }
    }
}
