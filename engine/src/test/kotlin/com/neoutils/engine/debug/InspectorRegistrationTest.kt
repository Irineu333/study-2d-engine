package com.neoutils.engine.debug

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.ColorRect
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.FakeInput
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InspectorRegistrationTest {

    @Test
    fun `inspector is the single widget, detail and gizmo are its arms`() {
        val tree = SceneTree(Node()).also { it.start() }
        val layer = tree.root.findChild(DebugLayer.NODE_NAME) as DebugLayer

        assertNotNull(tree.debug.inspector)
        assertNotNull(tree.debug.nodeInspector)
        assertNotNull(tree.debug.selectionGizmo)
        // The tree view (master) is the real toggle widget on the screen container.
        assertTrue(tree.debug.inspector in tree.debug.widgets)
        assertSame(layer.screenContainer, tree.debug.inspector.parent)
        // The detail panel is a screen-space slave arm: docked but NOT a widget.
        assertSame(layer.screenContainer, tree.debug.nodeInspector.parent)
        assertTrue(tree.debug.nodeInspector !in tree.debug.widgets)
        // The gizmo lives in the world container so it draws in the world pass,
        // but is NOT a standalone widget — controlled through the master.
        assertSame(layer.worldContainer, tree.debug.selectionGizmo.parent)
        assertTrue(tree.debug.selectionGizmo !in tree.debug.widgets)
    }

    @Test
    fun `inspector default disabled and arms derive their enabled`() {
        val tree = SceneTree(Node()).also { it.start() }
        assertEquals(false, tree.debug.inspector.enabled)
        assertEquals(false, tree.debug.nodeInspector.enabled)
        assertEquals(false, tree.debug.selectionGizmo.enabled)
        // Enabling the master enables both arms; disabling disables them.
        tree.debug.inspector.enabled = true
        assertEquals(true, tree.debug.nodeInspector.enabled)
        assertEquals(true, tree.debug.selectionGizmo.enabled)
        tree.debug.inspector.enabled = false
        assertEquals(false, tree.debug.nodeInspector.enabled)
        assertEquals(false, tree.debug.selectionGizmo.enabled)
        // The arms' own setters are no-ops — they cannot desync from the master.
        tree.debug.nodeInspector.enabled = true
        tree.debug.selectionGizmo.enabled = true
        assertEquals(false, tree.debug.nodeInspector.enabled)
        assertEquals(false, tree.debug.selectionGizmo.enabled)
    }

    @Test
    fun `only the master surfaces as a HUD row`() {
        val tree = SceneTree(Node()).also { it.resize(800f, 600f); it.start() }
        tree.debug.hud.enabled = true
        tree.process(0f)      // buildPanel enqueues the panel + row buttons
        tree.applyPending()   // drain the deferred mutations so rows attach

        val rowNames = collectButtonNames(tree.root)
        assertTrue("DebugHudRow_Inspector" in rowNames, rowNames.toString())
        assertFalse("DebugHudRow_Selection" in rowNames, rowNames.toString())
        // Exactly one "Inspector" row — the detail panel adds none.
        assertEquals(1, rowNames.count { it == "DebugHudRow_Inspector" }, rowNames.toString())
    }

    @Test
    fun `disabled - no pick walk and no draws`() {
        val target = ColorRect().apply {
            name = "Target"
            size = Vec2(40f, 40f)
            transform = Transform(position = Vec2(100f, 100f))
        }
        val tree = SceneTree(Node().apply { addChild(target) }).also { it.resize(800f, 600f); it.start() }
        // Inspector default-disabled → arms derived-disabled.
        val input = FakeInput(pointer = Vec2(110f, 110f), leftClicked = true)
        input.mouseClickConsumed = false
        tree.hitTestPick(input)
        assertNull(tree.debug.inspector.selected, "no walk while disabled")
        assertEquals(false, input.mouseClickConsumed, "disabled inspector leaves the flag untouched")

        val recorder = RecordingRenderer()
        tree.render(recorder)
        assertEquals(0, recorder.events.count { it is RecordedEvent.Text })
        assertEquals(0, recorder.events.count { it is RecordedEvent.Line && it.color == DEBUG_SELECTION_COLOR })
    }

    private fun collectButtonNames(node: Node, out: MutableList<String> = mutableListOf()): List<String> {
        if (node is Button) out += node.name
        for (child in node.children) collectButtonNames(child, out)
        return out
    }
}
