package com.neoutils.engine.debug

import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DebugLayerTest {

    @Test
    fun `every started SceneTree carries an auto-inserted DebugLayer`() {
        val tree = SceneTree(Node())
        tree.start()
        val layer = tree.root.findChild(DebugLayer.NODE_NAME)
        assertNotNull(layer, "expected __debug child after start()")
        assertIs<DebugLayer>(layer)
    }

    @Test
    fun `DebugLayer has exactly one world container and one screen canvas child`() {
        val tree = SceneTree(Node())
        tree.start()
        val layer = tree.root.findChild(DebugLayer.NODE_NAME) as DebugLayer
        val worldChildren = layer.children.filterIsInstance<WorldDebugContainer>()
        val screenChildren = layer.children.filterIsInstance<ScreenDebugCanvas>()
        assertEquals(1, worldChildren.size)
        assertEquals(1, screenChildren.size)
        assertSame(layer.worldContainer, worldChildren.single())
        assertSame(layer.screenContainer, screenChildren.single())
    }

    @Test
    fun `ScreenDebugCanvas hosts DebugHud, ProfilerWidget, DebugToggleNode, ColliderModePanel`() {
        val tree = SceneTree(Node())
        tree.start()
        val canvas = (tree.root.findChild(DebugLayer.NODE_NAME) as DebugLayer).screenContainer
        assertSame(tree.debug.hud, canvas.children.filterIsInstance<DebugHud>().single())
        assertSame(tree.debug.profiler, canvas.children.filterIsInstance<ProfilerWidget>().single())
        // The collider mode panel is the colliders tool's screen-space arm:
        // hosted here but kept out of the HUD widget list.
        assertSame(tree.debug.colliderModePanel, canvas.children.filterIsInstance<ColliderModePanel>().single())
        assertTrue(tree.debug.colliderModePanel !in tree.debug.widgets)
        assertTrue(canvas.children.any { it::class.simpleName == "DebugToggleNode" })
    }

    @Test
    fun `WorldDebugContainer hosts ColliderWidget`() {
        val tree = SceneTree(Node())
        tree.start()
        val layer = tree.root.findChild(DebugLayer.NODE_NAME) as DebugLayer
        val container = layer.worldContainer
        assertSame(tree.debug.colliders, container.children.filterIsInstance<ColliderWidget>().single())
    }

    @Test
    fun `auto-insert is idempotent across re-start`() {
        val tree = SceneTree(Node())
        tree.start()
        val first = tree.root.findChild(DebugLayer.NODE_NAME) as DebugLayer
        tree.stop()
        tree.start()
        val matches = tree.root.children.filter { it.name == DebugLayer.NODE_NAME }
        assertEquals(1, matches.size)
        assertSame(first, matches.single())
        // No widget duplication.
        assertEquals(1, first.worldContainer.children.filterIsInstance<ColliderWidget>().size)
        assertEquals(1, first.screenContainer.children.filterIsInstance<ProfilerWidget>().size)
    }

    @Test
    fun `built-in widgets are listed in the registry in registration order`() {
        val tree = SceneTree(Node())
        tree.start()
        val widgets = tree.debug.widgets
        assertEquals(9, widgets.size)
        assertSame(tree.debug.colliders, widgets[0])
        assertSame(tree.debug.log, widgets[1])
        assertSame(tree.debug.hud, widgets[2])
        assertEquals("Debug Draw", widgets[3].title)
        assertSame(tree.debug.velocityGizmo, widgets[4])
        assertSame(tree.debug.contactGizmo, widgets[5])
        assertSame(tree.debug.timeControls, widgets[6])
        assertSame(tree.debug.profiler, widgets[7])
        assertSame(tree.debug.scenePicker, widgets[8])
        // selectionGizmo is the picker's world-space arm — NOT a standalone
        // widget, so it is absent from `widgets`.
        assertTrue(tree.debug.selectionGizmo !in widgets)
    }
}
