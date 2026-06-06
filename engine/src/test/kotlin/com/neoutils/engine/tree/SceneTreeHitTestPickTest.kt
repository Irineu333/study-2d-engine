package com.neoutils.engine.tree

import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.loop.GameLoop
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.CanvasLayer
import com.neoutils.engine.scene.ColorRect
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SceneTreeHitTestPickTest {

    private fun box(name: String, pos: Vec2, size: Vec2 = Vec2(40f, 40f), rot: Float = 0f) =
        ColorRect().apply {
            this.name = name
            this.size = size
            transform = Transform(position = pos, rotation = rot)
        }

    @Test
    fun `disabled picker is a strict no-op`() {
        val target = box("Target", Vec2(100f, 100f))
        val tree = SceneTree(Node().apply { addChild(target) })
        tree.start()
        tree.debug.inspector.enabled = false
        val input = FakeInput(pointer = Vec2(110f, 110f), leftClicked = true)
        input.mouseClickConsumed = false

        tree.hitTestPick(input)

        assertFalse(input.mouseClickConsumed, "flag must be untouched while disabled")
        assertNull(tree.debug.inspector.selected)
    }

    @Test
    fun `enabled click selects the node under the cursor and consumes the click`() {
        val target = box("Target", Vec2(100f, 100f))
        val tree = SceneTree(Node().apply { addChild(target) })
        tree.start()
        tree.debug.inspector.enabled = true
        val input = FakeInput(pointer = Vec2(120f, 120f), leftClicked = true)

        tree.hitTestPick(input)

        assertSame(target, tree.debug.inspector.selected)
        assertTrue(input.mouseClickConsumed)
    }

    @Test
    fun `rotated node hit precisely - inside selects, empty AABB corner does not`() {
        // Square (0,0)..(100,100) rotated 45° about origin at world (0,0).
        val target = box("Rot", Vec2(0f, 0f), size = Vec2(100f, 100f), rot = (PI / 4.0).toFloat())
        val tree = SceneTree(Node().apply { addChild(target) })
        tree.start()
        tree.debug.inspector.enabled = true

        // Center of the rotated square: local (50,50) → world (0, ~70.7). Inside.
        val inside = FakeInput(pointer = Vec2(0f, 70.7f), leftClicked = true)
        tree.hitTestPick(inside)
        assertSame(target, tree.debug.inspector.selected, "click inside the rotated box must select")

        // (-60, 5): inside the AABB (x∈[-70.7,70.7], y∈[0,141.4]) but outside the
        // rotated square (applyInverse → local x < 0). A click here finds no
        // candidate and clears the prior selection.
        val emptyCorner = FakeInput(pointer = Vec2(-60f, 5f), leftClicked = true)
        // Confirm the point really is inside the loose AABB.
        assertTrue(target.worldBounds()!!.contains(Vec2(-60f, 5f)))
        tree.hitTestPick(emptyCorner)
        assertNull(tree.debug.inspector.selected, "empty AABB corner must not select the rotated node")
    }

    @Test
    fun `node without localBounds is never selected`() {
        val plain = object : Node2D() { init { name = "PlainPivot" } } // localBounds() == null
        plain.transform = Transform(position = Vec2(100f, 100f))
        val tree = SceneTree(Node().apply { addChild(plain) })
        tree.start()
        tree.debug.inspector.enabled = true
        val input = FakeInput(pointer = Vec2(100f, 100f), leftClicked = true)

        tree.hitTestPick(input)

        assertNull(tree.debug.inspector.selected)
    }

    @Test
    fun `CanvasLayer subtree is skipped`() {
        val canvas = CanvasLayer()
        val uiBox = box("UiBox", Vec2(100f, 100f))
        canvas.addChild(uiBox)
        val tree = SceneTree(Node().apply { addChild(canvas) })
        tree.start()
        tree.debug.inspector.enabled = true
        val input = FakeInput(pointer = Vec2(120f, 120f), leftClicked = true)

        tree.hitTestPick(input)

        assertNull(tree.debug.inspector.selected, "nodes under a CanvasLayer are not pickable")
    }

    @Test
    fun `front-most wins on a fresh click and repeated clicks cycle and wrap`() {
        // Three overlapping boxes; DFS draw-order: back, mid, front (front last-painted).
        val back = box("Back", Vec2(100f, 100f))
        val mid = box("Mid", Vec2(100f, 100f))
        val front = box("Front", Vec2(100f, 100f))
        val tree = SceneTree(Node().apply { addChild(back); addChild(mid); addChild(front) })
        tree.start()
        tree.debug.inspector.enabled = true
        val picker = tree.debug.inspector

        // Fresh click selects front-most.
        tree.hitTestPick(FakeInput(pointer = Vec2(110f, 110f), leftClicked = true))
        assertSame(front, picker.selected)

        // Near-same clicks cycle behind, then wrap.
        tree.hitTestPick(FakeInput(pointer = Vec2(111f, 110f), leftClicked = true))
        assertSame(mid, picker.selected)
        tree.hitTestPick(FakeInput(pointer = Vec2(110f, 111f), leftClicked = true))
        assertSame(back, picker.selected)
        tree.hitTestPick(FakeInput(pointer = Vec2(110f, 110f), leftClicked = true))
        assertSame(front, picker.selected, "cycle wraps around to front-most")

        // A far-away fresh click resets to front-most.
        tree.hitTestPick(FakeInput(pointer = Vec2(130f, 130f), leftClicked = true))
        assertSame(front, picker.selected)
    }

    @Test
    fun `pick does not mutate the tree`() {
        val target = box("Target", Vec2(100f, 100f))
        val root = Node().apply { addChild(target) }
        val tree = SceneTree(root)
        tree.start()
        tree.debug.inspector.enabled = true
        val before = countNodes(root)

        tree.hitTestPick(FakeInput(pointer = Vec2(110f, 110f), leftClicked = true))
        tree.applyPending()

        assertSame(target, tree.debug.inspector.selected)
        kotlin.test.assertEquals(before, countNodes(root), "node count must be unchanged")
        assertSame(root, target.parent)
    }

    @Test
    fun `pick runs after hitTestUI and before process - gameplay does not see the consumed click`() {
        val target = box("Target", Vec2(100f, 100f))
        var sawClick = false
        val gameplay = object : Node() {
            override fun onProcess(dt: Float) {
                if (tree?.input?.wasMouseClicked(MouseButton.Left) == true) sawClick = true
            }
        }
        val tree = SceneTree(Node().apply { addChild(target); addChild(gameplay) })
        tree.start()
        tree.debug.inspector.enabled = true
        val input = FakeInput(pointer = Vec2(110f, 110f), leftClicked = true)
        val loop = GameLoop(tree, NoDrawRenderer, input)

        loop.tick(16_000_000L)

        assertSame(target, tree.debug.inspector.selected)
        assertFalse(sawClick, "gameplay must not see a click claimed by the picker")
    }

    private fun countNodes(node: Node): Int {
        var total = 1
        for (child in node.children) total += countNodes(child)
        return total
    }
}

private object NoDrawRenderer : Renderer {
    override fun clear(color: Color) {}
    override fun drawRect(rect: Rect, color: Color, filled: Boolean) {}
    override fun drawCircle(center: Vec2, radius: Float, color: Color, filled: Boolean, thickness: Float) {}
    override fun drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color) {}
    override fun drawText(text: String, position: Vec2, size: Float, color: Color) {}
    override fun measureText(text: String, size: Float): Vec2 = Vec2.ZERO
    override fun drawPolygon(points: List<Vec2>, color: Color) {}
    override fun drawImage(texture: com.neoutils.engine.render.Texture, src: com.neoutils.engine.math.Rect, dst: com.neoutils.engine.math.Rect, flipH: Boolean) {}
    override fun pushTransform(translation: Vec2, rotation: Float, scale: Vec2) {}
    override fun popTransform() {}
    override fun pushClip(rect: Rect) {}
    override fun popClip() {}
}
