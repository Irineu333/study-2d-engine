package com.neoutils.engine.debug

import com.neoutils.engine.dx.Log
import com.neoutils.engine.dx.LogLevel
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LogOverlayWidgetTest {

    private val trees = mutableListOf<SceneTree>()
    private var previousGlobal: LogLevel = LogLevel.Info

    private fun newTree(): SceneTree {
        val tree = SceneTree(Node()).also {
            it.resize(800f, 600f)
            it.start()
        }
        trees += tree
        return tree
    }

    @BeforeTest
    fun setup() {
        previousGlobal = Log.config.globalLevel
        // Let every level reach emit; minLevel is the display-only filter.
        Log.config.globalLevel = LogLevel.Debug
    }

    @AfterTest
    fun teardown() {
        // Unsubscribe every overlay so it stops receiving the global stream.
        trees.forEach { it.debug.log.enabled = false }
        trees.clear()
        Log.config.globalLevel = previousGlobal
    }

    private fun overlayTexts(tree: SceneTree): List<RecordedEvent.Text> {
        val recorder = RecordingRenderer()
        tree.render(recorder)
        // Exclude the panel title-bar text drawn by the shared chrome; these
        // tests assert on the log lines, which are tagged "[...]".
        return recorder.events.filterIsInstance<RecordedEvent.Text>()
            .filter { it.text.startsWith("[") }
    }

    @Test
    fun `enabling subscribes and clears the buffer, disabling unsubscribes and stops recording`() {
        val tree = newTree()
        tree.debug.log.enabled = true
        Log.i("T", "first")
        assertEquals(1, overlayTexts(tree).size)

        // Disable, then emit — the entry must not reach the buffer.
        tree.debug.log.enabled = false
        Log.i("T", "while-closed")

        // Re-enable: buffer starts empty (live tail), so no draws until a new log.
        tree.debug.log.enabled = true
        assertTrue(overlayTexts(tree).isEmpty(), "re-enabled overlay must start empty")
        Log.i("T", "after-reopen")
        val texts = overlayTexts(tree)
        assertEquals(1, texts.size)
        assertEquals("[T] after-reopen", texts.single().text)
    }

    @Test
    fun `ring buffer keeps only the last N entries in emission order`() {
        val tree = newTree()
        tree.debug.log.enabled = true
        val total = 12 + 5
        for (i in 0 until total) Log.i("T", "m$i")

        val texts = overlayTexts(tree).map { it.text }
        // Newest drawn at the bottom → drawText order is newest first.
        val expected = (total - 1 downTo total - 12).map { "[T] m$it" }
        assertEquals(expected, texts)
    }

    @Test
    fun `lines are colored by level`() {
        val tree = newTree()
        tree.debug.log.enabled = true
        Log.w("T", "careful")
        Log.e("T", "boom")

        val texts = overlayTexts(tree)
        val warn = texts.single { it.text == "[T] careful" }
        val error = texts.single { it.text == "[T] boom" }
        assertEquals(DEBUG_LOG_WARN_COLOR, warn.color)
        assertEquals(DEBUG_LOG_ERROR_COLOR, error.color)
    }

    @Test
    fun `minLevel filters the display`() {
        val tree = newTree()
        tree.debug.log.enabled = true
        tree.debug.log.minLevel = LogLevel.Warn
        Log.d("T", "dbg")
        Log.i("T", "info")
        Log.w("T", "warn")
        Log.e("T", "err")

        val texts = overlayTexts(tree).map { it.text }
        assertEquals(setOf("[T] warn", "[T] err"), texts.toSet())
    }

    @Test
    fun `disabled overlay emits zero draws`() {
        val tree = newTree()
        tree.debug.log.enabled = false
        // Even logs emitted now must not be recorded or drawn.
        Log.i("T", "ignored")
        assertEquals(0, overlayTexts(tree).size)
    }

    @Test
    fun `lines stay within the bottom edge of the screen`() {
        val tree = newTree()
        tree.debug.log.enabled = true
        // Fill the buffer so the newest line sits closest to the bottom.
        for (i in 0 until 12 + 3) Log.i("T", "m$i")

        // `position.y` is the text's top edge; a line spans roughly one
        // line-height below it. The lowest line must not cross tree.size.y.
        val lineHeight = 16f
        val maxTop = overlayTexts(tree).maxOf { it.position.y }
        assertTrue(
            maxTop + lineHeight <= tree.size.y,
            "lowest line top=$maxTop + lineHeight overflows ${tree.size.y}",
        )
    }

    @Test
    fun `log overlay is a screen-canvas built-in present after start`() {
        val tree = newTree()
        val layer = tree.root.findChild(DebugLayer.NODE_NAME) as DebugLayer
        assertTrue(tree.debug.log in tree.debug.widgets)
        assertSame(layer.screenContainer, tree.debug.log.parent)
    }
}
