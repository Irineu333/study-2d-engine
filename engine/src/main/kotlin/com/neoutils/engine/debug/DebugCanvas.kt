package com.neoutils.engine.debug

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer

/**
 * One immediate-mode drawing surface — either the `world` space (drawn under
 * the active `Camera2D` view transform) or the `screen` space (screen
 * pixels). Verbs mirror [Renderer] and enqueue a [DrawCommand] into a
 * per-frame buffer; the matching backing node flushes the buffer during
 * `SceneTree.render` and the tree clears it at the render tail.
 *
 * Each verb is a no-op (early-return, nothing enqueued) while [isEnabled]
 * reports `false`, so calling draw verbs every frame from disabled debug
 * carries negligible cost. The enabled state lives on the owning [DebugDraw];
 * the canvas reads it through [isEnabled] so the single HUD toggle controls
 * both canvases.
 *
 * Single-threaded by contract — verbs run on the game-loop thread
 * (process/physics/render), so the buffer needs no synchronization.
 */
class DebugCanvas internal constructor(private val isEnabled: () -> Boolean) {

    private val _commands: MutableList<DrawCommand> = mutableListOf()

    /** Read-only view of the commands enqueued so far this frame. */
    val commands: List<DrawCommand> get() = _commands

    // @JvmOverloads generates the arity-reduced overloads that script hosts
    // (GraalPy / LuaJ) need: default arguments are not synthesized when a verb
    // is invoked reflectively from a script, so `world.line(a, b, color)` would
    // otherwise raise an arity error.
    @JvmOverloads
    fun line(from: Vec2, to: Vec2, color: Color, thickness: Float = 1f) {
        if (!isEnabled()) return
        _commands += DrawCommand.Line(from, to, color, thickness)
    }

    @JvmOverloads
    fun rect(rect: Rect, color: Color, filled: Boolean = false) {
        if (!isEnabled()) return
        _commands += DrawCommand.Rect(rect, color, filled)
    }

    @JvmOverloads
    fun circle(center: Vec2, radius: Float, color: Color, filled: Boolean = false, thickness: Float = 1f) {
        if (!isEnabled()) return
        _commands += DrawCommand.Circle(center, radius, color, filled, thickness)
    }

    fun polygon(points: List<Vec2>, color: Color) {
        if (!isEnabled()) return
        _commands += DrawCommand.Polygon(points, color)
    }

    @JvmOverloads
    fun text(position: Vec2, text: String, color: Color, size: Float = 14f) {
        if (!isEnabled()) return
        _commands += DrawCommand.Text(position, text, color, size)
    }

    /** Emits every buffered command onto [renderer] in enqueue order. */
    fun flush(renderer: Renderer) {
        for (command in _commands) {
            when (command) {
                is DrawCommand.Line ->
                    renderer.drawLine(command.from, command.to, command.thickness, command.color)
                is DrawCommand.Rect ->
                    renderer.drawRect(command.rect, command.color, command.filled)
                is DrawCommand.Circle ->
                    renderer.drawCircle(command.center, command.radius, command.color, command.filled, command.thickness)
                is DrawCommand.Polygon ->
                    renderer.drawPolygon(command.points, command.color)
                is DrawCommand.Text ->
                    renderer.drawText(command.text, command.position, command.size, command.color)
            }
        }
    }

    fun clear() {
        _commands.clear()
    }
}
