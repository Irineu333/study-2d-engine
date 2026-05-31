package com.neoutils.engine.debug

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.scene.ColorRect
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.FakeInput
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScenePickerWidgetTest {

    private fun treeWithSelectedTarget(): Triple<SceneTree, ColorRect, RecordingRenderer> {
        val target = ColorRect().apply {
            name = "Target"
            size = Vec2(40f, 20f)
            transform = Transform(position = Vec2(100f, 100f))
        }
        val group = Node().apply { name = "Group"; addChild(target) }
        val root = Node().apply { name = "Root"; addChild(group) }
        val tree = SceneTree(root).also { it.resize(800f, 600f); it.start() }
        tree.debug.scenePicker.enabled = true
        tree.hitTestPick(FakeInput(pointer = Vec2(120f, 110f), leftClicked = true))
        assertSame(target, tree.debug.scenePicker.selected)
        return Triple(tree, target, RecordingRenderer())
    }

    private fun textLines(recorder: RecordingRenderer): List<String> =
        recorder.events.filterIsInstance<RecordedEvent.Text>().map { it.text }

    @Test
    fun `panel lists the selection's inspect properties with current values`() {
        val (tree, _, recorder) = treeWithSelectedTarget()
        tree.render(recorder)
        val lines = textLines(recorder)
        assertTrue(lines.any { it.startsWith("type: ColorRect") }, lines.toString())
        assertTrue(lines.any { it.startsWith("name: Target") }, lines.toString())
        assertTrue(lines.any { it.startsWith("size = ") && it.contains("40.0") }, lines.toString())
    }

    @Test
    fun `breadcrumb shows the ancestor chain root to selected`() {
        val (tree, _, recorder) = treeWithSelectedTarget()
        tree.render(recorder)
        val lines = textLines(recorder)
        assertTrue(lines.any { it == "Root / Group / Target" }, lines.toString())
    }

    @Test
    fun `Node2D selection shows its world transform`() {
        val (tree, _, recorder) = treeWithSelectedTarget()
        tree.render(recorder)
        val lines = textLines(recorder)
        assertTrue(lines.any { it.startsWith("world.pos: ") && it.contains("100.0") }, lines.toString())
    }

    @Test
    fun `selection cleared when the node detaches`() {
        val (tree, target, _) = treeWithSelectedTarget()
        target.parent!!.removeChild(target)
        tree.process(0f) // ScenePickerWidget.onProcess clears a dead selection
        assertNull(tree.debug.scenePicker.selected)
    }

    @Test
    fun `panel is read-only - emits no interactive children`() {
        val (tree, _, recorder) = treeWithSelectedTarget()
        tree.render(recorder)
        // The widget draws plain text; it never builds Panel/Button children.
        assertEquals(0, tree.debug.scenePicker.children.size)
    }

    @Test
    fun `disabled picker draws nothing`() {
        val (tree, _, recorder) = treeWithSelectedTarget()
        tree.debug.scenePicker.enabled = false
        tree.render(recorder)
        assertEquals(0, recorder.events.count { it is RecordedEvent.Text })
    }
}
