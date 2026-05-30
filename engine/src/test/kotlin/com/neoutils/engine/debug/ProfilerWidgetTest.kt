package com.neoutils.engine.debug

import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProfilerWidgetTest {

    @Test
    fun `each tree owns an independent FrameProfile`() {
        val a = SceneTree(Node()).also { it.start() }
        val b = SceneTree(Node()).also { it.start() }
        assertTrue(a.debug.frameProfile !== b.debug.frameProfile)
        a.debug.frameProfile.totalNanos = 123L
        assertEquals(0L, b.debug.frameProfile.totalNanos, "writing A must not touch B")
    }

    @Test
    fun `profiler is a registered built-in row`() {
        val tree = SceneTree(Node()).also { it.start() }
        assertNotNull(tree.debug.profiler)
        assertTrue(tree.debug.widgets.any { it === tree.debug.profiler }, "appears in HUD widget list")
        assertEquals("Profiler", tree.debug.profiler.title)
        assertEquals(false, tree.debug.profiler.enabled, "off by default")
    }

    @Test
    fun `disabled profiler emits no draw calls`() {
        val tree = SceneTree(Node()).also { it.resize(800f, 600f); it.start() }
        tree.debug.profiler.enabled = false
        // Even with a populated profile, a disabled widget never samples nor draws.
        tree.debug.frameProfile.totalNanos = 5_000_000L
        tree.process(0.016f)
        val recorder = RecordingRenderer()
        tree.render(recorder)
        assertEquals(0, recorder.events.count { it is RecordedEvent.Text })
    }

    @Test
    fun `enabled profiler samples the FrameProfile and draws`() {
        val tree = SceneTree(Node()).also { it.resize(800f, 600f); it.start() }
        tree.debug.profiler.enabled = true
        tree.debug.frameProfile.apply {
            hitTestNanos = 1_000_000L
            physicsNanos = 4_000_000L
            processNanos = 2_000_000L
            renderNanos = 3_000_000L
            totalNanos = 11_000_000L
            physicsSteps = 2
        }
        tree.process(0.016f) // onProcess samples into the moving-average window
        val recorder = RecordingRenderer()
        tree.render(recorder)
        assertTrue(recorder.events.count { it is RecordedEvent.Text } > 0, "draws per-phase rows")
    }

    @Test
    fun `enable flip resets the moving-average windows`() {
        val tree = SceneTree(Node()).also { it.resize(800f, 600f); it.start() }
        val profiler = tree.debug.profiler
        profiler.enabled = true
        tree.debug.frameProfile.totalNanos = 8_000_000L
        repeat(5) { tree.process(0.016f) } // accumulate samples
        // Disable then enable: windows must reset, so with no sample taken yet
        // the widget draws nothing.
        profiler.enabled = false
        profiler.enabled = true
        val recorder = RecordingRenderer()
        tree.render(recorder)
        assertEquals(0, recorder.events.count { it is RecordedEvent.Text }, "no stale averages drawn")
    }

    @Test
    fun `convenience field and registry instance are the same`() {
        val tree = SceneTree(Node()).also { it.start() }
        val viaFind = tree.debug.find<ProfilerWidget>()
        assertSame(tree.debug.profiler, viaFind)
    }
}
