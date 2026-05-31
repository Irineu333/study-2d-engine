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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScenePickerRegistrationTest {

    @Test
    fun `both built-ins present and parented under the correct containers`() {
        val tree = SceneTree(Node()).also { it.start() }
        val layer = tree.root.findChild(DebugLayer.NODE_NAME) as DebugLayer

        assertNotNull(tree.debug.scenePicker)
        assertNotNull(tree.debug.selectionGizmo)
        assertTrue(tree.debug.scenePicker in tree.debug.widgets)
        assertTrue(tree.debug.selectionGizmo in tree.debug.widgets)
        // Picker is screen-space; gizmo is world-space.
        assertSame(layer.screenContainer, tree.debug.scenePicker.parent)
        assertSame(layer.worldContainer, tree.debug.selectionGizmo.parent)
    }

    @Test
    fun `both default disabled`() {
        val tree = SceneTree(Node()).also { it.start() }
        assertEquals(false, tree.debug.scenePicker.enabled)
        assertEquals(false, tree.debug.selectionGizmo.enabled)
    }

    @Test
    fun `both surface as HUD toggle rows`() {
        val tree = SceneTree(Node()).also { it.resize(800f, 600f); it.start() }
        tree.debug.hud.enabled = true
        tree.process(0f)      // buildPanel enqueues the panel + row buttons
        tree.applyPending()   // drain the deferred mutations so rows attach

        val rowNames = collectButtonNames(tree.root)
        assertTrue("DebugHudRow_Picker" in rowNames, rowNames.toString())
        assertTrue("DebugHudRow_Selection" in rowNames, rowNames.toString())
    }

    @Test
    fun `disabled - no pick walk and no draws`() {
        val target = ColorRect().apply {
            name = "Target"
            size = Vec2(40f, 40f)
            transform = Transform(position = Vec2(100f, 100f))
        }
        val tree = SceneTree(Node().apply { addChild(target) }).also { it.resize(800f, 600f); it.start() }
        // Both default-disabled.
        val input = FakeInput(pointer = Vec2(110f, 110f), leftClicked = true)
        input.mouseClickConsumed = false
        tree.hitTestPick(input)
        assertNull(tree.debug.scenePicker.selected, "no walk while disabled")
        assertEquals(false, input.mouseClickConsumed, "disabled picker leaves the flag untouched")

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
