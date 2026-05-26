package com.neoutils.engine.render

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2

/**
 * `Renderer` implementation that captures every call as a [RecordedEvent].
 * Used by unit tests in `:engine` to assert on the exact `push/pop/draw*`
 * sequence emitted by `SceneTree.render` and `Node2D.onDraw` overrides.
 *
 * `measureText` returns a fixed-shape result (`Vec2(text.length * size *
 * 0.5f, size)`) so tests that rely on text bounds can produce deterministic
 * numbers without a real backend.
 */
internal class RecordingRenderer : Renderer {

    val events: MutableList<RecordedEvent> = mutableListOf()

    override fun clear(color: Color) {
        events += RecordedEvent.Clear(color)
    }

    override fun drawRect(rect: Rect, color: Color, filled: Boolean) {
        events += RecordedEvent.Rect(rect, color, filled)
    }

    override fun drawCircle(center: Vec2, radius: Float, color: Color, filled: Boolean, thickness: Float) {
        events += RecordedEvent.Circle(center, radius, color, filled, thickness)
    }

    override fun drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color) {
        events += RecordedEvent.Line(from, to, thickness, color)
    }

    override fun drawText(text: String, position: Vec2, size: Float, color: Color) {
        events += RecordedEvent.Text(text, position, size, color)
    }

    override fun measureText(text: String, size: Float): Vec2 = Vec2(text.length * size * 0.5f, size)

    override fun drawPolygon(points: List<Vec2>, color: Color) {
        events += RecordedEvent.Polygon(points, color)
    }

    override fun pushTransform(translation: Vec2, rotation: Float, scale: Vec2) {
        events += RecordedEvent.Push(translation, rotation, scale)
    }

    override fun popTransform() {
        events += RecordedEvent.Pop
    }
}

internal sealed class RecordedEvent {
    data class Clear(val color: Color) : RecordedEvent()
    data class Rect(val rect: com.neoutils.engine.math.Rect, val color: Color, val filled: Boolean) : RecordedEvent()
    data class Circle(val center: Vec2, val radius: Float, val color: Color, val filled: Boolean, val thickness: Float) : RecordedEvent()
    data class Line(val from: Vec2, val to: Vec2, val thickness: Float, val color: Color) : RecordedEvent()
    data class Text(val text: String, val position: Vec2, val size: Float, val color: Color) : RecordedEvent()
    data class Polygon(val points: List<Vec2>, val color: Color) : RecordedEvent()
    data class Push(val translation: Vec2, val rotation: Float, val scale: Vec2) : RecordedEvent()
    data object Pop : RecordedEvent()
}
