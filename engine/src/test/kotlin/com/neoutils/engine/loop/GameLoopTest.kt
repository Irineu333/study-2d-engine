package com.neoutils.engine.loop

import com.neoutils.engine.input.Input
import com.neoutils.engine.input.Key
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.BoxCollider
import com.neoutils.engine.physics.Collider
import com.neoutils.engine.physics.PhysicsSystem
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Scene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private object NoopInput : Input {
    override val pointerPosition: Vec2 = Vec2.ZERO
    override fun isKeyDown(key: Key): Boolean = false
    override fun wasKeyPressed(key: Key): Boolean = false
}

private class CountingRenderer : Renderer {
    var clears = 0
    override fun clear(color: Color) { clears++ }
    override fun drawRect(rect: Rect, color: Color, filled: Boolean) {}
    override fun drawCircle(center: Vec2, radius: Float, color: Color, filled: Boolean) {}
    override fun drawText(text: String, position: Vec2, size: Float, color: Color) {}
}

class GameLoopTest {

    @Test
    fun `tick converts nanoseconds to seconds`() {
        val scene = Scene()
        val received = mutableListOf<Float>()
        val node = object : Node() { override fun onUpdate(dt: Float) { received += dt } }
        scene.addChild(node)
        val loop = GameLoop(scene, CountingRenderer(), NoopInput)
        loop.tick(16_666_666L)
        assertEquals(1, received.size)
        assertTrue(kotlin.math.abs(received[0] - 0.01666f) < 1e-4f)
    }

    @Test
    fun `tick order is update then physics then render`() {
        val scene = Scene()
        val order = mutableListOf<String>()
        // Sensor collider records onCollide ordering relative to update.
        val node = object : Node() { override fun onUpdate(dt: Float) { order += "update" } }
        scene.addChild(node)
        val a = object : BoxCollider(Vec2(10f, 10f)) {
            override fun onCollide(other: Collider) { order += "physics" }
        }
        val b = object : BoxCollider(Vec2(10f, 10f)) {
            override fun onCollide(other: Collider) { /* not recorded */ }
        }
        scene.addChild(a)
        scene.addChild(b)
        val renderer = object : Renderer {
            override fun clear(color: Color) {}
            override fun drawRect(rect: Rect, color: Color, filled: Boolean) {}
            override fun drawCircle(center: Vec2, radius: Float, color: Color, filled: Boolean) {}
            override fun drawText(text: String, position: Vec2, size: Float, color: Color) {}
        }
        // Inject a render-time recording node.
        val recorder = object : Node() {
            override fun onRender(renderer: Renderer) { order += "render" }
        }
        scene.addChild(recorder)
        GameLoop(scene, renderer, NoopInput, PhysicsSystem()).tick(16_000_000L)
        assertEquals(listOf("update", "physics", "render"), order)
    }

    @Test
    fun `large dtNanos is clamped to maxDt`() {
        val scene = Scene()
        val received = mutableListOf<Float>()
        scene.addChild(object : Node() { override fun onUpdate(dt: Float) { received += dt } })
        val loop = GameLoop(scene, CountingRenderer(), NoopInput).apply { maxDt = 0.05f }
        loop.tick(10_000_000_000L) // 10 seconds raw
        assertEquals(0.05f, received[0])
    }
}
