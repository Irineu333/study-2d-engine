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
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NodeInspectorWidgetTest {

    private fun treeWithSelectedTarget(): Triple<SceneTree, ColorRect, RecordingRenderer> {
        val target = ColorRect().apply {
            name = "Target"
            size = Vec2(40f, 20f)
            transform = Transform(position = Vec2(100f, 100f))
        }
        val group = Node().apply { name = "Group"; addChild(target) }
        val root = Node().apply { name = "Root"; addChild(group) }
        val tree = SceneTree(root).also {
            it.resize(800f, 600f)
            // Matches RecordingRenderer.measureText so the dock's contentSize
            // (which measures via tree.textMeasurer) agrees with the draw.
            it.textMeasurer = TextMeasurer { text, size -> Vec2(text.length * size * 0.5f, size) }
            it.start()
        }
        tree.debug.inspector.enabled = true
        tree.hitTestPick(FakeInput(pointer = Vec2(120f, 110f), leftClicked = true))
        assertSame(target, tree.debug.inspector.selected)
        return Triple(tree, target, RecordingRenderer())
    }

    private fun textLines(recorder: RecordingRenderer): List<String> =
        recorder.events.filterIsInstance<RecordedEvent.Text>().map { it.text }

    @Test
    fun `detail derives its enabled from the inspector master`() {
        val (tree, _, _) = treeWithSelectedTarget()
        assertTrue(tree.debug.nodeInspector.enabled, "enabled follows the master")
        tree.debug.inspector.enabled = false
        assertFalse(tree.debug.nodeInspector.enabled, "disabling the master disables the detail")
        // The slave's own setter is a no-op — it cannot desync from the master.
        tree.debug.nodeInspector.enabled = true
        assertFalse(tree.debug.nodeInspector.enabled)
    }

    @Test
    fun `detail carries no window controls`() {
        val (tree, _, _) = treeWithSelectedTarget()
        assertFalse(tree.debug.nodeInspector.closable, "the slave detail panel is not closable")
        assertFalse(tree.debug.nodeInspector.collapsible, "the slave detail panel is not collapsible")
    }

    @Test
    fun `panel lists the selection's inspect properties with current values`() {
        val (tree, _, recorder) = treeWithSelectedTarget()
        tree.render(recorder)
        val lines = textLines(recorder)
        // Header carries the type and name; the property appears as a key/value pair.
        assertTrue(lines.any { it == "ColorRect" }, lines.toString())
        assertTrue(lines.any { it == "Target" }, lines.toString())
        assertTrue(lines.any { it == "Properties" }, lines.toString())
        assertTrue(lines.any { it == "size" }, lines.toString())
        assertTrue(lines.any { it.contains("40.0") }, lines.toString())
    }

    @Test
    fun `detail panel draws no breadcrumb`() {
        val (tree, _, recorder) = treeWithSelectedTarget()
        tree.render(recorder)
        val lines = textLines(recorder)
        assertFalse(
            lines.any { it.contains(" / ") },
            "the detail panel must not draw a root→selected breadcrumb: $lines",
        )
    }

    @Test
    fun `Node2D selection shows its world transform`() {
        val (tree, _, recorder) = treeWithSelectedTarget()
        tree.render(recorder)
        val lines = textLines(recorder)
        assertTrue(lines.any { it == "Transform (world)" }, lines.toString())
        assertTrue(lines.any { it == "pos" }, lines.toString())
        assertTrue(lines.any { it.contains("100.0") }, lines.toString())
    }

    @Test
    fun `empty size when nothing is selected`() {
        val tree = SceneTree(Node()).also {
            it.resize(800f, 600f)
            it.textMeasurer = TextMeasurer { text, size -> Vec2(text.length * size * 0.5f, size) }
            it.start()
        }
        tree.debug.inspector.enabled = true // master on, but no selection
        assertNull(tree.debug.inspector.selected)
        assertEquals(Vec2.ZERO, tree.debug.nodeInspector.bodySize(), "no selection → no body")
    }

    @Test
    fun `panel is read-only - emits no interactive children`() {
        val (tree, _, _) = treeWithSelectedTarget()
        // The widget draws plain text; it never builds Panel/Button children.
        assertEquals(0, tree.debug.nodeInspector.children.size)
    }

    @Test
    fun `disabled inspector draws nothing`() {
        val (tree, _, recorder) = treeWithSelectedTarget()
        tree.debug.inspector.enabled = false
        tree.render(recorder)
        assertEquals(0, recorder.events.count { it is RecordedEvent.Text })
    }
}
