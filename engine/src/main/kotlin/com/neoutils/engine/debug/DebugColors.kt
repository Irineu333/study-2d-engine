package com.neoutils.engine.debug

import com.neoutils.engine.render.Color

/** Color used to outline `Area2D` shape bounds (triggers, e.g. goals). */
val DEBUG_AREA_COLOR: Color = Color(0f, 1f, 0f, 0.8f)

/** Color used to outline `PhysicsBody2D` shape bounds (solid bodies). */
val DEBUG_BODY_COLOR: Color = Color(1f, 0.3f, 0.3f, 0.8f)
