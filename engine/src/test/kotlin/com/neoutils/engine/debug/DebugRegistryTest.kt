package com.neoutils.engine.debug

import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class TestWorldWidget : WorldDebugWidget() {
    override val title: String = "TestWorld"
    override fun drawDebug(renderer: Renderer) {}
}

private class TestScreenWidget : ScreenDebugWidget() {
    override val title: String = "TestScreen"
    override fun drawDebug(renderer: Renderer) {}
}

class DebugRegistryTest {

    @Test
    fun `register routes WorldDebugWidget to world container`() {
        val tree = SceneTree(Node())
        tree.start()
        val widget = TestWorldWidget()
        tree.debug.register(widget)
        val layer = tree.root.findChild(DebugLayer.NODE_NAME) as DebugLayer
        assertSame(layer.worldContainer, widget.parent)
        assertTrue(widget in tree.debug.widgets)
    }

    @Test
    fun `register routes ScreenDebugWidget to screen canvas`() {
        val tree = SceneTree(Node())
        tree.start()
        val widget = TestScreenWidget()
        tree.debug.register(widget)
        val layer = tree.root.findChild(DebugLayer.NODE_NAME) as DebugLayer
        assertSame(layer.screenContainer, widget.parent)
        assertTrue(widget in tree.debug.widgets)
    }

    @Test
    fun `find returns instance by type`() {
        val tree = SceneTree(Node())
        tree.start()
        val custom = TestWorldWidget()
        tree.debug.register(custom)
        assertSame(custom, tree.debug.find<TestWorldWidget>())
        assertSame(tree.debug.fps, tree.debug.find<FpsWidget>())
    }

    @Test
    fun `find returns null when missing`() {
        val tree = SceneTree(Node())
        tree.start()
        assertNull(tree.debug.find<TestWorldWidget>())
    }

    @Test
    fun `unregister removes widget from list and detaches it from tree`() {
        val tree = SceneTree(Node())
        tree.start()
        val widget = TestScreenWidget()
        tree.debug.register(widget)
        tree.applyPending()
        assertTrue(widget in tree.debug.widgets)

        tree.debug.unregister(widget)
        tree.applyPending()
        assertTrue(widget !in tree.debug.widgets)
        assertEquals(null, widget.parent)
    }

    @Test
    fun `widgets list includes builtins plus custom registrations in order`() {
        val tree = SceneTree(Node())
        tree.start()
        val custom = TestScreenWidget()
        tree.debug.register(custom)
        val widgets = tree.debug.widgets
        assertEquals(12, widgets.size)
        assertSame(tree.debug.fps, widgets[0])
        assertSame(custom, widgets[11])
    }

    @Test
    fun `two SceneTrees do not share registry state`() {
        val treeA = SceneTree(Node()).also { it.start() }
        val treeB = SceneTree(Node()).also { it.start() }
        treeA.debug.momentum.enabled = true
        assertTrue(treeA.debug.momentum.enabled)
        assertTrue(!treeB.debug.momentum.enabled)
        // Distinct instances.
        assertTrue(treeA.debug.momentum !== treeB.debug.momentum)
    }
}
