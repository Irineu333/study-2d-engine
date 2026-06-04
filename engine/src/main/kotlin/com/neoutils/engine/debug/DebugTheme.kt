package com.neoutils.engine.debug

import com.neoutils.engine.render.Color
import com.neoutils.engine.scene.Border

/**
 * Single source of appearance for the whole debug subsystem: panel chrome
 * (background, border, the margin/gutter/padding spacing), a named text scale
 * (title/body/small), and the gizmo/log accent colors (re-exported by
 * [DebugColors] for back-compat).
 *
 * Plain `object`, never `@Serializable` — this is appearance state, not scene
 * state. Every debug panel derives its chrome from here so the overlays stay
 * visually consistent and a single edit re-themes them all.
 */
object DebugTheme {

    /** Fill behind every debug panel. */
    val panelBackground: Color = Color(0.10f, 0.10f, 0.12f, 0.85f)

    /** Outline color of every debug panel. */
    val panelBorderColor: Color = Color(0.55f, 0.55f, 0.60f, 1f)

    /** Outline thickness carried by the panel [border]. */
    val panelBorderWidth: Float = 1f

    /**
     * Fill behind a panel's title bar — the drag handle. A touch lighter than
     * [panelBackground] so the grabbable header reads as distinct from the body.
     */
    val headerBackground: Color = Color(0.18f, 0.19f, 0.24f, 0.95f)

    /** Height of the panel title bar / drag handle, in screen pixels. */
    val headerHeight: Float = 20f

    /** Color of the drag-grip dots drawn in the header to signal "draggable". */
    val headerGripColor: Color = Color(0.65f, 0.65f, 0.72f, 1f)

    /** Color of the header window-control glyphs (collapse `[_]` / close `[x]`). */
    val headerControlColor: Color = Color(0.80f, 0.80f, 0.86f, 1f)

    /** Fill of the scrollbar track behind the grabber. */
    val scrollTrackColor: Color = Color(0.20f, 0.20f, 0.24f, 0.6f)

    /** Fill of the scrollbar grabber (the draggable thumb). */
    val scrollGrabberColor: Color = Color(0.55f, 0.55f, 0.62f, 0.95f)

    /** Outer inset between a docked panel and the screen edge. */
    val margin: Float = 12f

    /** Vertical gap between two panels stacked in the same dock slot. */
    val gutter: Float = 8f

    /** Inner padding between a panel's border and its content. */
    val padding: Float = 8f

    /**
     * Thickness (in screen pixels) of the top and bottom dock bands the dynamic
     * dock maps the pointer into: a release inside a band docks the panel, a
     * release in the central remainder (the miolo) floats it. Sized to be a
     * comfortable drop target near each edge without swallowing the center.
     */
    val dockBandThickness: Float = 96f

    /** Fill of the insertion indicator drawn in the target slot during a drag. */
    val insertionIndicatorColor: Color = Color(0.4f, 0.9f, 1f, 0.95f)

    /** Headline text size (panel titles). */
    val titleTextSize: Float = 14f

    /** Default body text size (rows, readouts). */
    val bodyTextSize: Float = 12f

    /** Secondary text size (breadcrumbs, hints). */
    val smallTextSize: Float = 10f

    /** Default text color for panel body content. */
    val textColor: Color = Color.WHITE

    /** Convenience [Border] carrying the theme's outline color + width. */
    val border: Border get() = Border(panelBorderColor, panelBorderWidth)

    /** Outline of `Area2D` shape bounds (triggers, e.g. goals). */
    val areaColor: Color = Color(0f, 1f, 0f, 0.8f)

    /** Outline of `PhysicsBody2D` shape bounds (solid bodies). */
    val bodyColor: Color = Color(1f, 0.3f, 0.3f, 0.8f)

    /** `VelocityGizmoWidget` arrows (cyan). */
    val velocityColor: Color = Color(0.3f, 0.8f, 1f, 0.9f)

    /** `ContactGizmoWidget` markers and normal lines (yellow). */
    val contactColor: Color = Color(1f, 0.9f, 0.2f, 0.95f)

    /** `SelectionGizmoWidget` oriented box (magenta). */
    val selectionColor: Color = Color(1f, 0.2f, 1f, 0.95f)

    /** Log overlay text color for `Debug`/`Info` entries (neutral light gray). */
    val logNeutralColor: Color = Color(0.85f, 0.85f, 0.85f, 1f)

    /** Log overlay text color for `Warn` entries (amber). */
    val logWarnColor: Color = Color(1f, 0.75f, 0.2f, 1f)

    /** Log overlay text color for `Error` entries (red). */
    val logErrorColor: Color = Color(1f, 0.35f, 0.35f, 1f)
}
