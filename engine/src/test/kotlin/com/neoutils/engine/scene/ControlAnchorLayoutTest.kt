package com.neoutils.engine.scene

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Anchor layout pass: the engine resolves each `Control`'s `position`/`size`
 * from anchors + offsets against its parent rect before render/hit-test. We
 * drive the pass via `render` (which calls it at the start of the frame) and
 * assert the resolved local `position`/`size`.
 */
class ControlAnchorLayoutTest {

    private fun layout(tree: SceneTree) = tree.render(RecordingRenderer())

    private fun treeWith(vararg controls: Control, width: Float, height: Float): SceneTree {
        val root = Node()
        val layer = CanvasLayer()
        for (c in controls) layer.addChild(c)
        root.addChild(layer)
        val tree = SceneTree(root)
        tree.resize(width, height)
        tree.start()
        return tree
    }

    @Test
    fun `top-left anchors keep a constant rect regardless of surface`() {
        val panel = Panel().apply {
            anchorLeft = 0f; anchorTop = 0f; anchorRight = 0f; anchorBottom = 0f
            offsetLeft = 10f; offsetTop = 20f; offsetRight = 110f; offsetBottom = 70f
        }
        val tree = treeWith(panel, width = 800f, height = 600f)
        layout(tree)
        assertEquals(Vec2(10f, 20f), panel.position)
        assertEquals(Vec2(100f, 50f), panel.size)

        tree.resize(400f, 300f)
        layout(tree)
        assertEquals(Vec2(10f, 20f), panel.position, "fixed rect must not move with surface")
        assertEquals(Vec2(100f, 50f), panel.size)
    }

    @Test
    fun `right anchor grows the rect with the parent`() {
        val panel = Panel().apply {
            anchorLeft = 0f; anchorRight = 1f; anchorTop = 0f; anchorBottom = 0f
            offsetLeft = 10f; offsetRight = -10f; offsetTop = 0f; offsetBottom = 30f
        }
        val tree = treeWith(panel, width = 800f, height = 600f)
        layout(tree)
        assertEquals(780f, panel.size.x)

        tree.resize(400f, 600f)
        layout(tree)
        assertEquals(380f, panel.size.x, "stretch must track the surface width")
    }

    @Test
    fun `centered anchors place the control on the parent center`() {
        val panel = Panel().apply {
            anchorLeft = 0.5f; anchorTop = 0.5f; anchorRight = 0.5f; anchorBottom = 0.5f
            offsetLeft = -50f; offsetRight = 50f; offsetTop = -20f; offsetBottom = 20f
        }
        val tree = treeWith(panel, width = 800f, height = 600f)
        layout(tree)
        assertEquals(Vec2(350f, 280f), panel.position)
        assertEquals(Vec2(100f, 40f), panel.size)
    }

    @Test
    fun `bottom-right anchors reflow on surface resize without script`() {
        val panel = Panel().apply {
            anchorLeft = 1f; anchorTop = 1f; anchorRight = 1f; anchorBottom = 1f
            offsetLeft = -110f; offsetTop = -60f; offsetRight = -10f; offsetBottom = -10f
        }
        val tree = treeWith(panel, width = 800f, height = 600f)
        layout(tree)
        assertEquals(Vec2(690f, 540f), panel.position)
        assertEquals(Vec2(100f, 50f), panel.size)

        tree.resize(1024f, 768f)
        layout(tree)
        assertEquals(Vec2(914f, 708f), panel.position, "must re-resolve against the new surface")
        assertEquals(Vec2(100f, 50f), panel.size)
    }

    @Test
    fun `nested Control resolves against its ancestor Control rect`() {
        val child = Panel().apply {
            anchorLeft = 0.5f; anchorTop = 0.5f; anchorRight = 0.5f; anchorBottom = 0.5f
            offsetLeft = -10f; offsetRight = 10f; offsetTop = -10f; offsetBottom = 10f
        }
        val parent = Panel().apply {
            anchorLeft = 0f; anchorTop = 0f; anchorRight = 0f; anchorBottom = 0f
            offsetLeft = 100f; offsetTop = 100f; offsetRight = 500f; offsetBottom = 400f
            addChild(child)
        }
        val tree = treeWith(parent, width = 800f, height = 600f)
        layout(tree)
        // Parent resolves to Rect(100,100, 400,300); child centers on (300,250).
        assertEquals(Vec2(400f, 300f), parent.size)
        assertEquals(Vec2(20f, 20f), child.size)
        // Child transform is local; world position is the absolute resolved origin.
        assertEquals(Vec2(290f, 240f), child.world().position)
    }
}
