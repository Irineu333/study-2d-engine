package com.neoutils.engine.debug

import com.neoutils.engine.scene.Node2D

/**
 * Direct `Node2D` child of `DebugLayer` that hosts every `WorldDebugWidget`.
 * Identity transform — it exists only to live in the world pass of
 * `SceneTree.render`, so its descendants pick up the current `Camera2D`
 * view transform without any per-widget bookkeeping.
 */
class WorldDebugContainer : Node2D() {

    init { name = "WorldDebugContainer" }
}
