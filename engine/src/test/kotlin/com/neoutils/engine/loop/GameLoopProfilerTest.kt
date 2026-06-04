package com.neoutils.engine.loop

import com.neoutils.engine.input.Input
import com.neoutils.engine.input.Key
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.PhysicsSystem
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class StubInput : Input {
    override val pointerPosition: Vec2 = Vec2.ZERO
    override var mouseClickConsumed: Boolean = true
    override fun isKeyDown(key: Key): Boolean = false
    override fun wasKeyPressed(key: Key): Boolean = false
    override fun isMouseDown(button: MouseButton): Boolean = false
    override fun wasMouseClickedRaw(button: MouseButton): Boolean = false
}

private class StubRenderer : Renderer {
    override fun clear(color: Color) {}
    override fun drawRect(rect: Rect, color: Color, filled: Boolean) {}
    override fun drawCircle(center: Vec2, radius: Float, color: Color, filled: Boolean, thickness: Float) {}
    override fun drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color) {}
    override fun drawText(text: String, position: Vec2, size: Float, color: Color) {}
    override fun measureText(text: String, size: Float): Vec2 = Vec2.ZERO
    override fun drawPolygon(points: List<Vec2>, color: Color) {}
    override fun pushTransform(translation: Vec2, rotation: Float, scale: Vec2) {}
    override fun popTransform() {}
    override fun pushClip(rect: Rect) {}
    override fun popClip() {}
}

/** Burns a fixed amount of wall time inside each physics step so the profiler's
 *  per-step aggregation is observable above timer noise. */
private class SlowPhysicsProbe(private val spinNanos: Long) : Node() {
    override fun onPhysicsProcess(dt: Float) {
        val deadline = System.nanoTime() + spinNanos
        while (System.nanoTime() < deadline) { /* busy-wait */ }
    }
}

class GameLoopProfilerTest {

    private fun setup(probe: Node = Node()): Pair<SceneTree, GameLoop> {
        val root = Node().apply { addChild(probe) }
        val tree = SceneTree(root)
        val loop = GameLoop(tree, StubRenderer(), StubInput(), PhysicsSystem())
        tree.start()
        return tree to loop
    }

    @Test
    fun `phases are not recorded when profiling is off, recorded when on`() {
        val (tree, loop) = setup()
        val profile = tree.debug.frameProfile
        // Sentinel: writeProfile always overwrites, so any change proves a write.
        profile.hitTestNanos = -1
        profile.processNanos = -1
        profile.renderNanos = -1
        profile.totalNanos = -1

        tree.debug.profiler.enabled = false
        loop.tick(16_000_000L)
        assertEquals(-1, profile.totalNanos, "off: no write")
        assertEquals(-1, profile.processNanos)
        assertEquals(-1, profile.renderNanos)

        tree.debug.profiler.enabled = true
        loop.tick(16_000_000L)
        assertTrue(profile.totalNanos > 0, "on: total populated (${profile.totalNanos})")
        assertTrue(profile.processNanos >= 0 && profile.processNanos != -1L, "process populated")
        assertTrue(profile.renderNanos >= 0 && profile.renderNanos != -1L, "render populated")
        assertTrue(profile.hitTestNanos >= 0 && profile.hitTestNanos != -1L, "hitTest populated")
    }

    @Test
    fun `physics phase aggregates the fixed-step loop`() {
        val spin = 4_000_000L // 4ms per step
        val (tree, loop) = setup(SlowPhysicsProbe(spin))
        tree.debug.profiler.enabled = true
        // 0.06s drains exactly 3 steps at 60Hz (3 * 1/60 = 0.05 consumed, 4th needs 0.0667).
        loop.tick(60_000_000L)
        val profile = tree.debug.frameProfile
        assertEquals(3, profile.physicsSteps)
        // Summed across 3 spins of ~4ms ⇒ comfortably above a single step's worth.
        assertTrue(
            profile.physicsNanos >= 2 * spin,
            "physicsNanos should sum 3 steps (got ${profile.physicsNanos}, one step ≈ $spin)",
        )
    }

    @Test
    fun `total covers at least the sum of the four phases`() {
        val (tree, loop) = setup(SlowPhysicsProbe(2_000_000L))
        tree.debug.profiler.enabled = true
        loop.tick(33_000_000L)
        val p = tree.debug.frameProfile
        val sum = p.hitTestNanos + p.physicsNanos + p.processNanos + p.renderNanos
        assertTrue(p.totalNanos >= sum, "total ${p.totalNanos} should be ≥ Σphases $sum")
    }

    @Test
    fun `profiling off takes no measurements`() {
        val (tree, loop) = setup()
        val profile = tree.debug.frameProfile
        profile.totalNanos = 42L
        tree.debug.profiler.enabled = false
        repeat(5) { loop.tick(16_000_000L) }
        assertEquals(42L, profile.totalNanos, "untouched while profiling off")
    }
}