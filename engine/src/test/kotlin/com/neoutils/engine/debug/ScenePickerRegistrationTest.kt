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

class ScenePickerRegistrationTest {

    @Test
    fun `picker is a single widget, gizmo is its world-space arm`() {
        val tree = SceneTree(Node()).also { it.start() }
        val layer = tree.root.findChild(DebugLayer.NODE_NAME) as DebugLayer

        assertNotNull(tree.debug.scenePicker)
        assertNotNull(tree.debug.selectionGizmo)
        // Picker is a real toggle widget on the screen container.
        assertTrue(tree.debug.scenePicker in tree.debug.widgets)
        assertSame(layer.screenContainer, tree.debug.scenePicker.parent)
        // Gizmo lives in the world container so it draws in the world pass, but
        // is NOT a standalone widget — controlled through the picker.
        assertSame(layer.worldContainer, tree.debug.selectionGizmo.parent)
        assertTrue(tree.debug.selectionGizmo !in tree.debug.widgets)
    }

    @Test
    fun `picker default disabled and gizmo derives its enabled`() {
        val tree = SceneTree(Node()).also { it.start() }
        assertEquals(false, tree.debug.scenePicker.enabled)
        assertEquals(false, tree.debug.selectionGizmo.enabled)
        // Enabling the picker enables the gizmo; disabling disables it.
        tree.debug.scenePicker.enabled = true
        assertEquals(true, tree.debug.selectionGizmo.enabled)
        tree.debug.scenePicker.enabled = false
        assertEquals(false, tree.debug.selectionGizmo.enabled)
        // The gizmo's own setter is a no-op — it cannot desync from the picker.
        tree.debug.selectionGizmo.enabled = true
        assertEquals(false, tree.debug.selectionGizmo.enabled)
    }

    @Test
    fun `only the picker surfaces as a HUD row`() {
        val tree = SceneTree(Node()).also { it.resize(800f, 600f); it.start() }
        tree.debug.hud.enabled = true
        tree.process(0f)      // buildPanel enqueues the panel + row buttons
        tree.applyPending()   // drain the deferred mutations so rows attach

        val rowNames = collectButtonNames(tree.root)
        assertTrue("DebugHudRow_Picker" in rowNames, rowNames.toString())
        assertFalse("DebugHudRow_Selection" in rowNames, rowNames.toString())
    }

    @Test
    fun `disabled - no pick walk and no draws`() {
        val target = ColorRect().apply {
            name = "Target"
            size = Vec2(40f, 40f)
            transform = Transform(position = Vec2(100f, 100f))
        }
        val tree = SceneTree(Node().apply { addChild(target) }).also { it.resize(800f, 600f); it.start() }
        // Picker default-disabled → gizmo derived-disabled.
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
