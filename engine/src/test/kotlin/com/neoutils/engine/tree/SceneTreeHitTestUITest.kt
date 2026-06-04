package com.neoutils.engine.tree

import com.neoutils.engine.input.Input
import com.neoutils.engine.input.Key
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.CanvasLayer
import com.neoutils.engine.scene.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SceneTreeHitTestUITest {

    @Test
    fun `click inside enabled button sets mouseClickConsumed`() {
        val (tree, _, input) = treeWithButton(
            buttonPos = Vec2(100f, 100f),
            buttonSize = Vec2(50f, 30f),
            pointer = Vec2(120f, 110f),
            leftClicked = true,
        )
        tree.hitTestUI(input)
        assertTrue(input.mouseClickConsumed)
        assertTrue(input.wasMouseClickedRaw(MouseButton.Left))
        assertFalse(input.wasMouseClicked(MouseButton.Left), "wasMouseClicked must mirror consumed=false")
    }

    @Test
    fun `click outside any button does NOT consume`() {
        val (tree, _, input) = treeWithButton(
            buttonPos = Vec2(100f, 100f),
            buttonSize = Vec2(50f, 30f),
            pointer = Vec2(500f, 500f),
            leftClicked = true,
        )
        tree.hitTestUI(input)
        assertFalse(input.mouseClickConsumed)
        assertTrue(input.wasMouseClicked(MouseButton.Left))
    }

    @Test
    fun `disabled button does not absorb the click`() {
        val (tree, button, input) = treeWithButton(
            buttonPos = Vec2(100f, 100f),
            buttonSize = Vec2(50f, 30f),
            pointer = Vec2(110f, 110f),
            leftClicked = true,
        )
        button.disabled = true
        tree.hitTestUI(input)
        assertFalse(input.mouseClickConsumed)
    }

    @Test
    fun `top-most CanvasLayer wins overlap`() {
        val root = Node()
        val low = CanvasLayer().apply { layer = 0 }
        val lowBtn = Button().apply {
            name = "low"
            transform = Transform(position = Vec2(100f, 100f))
            size = Vec2(50f, 50f)
        }
        low.addChild(lowBtn)
        val high = CanvasLayer().apply { layer = 10 }
        val highBtn = Button().apply {
            name = "high"
            transform = Transform(position = Vec2(100f, 100f))
            size = Vec2(50f, 50f)
        }
        high.addChild(highBtn)
        root.addChild(low); root.addChild(high)
        val tree = SceneTree(root); tree.start()
        val input = FakeInput(pointer = Vec2(110f, 110f), leftClicked = true)
        tree.input = input

        tree.hitTestUI(input)
        assertTrue(input.mouseClickConsumed)
        // After hitTestUI, the high (top-most) button must have armed itself.
        // We trigger _process with mouse released to make `pressed` emit and
        // assert that only the high button fired.
        input.leftDown = false
        var fired = "none"
        lowBtn.pressed.connect { fired = "low" }
        highBtn.pressed.connect { fired = "high" }
        tree.process(0.016f)
        assertEquals("high", fired)
    }

    @Test
    fun `mouseClickConsumed resets each tick`() {
        val (tree, _, input) = treeWithButton(
            buttonPos = Vec2(100f, 100f),
            buttonSize = Vec2(50f, 30f),
            pointer = Vec2(110f, 110f),
            leftClicked = true,
        )
        tree.hitTestUI(input)
        assertTrue(input.mouseClickConsumed)

        // Next tick: no click; hitTestUI must clear the flag at its start.
        input.leftClicked = false
        tree.hitTestUI(input)
        assertFalse(input.mouseClickConsumed)
    }

    @Test
    fun `Button pressed emits once per click cycle when released inside`() {
        val (tree, button, input) = treeWithButton(
            buttonPos = Vec2(100f, 100f),
            buttonSize = Vec2(50f, 30f),
            pointer = Vec2(110f, 110f),
            leftClicked = true,
            leftDown = true,
        )
        var count = 0
        button.pressed.connect { count++ }
        tree.hitTestUI(input)        // arms
        tree.process(0.016f)          // still held; no emit
        assertEquals(0, count)
        input.leftClicked = false
        input.leftDown = false
        tree.process(0.016f)          // released inside; emits
        assertEquals(1, count)
        // No re-emit on subsequent ticks without a new click.
        tree.process(0.016f)
        assertEquals(1, count)
    }

    @Test
    fun `drag-out cancels press`() {
        val (tree, button, input) = treeWithButton(
            buttonPos = Vec2(100f, 100f),
            buttonSize = Vec2(50f, 30f),
            pointer = Vec2(110f, 110f),
            leftClicked = true,
            leftDown = true,
        )
        var count = 0
        button.pressed.connect { count++ }
        tree.hitTestUI(input)         // arms
        // Mouse moves outside, then releases.
        input.pointer = Vec2(500f, 500f)
        input.leftClicked = false
        input.leftDown = false
        tree.process(0.016f)
        assertEquals(0, count, "release outside must cancel")
    }

    @Test
    fun `disabled flag suppresses emit`() {
        val (tree, button, input) = treeWithButton(
            buttonPos = Vec2(100f, 100f),
            buttonSize = Vec2(50f, 30f),
            pointer = Vec2(110f, 110f),
            leftClicked = true,
            leftDown = true,
        )
        button.disabled = true
        var count = 0
        button.pressed.connect { count++ }
        tree.hitTestUI(input)
        input.leftClicked = false
        input.leftDown = false
        tree.process(0.016f)
        assertEquals(0, count)
    }

    private fun treeWithButton(
        buttonPos: Vec2,
        buttonSize: Vec2,
        pointer: Vec2,
        leftClicked: Boolean,
        leftDown: Boolean = false,
    ): Triple<SceneTree, Button, FakeInput> {
        val root = Node()
        val canvas = CanvasLayer()
        val button = Button().apply {
            name = "B"
            transform = Transform(position = buttonPos)
            size = buttonSize
        }
        canvas.addChild(button)
        root.addChild(canvas)
        val tree = SceneTree(root); tree.start()
        val input = FakeInput(pointer = pointer, leftClicked = leftClicked, leftDown = leftDown)
        tree.input = input
        return Triple(tree, button, input)
    }
}

internal class FakeInput(
    var pointer: Vec2 = Vec2.ZERO,
    var leftClicked: Boolean = false,
    var leftDown: Boolean = false,
    override var scrollDelta: Vec2 = Vec2.ZERO,
) : Input {
    override val pointerPosition: Vec2 get() = pointer
    override var mouseClickConsumed: Boolean = false
    override var mouseDragConsumed: Boolean = false
    override var scrollConsumed: Boolean = false
    override fun isKeyDown(key: Key): Boolean = false
    override fun wasKeyPressed(key: Key): Boolean = false
    override fun isMouseDown(button: MouseButton): Boolean =
        button == MouseButton.Left && leftDown
    override fun wasMouseClickedRaw(button: MouseButton): Boolean =
        button == MouseButton.Left && leftClicked
}
