package com.neoutils.engine.dx

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.BoxCollider
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Scene
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
        val scene = sceneWithCollider()

        renderDebugOverlay(recorder, scene)

        assertEquals(0, recorder.calls.size)
    }

    @Test
    fun `only fps on draws single text`() {
        Debug.showFps = true
        val recorder = RecordingRenderer()
        val scene = sceneWithCollider()

        renderDebugOverlay(recorder, scene)

        assertEquals(1, recorder.calls.size)
        val text = recorder.calls.single() as Call.Text
        assertEquals("fps 60", text.text)
    }

    @Test
    fun `only colliders on draws one outlined rect per collider`() {
        Debug.colliderVisualization = true
        val recorder = RecordingRenderer()
        val scene = sceneWithCollider(count = 3)

        renderDebugOverlay(recorder, scene)

        val rects = recorder.calls.filterIsInstance<Call.Rect>()
        assertEquals(3, rects.size)
        rects.forEach { call ->
            assertEquals(false, call.filled)
        }
    }

    @Test
    fun `both flags on draws both`() {
        Debug.showFps = true
        Debug.colliderVisualization = true
        val recorder = RecordingRenderer()
        val scene = sceneWithCollider(count = 2)

        renderDebugOverlay(recorder, scene)

        val rects = recorder.calls.filterIsInstance<Call.Rect>()
        val texts = recorder.calls.filterIsInstance<Call.Text>()
        assertEquals(2, rects.size)
        assertEquals(1, texts.size)
    }

    private fun sceneWithCollider(count: Int = 1): Scene {
        val scene = Scene()
        repeat(count) { scene.addChild(BoxCollider().apply { size = Vec2(10f, 10f) }) }
        scene.start()
        return scene
    }
}

private sealed class Call {
    data class Rect(val rect: com.neoutils.engine.math.Rect, val color: Color, val filled: Boolean) : Call()
    data class Text(val text: String, val position: Vec2, val size: Float, val color: Color) : Call()
    data class Line(val from: Vec2, val to: Vec2, val thickness: Float, val color: Color) : Call()
    data class Circle(val center: Vec2, val radius: Float, val color: Color, val filled: Boolean, val thickness: Float) : Call()
    data class Clear(val color: Color) : Call()
}

private class RecordingRenderer : Renderer {

    val calls: MutableList<Call> = mutableListOf()

    override fun clear(color: Color) {
        calls += Call.Clear(color)
    }

    override fun drawRect(rect: Rect, color: Color, filled: Boolean) {
        calls += Call.Rect(rect, color, filled)
    }

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
}
