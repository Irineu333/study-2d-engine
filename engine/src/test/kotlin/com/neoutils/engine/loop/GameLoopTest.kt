package com.neoutils.engine.loop

import com.neoutils.engine.input.Input
import com.neoutils.engine.input.Key
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.math.Transform
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.PhysicsBody2D
import com.neoutils.engine.physics.PhysicsSystem
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.physics.StaticBody2D
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private object NoopInput : Input {
    override val pointerPosition: Vec2 = Vec2.ZERO
    override var mouseClickConsumed: Boolean = false
    override fun isKeyDown(key: Key): Boolean = false
    override fun wasKeyPressed(key: Key): Boolean = false
    override fun isMouseDown(button: MouseButton): Boolean = false
    override fun wasMouseClickedRaw(button: MouseButton): Boolean = false
}

private class CountingRenderer : Renderer {
    var clears = 0
    override fun clear(color: Color) { clears++ }
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

class GameLoopTest {

    @Test
    fun `tick converts nanoseconds to seconds`() {
        val root = Node()
        val received = mutableListOf<Float>()
        val node = object : Node() { override fun onProcess(dt: Float) { received += dt } }
        root.addChild(node)
        val tree = SceneTree(root)
        val loop = GameLoop(tree, CountingRenderer(), NoopInput)
        loop.tick(16_666_666L)
        assertEquals(1, received.size)
        assertTrue(kotlin.math.abs(received[0] - 0.01666f) < 1e-4f)
    }

    @Test
    fun `tick order is physics then process then draw`() {
        val root = Node()
        val order = mutableListOf<String>()
        val node = object : Node() { override fun onProcess(dt: Float) { order += "process" } }
        root.addChild(node)
        val a = object : StaticBody2D() {
            override fun onBodyEntered(body: PhysicsBody2D) { order += "physics" }
        }.apply {
            transform = Transform(position = Vec2(0f, 0f))
            addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { size = Vec2(10f, 10f) } })
        }
        val b = StaticBody2D().apply {
            transform = Transform(position = Vec2(5f, 5f))
            addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { size = Vec2(10f, 10f) } })
        }
        root.addChild(a)
        root.addChild(b)
        val renderer = object : Renderer {
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
        val recorder = object : Node() {
            override fun onDraw(renderer: Renderer) { order += "draw" }
        }
        root.addChild(recorder)
        val tree = SceneTree(root)
        // 20 ms > 1/60s so the accumulator drains one full physics step.
        GameLoop(tree, renderer, NoopInput, PhysicsSystem()).tick(20_000_000L)
        assertEquals(listOf("physics", "process", "draw"), order)
    }

    @Test
    fun `large dtNanos is clamped to maxDt`() {
        val root = Node()
        val received = mutableListOf<Float>()
        root.addChild(object : Node() { override fun onProcess(dt: Float) { received += dt } })
        val tree = SceneTree(root)
        val loop = GameLoop(tree, CountingRenderer(), NoopInput).apply { maxDt = 0.05f }
        loop.tick(10_000_000_000L) // 10 seconds raw
        assertEquals(0.05f, received[0])
    }
}
