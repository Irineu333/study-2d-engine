package com.neoutils.engine.scene

import com.neoutils.engine.serialization.NodeRegistry
import com.neoutils.engine.serialization.SceneLoader
import com.neoutils.engine.tree.SceneTree
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimerTest {

    init {
        NodeRegistry.registerEngineTypes()
    }

    private fun fresh(): Timer {
        return NodeRegistry.create("engine.Timer") as Timer
    }

    @Test
    fun `defaults via NodeRegistry`() {
        val t = fresh()
        assertEquals(1f, t.waitTime)
        assertEquals(false, t.autostart)
        assertEquals(false, t.oneShot)
        assertEquals(TimerMode.PHYSICS, t.processCallback)
        assertEquals(0f, t.timeLeft)
        assertTrue(t.isStopped)
    }

    @Test
    fun `autostart=true schedules first emit after waitTime, not immediately`() {
        val root = Node()
        val t = Timer().apply { waitTime = 0.2f; autostart = true }
        root.addChild(t)
        val tree = SceneTree(root)
        tree.start()

        var fired = 0
        t.timeout.connect { fired++ }

        repeat(6) { tree.physicsProcess(1f / 60f) } // 0.1s
        assertEquals(0, fired)

        repeat(7) { tree.physicsProcess(1f / 60f) } // ~0.217s total, crosses 0.2s
        assertEquals(1, fired)
    }

    @Test
    fun `autostart=false leaves Timer stopped after onEnter`() {
        val root = Node()
        val t = Timer().apply { waitTime = 0.5f; autostart = false }
        root.addChild(t)
        SceneTree(root).start()

        assertTrue(t.isStopped)
        assertEquals(0f, t.timeLeft)
    }

    @Test
    fun `oneShot=true emits once and stops`() {
        val root = Node()
        val t = Timer().apply { waitTime = 0.05f; autostart = true; oneShot = true }
        root.addChild(t)
        val tree = SceneTree(root)
        tree.start()

        var fired = 0
        t.timeout.connect { fired++ }

        repeat(20) { tree.physicsProcess(1f / 60f) } // 0.333s
        assertEquals(1, fired)
        assertTrue(t.isStopped)
    }

    @Test
    fun `oneShot=false emits ~10 times in 1s at waitTime=0_1`() {
        val root = Node()
        val t = Timer().apply { waitTime = 0.1f; autostart = true }
        root.addChild(t)
        val tree = SceneTree(root)
        tree.start()

        var fired = 0
        t.timeout.connect { fired++ }

        repeat(60) { tree.physicsProcess(1f / 60f) } // 1s
        assertTrue(abs(fired - 10) <= 1, "expected ~10 emissions, got $fired")
    }

    @Test
    fun `PHYSICS mode does not advance in onProcess`() {
        val root = Node()
        val t = Timer().apply { waitTime = 0.5f; autostart = true; processCallback = TimerMode.PHYSICS }
        root.addChild(t)
        val tree = SceneTree(root)
        tree.start()

        var fired = 0
        t.timeout.connect { fired++ }

        repeat(10) { tree.process(0.1f) } // 1s of process
        assertEquals(0, fired)
        assertEquals(0.5f, t.timeLeft)
    }

    @Test
    fun `IDLE mode does not advance in onPhysicsProcess`() {
        val root = Node()
        val t = Timer().apply { waitTime = 0.5f; autostart = true; processCallback = TimerMode.IDLE }
        root.addChild(t)
        val tree = SceneTree(root)
        tree.start()

        var fired = 0
        t.timeout.connect { fired++ }

        repeat(10) { tree.physicsProcess(0.1f) }
        assertEquals(0, fired)
        assertEquals(0.5f, t.timeLeft)
    }

    @Test
    fun `switching processCallback at runtime takes effect next tick`() {
        val root = Node()
        val t = Timer().apply { waitTime = 1f; autostart = true; processCallback = TimerMode.PHYSICS }
        root.addChild(t)
        val tree = SceneTree(root)
        tree.start()

        t.processCallback = TimerMode.IDLE

        val before = t.timeLeft
        tree.process(0.1f)
        assertEquals(before - 0.1f, t.timeLeft, 1e-5f)

        val afterProcess = t.timeLeft
        tree.physicsProcess(0.1f)
        assertEquals(afterProcess, t.timeLeft)
    }

    @Test
    fun `start with non-positive override throws with offending value`() {
        val t = Timer()
        val e0 = assertFailsWith<IllegalArgumentException> { t.start(0f) }
        assertTrue(e0.message!!.contains("0"), "message should include offending value: ${e0.message}")

        val eNeg = assertFailsWith<IllegalArgumentException> { t.start(-1f) }
        assertTrue(eNeg.message!!.contains("-1"), "message should include offending value: ${eNeg.message}")
    }

    @Test
    fun `start with override applies only to first emission`() {
        val root = Node()
        val t = Timer().apply { waitTime = 0.5f; autostart = false; processCallback = TimerMode.PHYSICS }
        root.addChild(t)
        val tree = SceneTree(root)
        tree.start()

        val firings = mutableListOf<Float>()
        var elapsed = 0f
        t.timeout.connect { firings += elapsed }

        t.start(0.1f)
        val dt = 1f / 60f
        repeat(50) {
            tree.physicsProcess(dt)
            elapsed += dt
        }

        assertTrue(firings.size >= 2, "expected at least two emissions, got ${firings.size}")
        assertTrue(abs(firings[0] - 0.1f) < 0.02f, "first ~0.1s, got ${firings[0]}")
        val gap = firings[1] - firings[0]
        assertTrue(abs(gap - 0.5f) < 0.02f, "second emission ~0.5s after first, got gap=$gap")
    }

    @Test
    fun `stop zeroes timeLeft and prevents further emissions`() {
        val root = Node()
        val t = Timer().apply { waitTime = 0.1f; autostart = true }
        root.addChild(t)
        val tree = SceneTree(root)
        tree.start()

        var fired = 0
        t.timeout.connect { fired++ }

        t.stop()
        assertEquals(0f, t.timeLeft)
        assertTrue(t.isStopped)

        repeat(30) { tree.physicsProcess(1f / 60f) }
        assertEquals(0, fired)
    }

    @Test
    fun `re-attach with autostart restarts cleanly`() {
        val root = Node()
        val t = Timer().apply { waitTime = 0.4f; autostart = true }
        root.addChild(t)
        val tree = SceneTree(root)
        tree.start()

        assertEquals(0.4f, t.timeLeft)
        tree.physicsProcess(0.1f)
        assertTrue(t.timeLeft < 0.4f)

        root.removeChild(t)
        assertTrue(t.isStopped)

        root.addChild(t)
        assertEquals(0.4f, t.timeLeft)
        assertFalse(t.isStopped)
    }

    @Test
    fun `timeLeft is transient and never serialized`() {
        val root = Node().apply { name = "Root" }
        val t = Timer().apply { name = "MoveTimer"; waitTime = 0.4f }
        root.addChild(t)
        t.timeLeft = 0.5f
        val json = SceneLoader.save(root)
        assertFalse(json.contains("timeLeft"), "timeLeft should not appear in JSON: $json")
    }

    @Test
    fun `removeChild stops the Timer automatically`() {
        val root = Node()
        val t = Timer().apply { waitTime = 0.1f; autostart = true }
        root.addChild(t)
        val tree = SceneTree(root)
        tree.start()

        assertFalse(t.isStopped)
        root.removeChild(t)
        assertTrue(t.isStopped)
    }
}
