package com.neoutils.engine.scene

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.math.Rect
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.serialization.Signal
import kotlin.test.Test
import kotlin.test.assertEquals

private val testRenderer: Renderer = object : Renderer {
    override fun clear(color: Color) {}
    override fun drawRect(rect: Rect, color: Color, filled: Boolean) {}
    override fun drawCircle(center: Vec2, radius: Float, color: Color, filled: Boolean, thickness: Float) {}
    override fun drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color) {}
    override fun drawText(text: String, position: Vec2, size: Float, color: Color) {}
    override fun measureText(text: String, size: Float): Vec2 = Vec2.ZERO
    override fun drawPolygon(points: List<Vec2>, color: Color) {}
    override fun drawImage(texture: com.neoutils.engine.render.Texture, src: com.neoutils.engine.math.Rect, dst: com.neoutils.engine.math.Rect, flipH: Boolean) {}
    override fun pushTransform(translation: Vec2, rotation: Float, scale: Vec2) {}
    override fun popTransform() {}
    override fun pushClip(rect: Rect) {}
    override fun popClip() {}
}

class NodeScriptInstanceTest {

    private class RecordingContract : ScriptInstanceContract {
        val calls = mutableListOf<String>()
        override val signals: Map<String, Signal<*>> = emptyMap()
        override fun onEnter() { calls += "onEnter" }
        override fun onProcess(dt: Float) { calls += "onProcess($dt)" }
        override fun onPhysicsProcess(dt: Float) { calls += "onPhysicsProcess($dt)" }
        override fun onDraw(renderer: Renderer) { calls += "onDraw" }
        override fun onExit() { calls += "onExit" }
    }

    @Test
    fun `onProcess dispatches to scriptInstance`() {
        val node = Node2D()
        val contract = RecordingContract()
        node.scriptInstance = contract
        node.onProcess(0.016f)
        assertEquals(listOf("onProcess(0.016)"), contract.calls)
    }

    @Test
    fun `onPhysicsProcess dispatches to scriptInstance`() {
        val node = Node2D()
        val contract = RecordingContract()
        node.scriptInstance = contract
        node.onPhysicsProcess(0.016f)
        assertEquals(listOf("onPhysicsProcess(0.016)"), contract.calls)
    }

    @Test
    fun `onEnter dispatches to scriptInstance`() {
        val node = Node2D()
        val contract = RecordingContract()
        node.scriptInstance = contract
        node.onEnter()
        assertEquals(listOf("onEnter"), contract.calls)
    }

    @Test
    fun `onDraw dispatches to scriptInstance`() {
        val node = Node2D()
        val contract = RecordingContract()
        node.scriptInstance = contract
        node.onDraw(testRenderer)
        assertEquals(listOf("onDraw"), contract.calls)
    }

    @Test
    fun `node without scriptInstance behaves like before`() {
        val node = Node2D()
        node.onProcess(0.016f)
        node.onPhysicsProcess(0.016f)
        node.onEnter()
    }
}
