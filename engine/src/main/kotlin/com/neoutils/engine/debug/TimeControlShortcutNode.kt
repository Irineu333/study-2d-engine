package com.neoutils.engine.debug

import com.neoutils.engine.input.Key
import com.neoutils.engine.scene.Node

/**
 * Engine-internal `Node` that polls the time-control keyboard shortcuts each
 * tick and mutates the owning `SceneTree`'s time state on the key edge:
 * [pauseKey] toggles `paused`, [stepKey] calls `requestStep()`, and
 * [speedDownKey] / [speedUpKey] step `timeScale` one [TimeControlWidget.SPEED_PRESETS]
 * entry down / up — mirroring the widget's − / + steppers (clamped, no wrap).
 *
 * Lives under `ScreenDebugCanvas` so it runs in `process` — and since
 * `GameLoop` keeps `process` running under pause (`dt = 0`), the shortcuts
 * stay responsive while the world is frozen. Not a `DebugWidget`: no HUD row,
 * no `drawDebug`, mirroring `DebugToggleNode`. Keys are public `var`s so a
 * game can rebind them to avoid gameplay conflicts.
 */
internal class TimeControlShortcutNode : Node() {

    var pauseKey: Key = Key.P
    var stepKey: Key = Key.O
    var speedDownKey: Key = Key.U
    var speedUpKey: Key = Key.I

    init { name = "TimeControlShortcutNode" }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        val owningTree = tree ?: return
        val input = owningTree.input ?: return
        if (input.wasKeyPressed(pauseKey)) {
            owningTree.paused = !owningTree.paused
        }
        if (input.wasKeyPressed(stepKey)) {
            owningTree.requestStep()
        }
        if (input.wasKeyPressed(speedDownKey)) {
            owningTree.timeScale = TimeControlWidget.stepSpeed(owningTree.timeScale, up = false)
        }
        if (input.wasKeyPressed(speedUpKey)) {
            owningTree.timeScale = TimeControlWidget.stepSpeed(owningTree.timeScale, up = true)
        }
    }
}
