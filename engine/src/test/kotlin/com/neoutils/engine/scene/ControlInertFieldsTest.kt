package com.neoutils.engine.scene

import com.neoutils.engine.input.Input
import com.neoutils.engine.input.Key
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `focusMode`/`focusNeighbor*`/`sizeFlags*` are declared so `Control` is born
 * complete, but inert in this change: setting them changes nothing observable
 * about layout or hit-test (their behavior is reserved for `ui-focus`/`ui-layout`).
 */
class ControlInertFieldsTest {

    private val parentRect = Rect(Vec2.ZERO, Vec2(800f, 600f))

    private fun panel() = Panel().apply {
        anchorLeft = 0f; anchorTop = 0f; anchorRight = 1f; anchorBottom = 0f
        offsetLeft = 10f; offsetRight = -10f; offsetTop = 5f; offsetBottom = 40f
    }

    @Test
    fun `sizeFlags do not change the resolved rect`() {
        val base = panel()
        val flagged = panel().apply { sizeFlagsHorizontal = 2; sizeFlagsVertical = 2 }
        base.resolveLayout(parentRect)
        flagged.resolveLayout(parentRect)
        assertEquals(base.position, flagged.position)
        assertEquals(base.size, flagged.size)
    }

    @Test
    fun `focusMode does not change the resolved rect`() {
        val base = panel()
        val focusable = panel().apply { focusMode = FocusMode.ALL; focusNeighborLeft = "../Other" }
        base.resolveLayout(parentRect)
        focusable.resolveLayout(parentRect)
        assertEquals(base.position, focusable.position)
        assertEquals(base.size, focusable.size)
    }

    @Test
    fun `focusMode does not block a normal button click cycle`() {
        val root = Node()
        val layer = CanvasLayer()
        val button = Button().apply {
            position = Vec2(100f, 100f); size = Vec2(50f, 30f); focusMode = FocusMode.ALL
        }
        layer.addChild(button)
        root.addChild(layer)
        val tree = SceneTree(root)
        tree.resize(800f, 600f)
        tree.start()
        var count = 0
        button.pressed.connect { count++ }
        val input = ClickCycleInput(Vec2(110f, 110f))
        tree.input = input

        tree.hitTestUI(input)        // arms
        input.down = false; input.clicked = false
        tree.process(0.016f)          // release inside → emit
        assertEquals(1, count, "focusMode is inert; the click still emits")
    }

    private class ClickCycleInput(
        private val pointer: Vec2,
        var clicked: Boolean = true,
        var down: Boolean = true,
    ) : Input {
        override val pointerPosition: Vec2 get() = pointer
        override var mouseClickConsumed: Boolean = false
        override fun isKeyDown(key: Key): Boolean = false
        override fun wasKeyPressed(key: Key): Boolean = false
        override fun isMouseDown(button: MouseButton): Boolean = button == MouseButton.Left && down
        override fun wasMouseClickedRaw(button: MouseButton): Boolean = button == MouseButton.Left && clicked
    }
}
