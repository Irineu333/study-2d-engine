package com.neoutils.engine.debug

import com.neoutils.engine.render.Color

/** Color used to outline `Area2D` shape bounds (triggers, e.g. goals). */
val DEBUG_AREA_COLOR: Color = Color(0f, 1f, 0f, 0.8f)

/** Color used to outline `PhysicsBody2D` shape bounds (solid bodies). */
val DEBUG_BODY_COLOR: Color = Color(1f, 0.3f, 0.3f, 0.8f)

/** Color of the `VelocityGizmoWidget` arrows (cyan). */
val DEBUG_VELOCITY_COLOR: Color = Color(0.3f, 0.8f, 1f, 0.9f)

/** Color of the `ContactGizmoWidget` markers and normal lines (yellow). */
val DEBUG_CONTACT_COLOR: Color = Color(1f, 0.9f, 0.2f, 0.95f)

/** Log overlay text color for `Debug`/`Info` entries (neutral light gray). */
val DEBUG_LOG_NEUTRAL_COLOR: Color = Color(0.85f, 0.85f, 0.85f, 1f)

/** Log overlay text color for `Warn` entries (amber). */
val DEBUG_LOG_WARN_COLOR: Color = Color(1f, 0.75f, 0.2f, 1f)

/** Log overlay text color for `Error` entries (red). */
val DEBUG_LOG_ERROR_COLOR: Color = Color(1f, 0.35f, 0.35f, 1f)

/** Color of the `SelectionGizmoWidget` oriented box (magenta). */
val DEBUG_SELECTION_COLOR: Color = Color(1f, 0.2f, 1f, 0.95f)
