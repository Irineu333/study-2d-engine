package com.neoutils.engine.scene

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneRenderCameraTest {

    @Test
    fun `with current camera, push wraps draws and pop ends the sequence`() {
        val root = Node()
        val camera = Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))
            current = true
            aspectMode = AspectMode.FIT
        }
        root.addChild(camera)
        root.addChild(DrawSpy())
        val tree = SceneTree(root)
        tree.resize(1280f, 900f)
        tree.start()

        val recorder = RecordingRenderer()
        tree.render(recorder)

        val tags = recorder.events.map { it.tag() }
        assertTrue(tags.first() == "push", "expected push first; got $tags")
        assertTrue(tags.last() == "pop", "expected pop last; got $tags")
        // At least one draw between push and pop.
        val draws = tags.filter { it == "rect" }
        assertEquals(1, draws.size)

        // World coords reach the renderer unchanged (the renderer doesn't apply
        // the transform — that's the backend's job in production).
        val rect = recorder.events.filterIsInstance<Call.Rect>().single()
        assertEquals(Rect(Vec2.ZERO, Vec2(800f, 600f)), rect.rect)

        // The outermost push carries the FIT view transform: scale 1.5, offset
        // (40, 0), no rotation. Camera2D itself is a Node2D so it ALSO produces
        // a per-Node2D push (identity), which is recorded second.
        val pushes = recorder.events.filterIsInstance<Call.Push>()
        val viewPush = pushes.first()
        assertEquals(Vec2(40f, 0f), viewPush.translation)
        assertEquals(0f, viewPush.rotation)
        assertEquals(Vec2(1.5f, 1.5f), viewPush.scale)
    }

    @Test
    fun `without current camera, no push or pop is emitted`() {
        val root = Node()
        root.addChild(DrawSpy())
        val tree = SceneTree(root)
        tree.resize(1280f, 900f)
        tree.start()

        val recorder = RecordingRenderer()
        tree.render(recorder)

        assertEquals(0, recorder.events.count { it is Call.Push })
        assertEquals(0, recorder.events.count { it is Call.Pop })
        assertEquals(1, recorder.events.count { it is Call.Rect })
    }

    @Test
    fun `current camera with degenerate bounds skips view push`() {
        val root = Node()
        val camera = Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2.ZERO)
            current = true
        }
        root.addChild(camera)
        root.addChild(DrawSpy())
        val tree = SceneTree(root)
        tree.resize(800f, 600f)
        tree.start()

        val recorder = RecordingRenderer()
        tree.render(recorder)

        // Camera is a Node2D so it still produces its own per-Node2D push;
        // what MUST be skipped is the outermost view push. With degenerate
        // bounds, total pushes == 1 (the Camera2D Node2D itself) rather than 2.
        assertEquals(1, recorder.events.count { it is Call.Push })
        assertEquals(1, recorder.events.count { it is Call.Pop })
    }
}

private class DrawSpy : Node() {
    override fun onDraw(renderer: Renderer) {
        renderer.drawRect(Rect(Vec2.ZERO, Vec2(800f, 600f)), Color.WHITE, filled = true)
    }
}

private sealed class Call {
    data class Push(val translation: Vec2, val rotation: Float, val scale: Vec2) : Call()
    object Pop : Call()
    data class Rect(val rect: com.neoutils.engine.math.Rect, val color: Color, val filled: Boolean) : Call()
    data class Text(val text: String) : Call()

    fun tag(): String = when (this) {
        is Push -> "push"
        is Pop -> "pop"
        is Rect -> "rect"
        is Text -> "text"
    }
}

private class RecordingRenderer : Renderer {
    val events: MutableList<Call> = mutableListOf()
    override fun clear(color: Color) {}
    override fun drawRect(rect: com.neoutils.engine.math.Rect, color: Color, filled: Boolean) {
        events += Call.Rect(rect, color, filled)
    }
    override fun drawCircle(center: Vec2, radius: Float, color: Color, filled: Boolean, thickness: Float) {}
    override fun drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color) {}
    override fun drawText(text: String, position: Vec2, size: Float, color: Color) { events += Call.Text(text) }
    override fun measureText(text: String, size: Float): Vec2 = Vec2.ZERO
    override fun drawPolygon(points: List<Vec2>, color: Color) {}
    override fun pushTransform(translation: Vec2, rotation: Float, scale: Vec2) {
        events += Call.Push(translation, rotation, scale)
    }
    override fun popTransform() { events += Call.Pop }
}
