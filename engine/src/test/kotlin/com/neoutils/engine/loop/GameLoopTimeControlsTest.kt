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
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FlagInput : Input {
    override val pointerPosition: Vec2 = Vec2.ZERO
    override var mouseClickConsumed: Boolean = true
    override fun isKeyDown(key: Key): Boolean = false
    override fun wasKeyPressed(key: Key): Boolean = false
    override fun isMouseDown(button: MouseButton): Boolean = false
    override fun wasMouseClickedRaw(button: MouseButton): Boolean = false
}

private class NoopRenderer : Renderer {
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

/** Records physics-step count (1:1 with `physics.step`), process deltas, and draw count. */
private class Probe : Node() {
    var physicsSteps = 0
    val processDts = mutableListOf<Float>()
    var draws = 0
    override fun onPhysicsProcess(dt: Float) { physicsSteps++ }
    override fun onProcess(dt: Float) { processDts += dt }
    override fun onDraw(renderer: Renderer) { draws++ }
}

class GameLoopTimeControlsTest {

    private val physicsDtNanos = (1_000_000_000L / 60L)

    private fun setup(): Triple<SceneTree, Probe, GameLoop> {
        val root = Node()
        val probe = Probe()
        root.addChild(probe)
        val tree = SceneTree(root)
        val loop = GameLoop(tree, NoopRenderer(), FlagInput(), PhysicsSystem())
        return Triple(tree, probe, loop)
    }

    @Test
    fun `slow-motion runs a quarter of the physics steps`() {
        // 0.075s drains 4 steps at timeScale 1 (4 * 1/60 = 0.0667 consumed).
        val frameNanos = 75_000_000L

        val (treeFull, probeFull, loopFull) = setup()
        loopFull.tick(frameNanos)
        assertEquals(4, probeFull.physicsSteps)

        val (treeSlow, probeSlow, loopSlow) = setup()
        treeSlow.timeScale = 0.25f
        loopSlow.tick(frameNanos)
        assertEquals(1, probeSlow.physicsSteps, "0.25x accumulates a quarter as fast")
    }

    @Test
    fun `process frame delta is scaled by timeScale`() {
        val (tree, probe, loop) = setup()
        tree.timeScale = 0.5f
        // raw 0.02s → gameplay 0.01s, below maxDt and below physicsDt (no step).
        loop.tick(20_000_000L)
        assertEquals(1, probe.processDts.size)
        assertTrue(abs(probe.processDts[0] - 0.01f) < 1e-4f, "got ${probe.processDts[0]}")
        assertEquals(0, probe.physicsSteps)
    }

    @Test
    fun `paused runs no physics, processes at dt 0, still draws and hit-tests`() {
        val (tree, probe, loop) = setup()
        tree.paused = true
        loop.tick(100_000_000L) // a large delta that would drain several steps
        assertEquals(0, probe.physicsSteps)
        assertEquals(listOf(0f), probe.processDts)
        assertEquals(1, probe.draws)
        // hitTestUI runs every tick and resets mouseClickConsumed to false.
        assertEquals(false, tree.input?.mouseClickConsumed)
    }

    @Test
    fun `one requestStep while paused advances exactly one step`() {
        val (tree, probe, loop) = setup()
        tree.paused = true
        tree.requestStep()
        loop.tick(physicsDtNanos)
        assertEquals(1, probe.physicsSteps, "the step path advances one fixed step")
        assertEquals(listOf(1f / 60f), probe.processDts.map { it })
        assertEquals(1, probe.draws)
        // Next tick with no new request: frozen, no step.
        loop.tick(physicsDtNanos)
        assertEquals(1, probe.physicsSteps, "no further steps without a new request")
    }

    @Test
    fun `requestStep while running is a no-op`() {
        val (tree, probe, loop) = setup()
        tree.requestStep() // running: should be consumed and ignored
        // tiny delta below one physics step so the normal path drains zero steps
        loop.tick(1_000_000L)
        assertEquals(0, probe.physicsSteps)
    }

    @Test
    fun `defaults reproduce the plain tick`() {
        val (tree, probe, loop) = setup()
        // 20ms with defaults: one physics step, process at 0.02s, one draw.
        loop.tick(20_000_000L)
        assertEquals(1, probe.physicsSteps)
        assertEquals(1, probe.processDts.size)
        assertTrue(abs(probe.processDts[0] - 0.02f) < 1e-4f)
        assertEquals(1, probe.draws)
    }
}
