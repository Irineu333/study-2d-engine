package com.neoutils.engine.tree

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.CanvasLayer
import com.neoutils.engine.scene.ColorRect
import com.neoutils.engine.scene.Label
import com.neoutils.engine.scene.MouseFilter
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Panel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `visible` (render + hit-test skip) and `mouseFilter` (STOP/PASS/IGNORE). */
class ControlVisibilityFilterTest {

    private fun treeWith(layer: CanvasLayer): SceneTree {
        val root = Node()
        root.addChild(layer)
        val tree = SceneTree(root)
        tree.resize(800f, 600f)
        tree.start()
        return tree
    }

    // --- visibility: render ---

    @Test
    fun `invisible Control and its subtree are not drawn`() {
        val label = Label().apply { text = "hi" }
        val panel = Panel().apply { visible = false; addChild(label) }
        val tree = treeWith(CanvasLayer().apply { addChild(panel) })

        val recorder = RecordingRenderer()
        tree.render(recorder)

        assertTrue(recorder.events.filterIsInstance<RecordedEvent.Rect>().isEmpty(), "panel not drawn")
        assertTrue(recorder.events.filterIsInstance<RecordedEvent.Text>().isEmpty(), "child label not drawn")
    }

    @Test
    fun `sibling of an invisible Control still draws`() {
        val shown = Panel().apply { name = "A" }
        val hidden = Panel().apply { name = "B"; visible = false }
        val tree = treeWith(CanvasLayer().apply { addChild(shown); addChild(hidden) })

        val recorder = RecordingRenderer()
        tree.render(recorder)

        assertEquals(1, recorder.events.filterIsInstance<RecordedEvent.Rect>().size, "only the visible panel draws")
    }

    // --- visibility: hit-test ---

    @Test
    fun `invisible Button is not hit-tested`() {
        val button = Button().apply {
            position = Vec2(100f, 100f); size = Vec2(50f, 30f); visible = false
        }
        val tree = treeWith(CanvasLayer().apply { addChild(button) })
        var fired = 0
        button.pressed.connect { fired++ }
        val input = FakeInput(pointer = Vec2(120f, 110f), leftClicked = true)
        tree.input = input

        tree.hitTestUI(input)
        assertFalse(input.mouseClickConsumed, "invisible button must not absorb the click")
    }

    // --- mouseFilter ---

    @Test
    fun `STOP Panel consumes the press`() {
        val panel = Panel().apply { position = Vec2(100f, 100f); size = Vec2(50f, 50f) }
        val tree = treeWith(CanvasLayer().apply { addChild(panel) })
        val input = FakeInput(pointer = Vec2(120f, 120f), leftClicked = true)
        tree.input = input

        tree.hitTestUI(input)
        assertTrue(input.mouseClickConsumed, "STOP panel is opaque UI")
    }

    @Test
    fun `IGNORE Label passes the press through`() {
        val label = Label().apply { position = Vec2(100f, 100f); size = Vec2(50f, 50f) }
        // Label localBounds needs a measurer to be non-null for hit-test rects.
        val tree = treeWith(CanvasLayer().apply { addChild(label) })
        tree.textMeasurer = com.neoutils.engine.render.TextMeasurer { _, _ -> Vec2(50f, 50f) }
        val input = FakeInput(pointer = Vec2(110f, 110f), leftClicked = true)
        tree.input = input

        tree.hitTestUI(input)
        assertFalse(input.mouseClickConsumed, "IGNORE never consumes")
    }

    @Test
    fun `PASS Panel registers without consuming`() {
        val panel = Panel().apply {
            position = Vec2(100f, 100f); size = Vec2(50f, 50f); mouseFilter = MouseFilter.PASS
        }
        val tree = treeWith(CanvasLayer().apply { addChild(panel) })
        val input = FakeInput(pointer = Vec2(120f, 120f), leftClicked = true)
        tree.input = input

        tree.hitTestUI(input)
        assertFalse(input.mouseClickConsumed, "PASS observes but does not consume")
    }

    @Test
    fun `default mouse filters match widget intent`() {
        assertEquals(MouseFilter.STOP, Button().mouseFilter)
        assertEquals(MouseFilter.STOP, Panel().mouseFilter)
        assertEquals(MouseFilter.IGNORE, ColorRect().mouseFilter)
        assertEquals(MouseFilter.IGNORE, Label().mouseFilter)
    }
}
