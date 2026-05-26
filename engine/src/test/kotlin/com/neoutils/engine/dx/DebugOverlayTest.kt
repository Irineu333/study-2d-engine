package com.neoutils.engine.dx

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.Area2D
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.physics.StaticBody2D
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.AspectMode
import com.neoutils.engine.scene.Camera2D
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DebugOverlayTest {

    private val savedShowFps = Debug.showFps
    private val savedColliders = Debug.colliderVisualization
    private val savedFps = Debug.currentFps

    @BeforeTest
    fun setUp() {
        Debug.showFps = false
        Debug.colliderVisualization = false
        Debug.currentFps = 60f
    }

    @AfterTest
    fun tearDown() {
        Debug.showFps = savedShowFps
        Debug.colliderVisualization = savedColliders
        Debug.currentFps = savedFps
    }

    @Test
    fun `bothFlagsOff issues no calls`() {
        val recorder = RecordingRenderer()
        val tree = treeWithBody()

        renderDebugOverlay(recorder, tree)

        assertEquals(0, recorder.calls.size)
    }

    @Test
    fun `only fps on draws single text`() {
        Debug.showFps = true
        val recorder = RecordingRenderer()
        val tree = treeWithBody()

        renderDebugOverlay(recorder, tree)

        assertEquals(1, recorder.calls.size)
        val text = recorder.calls.single() as Call.Text
        assertEquals("fps 60", text.text)
    }

    @Test
    fun `only colliders on draws one outlined rect per active shape`() {
        Debug.colliderVisualization = true
        val recorder = RecordingRenderer()
        val tree = treeWithBody(count = 3)

        renderDebugOverlay(recorder, tree)

        val rects = recorder.calls.filterIsInstance<Call.Rect>()
        assertEquals(3, rects.size)
        rects.forEach { call -> assertEquals(false, call.filled) }
    }

    @Test
    fun `both flags on draws both`() {
        Debug.showFps = true
        Debug.colliderVisualization = true
        val recorder = RecordingRenderer()
        val tree = treeWithBody(count = 2)

        renderDebugOverlay(recorder, tree)

        val rects = recorder.calls.filterIsInstance<Call.Rect>()
        val texts = recorder.calls.filterIsInstance<Call.Text>()
        assertEquals(2, rects.size)
        assertEquals(1, texts.size)
    }

    @Test
    fun `Areas and Bodies are colored distinctly`() {
        Debug.colliderVisualization = true
        val root = Node()
        root.addChild(makeArea(Vec2(10f, 10f)))
        root.addChild(makeBody(Vec2(10f, 10f)))
        val tree = SceneTree(root)
        tree.start()

        val recorder = RecordingRenderer()
        renderDebugOverlay(recorder, tree)

        val rects = recorder.calls.filterIsInstance<Call.Rect>()
        assertEquals(2, rects.size)
        val colors = rects.map { it.color }.toSet()
        // Area must use the green-ish debug color, Body the red-ish one.
        assertEquals(setOf(DEBUG_AREA_COLOR, DEBUG_BODY_COLOR), colors)
    }

    @Test
    fun `colliders with current camera push view transform around rects`() {
        Debug.colliderVisualization = true
        val root = Node()
        val camera = Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))
            current = true
            aspectMode = AspectMode.FIT
        }
        root.addChild(camera)
        root.addChild(makeBody(Vec2(10f, 10f)))
        root.addChild(makeBody(Vec2(20f, 20f)))
        val tree = SceneTree(root)
        tree.resize(1280f, 900f)
        tree.start()

        val recorder = RecordingRenderer()
        renderDebugOverlay(recorder, tree)

        val tags = recorder.calls.map {
            when (it) {
                is Call.Push -> "push"
                is Call.Pop -> "pop"
                is Call.Rect -> "rect"
                else -> "other"
            }
        }
        assertEquals(listOf("push", "rect", "rect", "pop"), tags)

        val push = recorder.calls.filterIsInstance<Call.Push>().single()
        assertEquals(Vec2(40f, 0f), push.translation)
        assertEquals(Vec2(1.5f, 1.5f), push.scale)
    }

    @Test
    fun `colliders without current camera draw without push or pop`() {
        Debug.colliderVisualization = true
        val recorder = RecordingRenderer()
        val tree = treeWithBody(count = 2)

        renderDebugOverlay(recorder, tree)

        assertEquals(0, recorder.calls.count { it is Call.Push })
        assertEquals(0, recorder.calls.count { it is Call.Pop })
        assertEquals(2, recorder.calls.count { it is Call.Rect })
    }

    @Test
    fun `FPS draws outside any push when camera is current`() {
        Debug.showFps = true
        Debug.colliderVisualization = true
        val root = Node()
        val camera = Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))
            current = true
        }
        root.addChild(camera)
        root.addChild(makeBody(Vec2(10f, 10f)))
        val tree = SceneTree(root)
        tree.resize(1280f, 900f)
        tree.start()

        val recorder = RecordingRenderer()
        renderDebugOverlay(recorder, tree)

        val tags = recorder.calls.map {
            when (it) {
                is Call.Push -> "push"
                is Call.Pop -> "pop"
                is Call.Rect -> "rect"
                is Call.Text -> "text"
                else -> "other"
            }
        }
        assertEquals(listOf("push", "rect", "pop", "text"), tags)
    }

    private fun makeBody(size: Vec2): StaticBody2D {
        val body = StaticBody2D()
        body.addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { this.size = size } })
        return body
    }

    private fun makeArea(size: Vec2): Area2D {
        val area = Area2D()
        area.addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { this.size = size } })
        return area
    }

    private fun treeWithBody(count: Int = 1): SceneTree {
        val root = Node()
        repeat(count) { root.addChild(makeBody(Vec2(10f, 10f))) }
        val tree = SceneTree(root)
        tree.start()
        return tree
    }
}

private sealed class Call {
    data class Rect(val rect: com.neoutils.engine.math.Rect, val color: Color, val filled: Boolean) : Call()
    data class Text(val text: String, val position: Vec2, val size: Float, val color: Color) : Call()
    data class Line(val from: Vec2, val to: Vec2, val thickness: Float, val color: Color) : Call()
    data class Circle(val center: Vec2, val radius: Float, val color: Color, val filled: Boolean, val thickness: Float) : Call()
    data class Clear(val color: Color) : Call()
    data class Push(val translation: Vec2, val rotation: Float, val scale: Vec2) : Call()
    object Pop : Call()
}

private class RecordingRenderer : Renderer {

    val calls: MutableList<Call> = mutableListOf()

    override fun clear(color: Color) { calls += Call.Clear(color) }
    override fun drawRect(rect: Rect, color: Color, filled: Boolean) { calls += Call.Rect(rect, color, filled) }
    override fun drawCircle(center: Vec2, radius: Float, color: Color, filled: Boolean, thickness: Float) {
        calls += Call.Circle(center, radius, color, filled, thickness)
    }
    override fun drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color) {
        calls += Call.Line(from, to, thickness, color)
    }
    override fun drawText(text: String, position: Vec2, size: Float, color: Color) {
        calls += Call.Text(text, position, size, color)
    }
    override fun measureText(text: String, size: Float): Vec2 = Vec2(text.length * size * 0.5f, size)
    override fun drawPolygon(points: List<Vec2>, color: Color) {}
    override fun pushTransform(translation: Vec2, rotation: Float, scale: Vec2) {
        calls += Call.Push(translation, rotation, scale)
    }
    override fun popTransform() { calls += Call.Pop }
}
