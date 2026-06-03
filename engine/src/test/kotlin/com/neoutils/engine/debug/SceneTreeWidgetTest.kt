package com.neoutils.engine.debug

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.render.TextMeasurer
import com.neoutils.engine.scene.ColorRect
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.FakeInput
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SceneTreeWidgetTest {

    private fun startedTree(root: Node, w: Float = 800f, h: Float = 600f): SceneTree =
        SceneTree(root).also {
            it.resize(w, h)
            it.textMeasurer = TextMeasurer { text, size -> Vec2(text.length * size * 0.5f, size) }
            it.start()
            it.debug.inspector.enabled = true
        }

    private fun texts(recorder: RecordingRenderer): List<RecordedEvent.Text> =
        recorder.events.filterIsInstance<RecordedEvent.Text>()

    private fun textAt(recorder: RecordingRenderer, s: String): RecordedEvent.Text? =
        texts(recorder).firstOrNull { it.text == s }

    @Test
    fun `tree lists the hierarchy in DFS order indented by depth`() {
        val a1 = Node().apply { name = "A1" }
        val a = Node().apply { name = "A"; addChild(a1) }
        val b = Node().apply { name = "B" }
        val root = Node().apply { name = "Root"; addChild(a); addChild(b) }
        val tree = startedTree(root)

        val recorder = RecordingRenderer()
        tree.render(recorder)

        val rootName = textAt(recorder, "Root")
        val aName = textAt(recorder, "A")
        val a1Name = textAt(recorder, "A1")
        val bName = textAt(recorder, "B")
        assertNotNull(rootName); assertNotNull(aName); assertNotNull(a1Name); assertNotNull(bName)
        // Indentation grows with depth: Root(0) < A(1) < A1(2).
        assertTrue(aName!!.position.x > rootName!!.position.x, "depth 1 is indented past the root")
        assertTrue(a1Name!!.position.x > aName.position.x, "depth 2 is indented past depth 1")
        // DFS pre-order: Root, A, A1, B (A1 drawn before B).
        assertTrue(a1Name.position.y < bName!!.position.y, "A1 precedes its uncle B in DFS order")
    }

    @Test
    fun `tree excludes the debug layer subtree`() {
        val root = Node().apply { name = "Root" }
        val tree = startedTree(root)

        val recorder = RecordingRenderer()
        tree.render(recorder)
        assertFalse(texts(recorder).any { it.text == DebugLayer.NODE_NAME }, "no __debug row")
        assertFalse(texts(recorder).any { it.text == "DebugLayer" }, "no DebugLayer type row")
    }

    @Test
    fun `clicking a row selects the node and consumes the click`() {
        val a1 = Node().apply { name = "A1" }
        val a = Node().apply { name = "A"; addChild(a1) }
        val root = Node().apply { name = "Root"; addChild(a) }
        val tree = startedTree(root)

        // Lay the panel out so origin and the row layout are settled.
        tree.render(RecordingRenderer())
        val widget = tree.debug.inspector
        // Row index 2 is A1 (DFS: Root=0, A=1, A1=2).
        val rowTop = widget.bodyOrigin.y + DebugTheme.padding + 2 * TREE_H
        val pointer = Vec2(widget.origin.x + 5f, rowTop + TREE_H / 2f)

        val input = FakeInput(pointer = pointer, leftClicked = true, leftDown = true)
        tree.input = input
        tree.hitTestUI(input)
        tree.process(0f)

        assertSame(a1, widget.selected, "clicking A1's row selects it")
        assertTrue(input.mouseClickConsumed, "the row click is consumed as UI")
    }

    @Test
    fun `selected row is highlighted`() {
        val a = Node().apply { name = "A" }
        val root = Node().apply { name = "Root"; addChild(a) }
        val tree = startedTree(root)
        tree.debug.inspector.select(a)

        val recorder = RecordingRenderer()
        tree.render(recorder)
        val highlight = recorder.events.filterIsInstance<RecordedEvent.Rect>()
            .any { it.filled && it.color == SELECTED_ROW_COLOR }
        assertTrue(highlight, "the selected row draws a filled highlight band")
    }

    @Test
    fun `overflow tail is summarized when the tree does not fit`() {
        val root = Node().apply { name = "Root" }
        repeat(40) { i -> root.addChild(Node().apply { name = "N$i" }) }
        // A short viewport forces most rows out.
        val tree = startedTree(root, h = 120f)

        val recorder = RecordingRenderer()
        tree.render(recorder)
        val overflow = texts(recorder).firstOrNull { it.text.startsWith("… (+") }
        assertNotNull(overflow, "an overflow row summarizes the hidden tail")
    }

    @Test
    fun `explicit tree selection resets the world-pick cycling`() {
        // Three overlapping boxes; DFS draw-order: back, mid, front.
        val back = box("Back", Vec2(100f, 100f))
        val mid = box("Mid", Vec2(100f, 100f))
        val front = box("Front", Vec2(100f, 100f))
        val root = Node().apply { addChild(back); addChild(mid); addChild(front) }
        val tree = startedTree(root)
        val widget = tree.debug.inspector

        // Fresh pick → front-most, then a near pick advances the cycle to mid.
        tree.hitTestPick(FakeInput(pointer = Vec2(110f, 110f), leftClicked = true))
        assertSame(front, widget.selected)
        tree.hitTestPick(FakeInput(pointer = Vec2(111f, 110f), leftClicked = true))
        assertSame(mid, widget.selected)

        // An explicit tree selection resets the cycle.
        widget.select(back)
        assertSame(back, widget.selected)

        // A near-same pick now starts fresh from the front-most, not from mid.
        tree.hitTestPick(FakeInput(pointer = Vec2(110f, 111f), leftClicked = true))
        assertSame(front, widget.selected, "explicit selection reset the cycle to a fresh pick")
    }

    @Test
    fun `selection cleared when the node detaches`() {
        val target = box("Target", Vec2(100f, 100f))
        val root = Node().apply { addChild(target) }
        val tree = startedTree(root)
        tree.debug.inspector.select(target)
        assertSame(target, tree.debug.inspector.selected)

        root.removeChild(target)
        tree.process(0f) // onProcess clears a dead selection
        assertEquals(null, tree.debug.inspector.selected)
    }

    private fun box(name: String, pos: Vec2) = ColorRect().apply {
        this.name = name
        size = Vec2(40f, 40f)
        transform = Transform(position = pos)
    }
}
