package com.neoutils.engine.debug

import com.neoutils.engine.render.Renderer

/**
 * Marker contract for a single, self-contained debug visualization.
 *
 * `DebugWidget` is an interface (not a class) so the two engine-shipped bases
 * — `ScreenDebugWidget : Node()` and `WorldDebugWidget : Node2D()` — can each
 * extend the right scene-graph base while sharing the debug contract.
 *
 * Subclasses choose their space by extending the matching base:
 *  - `ScreenDebugWidget` for HUD-like overlays in screen pixels.
 *  - `WorldDebugWidget` for gizmos that follow the active `Camera2D`.
 */
interface DebugWidget {

    /** Short label displayed in the HUD row that toggles this widget. */
    val title: String

    /**
     * When `false`, `drawDebug` is never invoked and the widget emits no draw
     * calls. Default is `false` so production frames stay clean; the user
     * opens the HUD and ticks the row to enable.
     */
    var enabled: Boolean

    /** Only called by the engine when [enabled] is `true`. */
    fun drawDebug(renderer: Renderer)
}
