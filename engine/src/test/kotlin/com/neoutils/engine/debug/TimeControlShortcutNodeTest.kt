package com.neoutils.engine.debug

import com.neoutils.engine.input.Input
import com.neoutils.engine.input.Key
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class ShortcutKeyInput(var pressedKey: Key? = null) : Input {
    override val pointerPosition: Vec2 get() = Vec2.ZERO
    override var mouseClickConsumed: Boolean = false
    override fun isKeyDown(key: Key): Boolean = false
    override fun wasKeyPressed(key: Key): Boolean = pressedKey == key
    override fun isMouseDown(button: MouseButton): Boolean = false
    override fun wasMouseClickedRaw(button: MouseButton): Boolean = false
}

class TimeControlShortcutNodeTest {

    private fun treeWith(input: ShortcutKeyInput): SceneTree =
        SceneTree(Node()).also {
            it.start()
            it.input = input
            it.debug.timeControls.enabled = true
        }

    @Test
    fun `pause key toggles paused`() {
        val input = ShortcutKeyInput()
        val tree = treeWith(input)
        input.pressedKey = Key.P
        tree.process(0f)
        assertTrue(tree.paused)
        tree.process(0f)
        assertFalse(tree.paused, "pressing again resumes")
    }

    @Test
    fun `step key requests a step`() {
        val input = ShortcutKeyInput(pressedKey = Key.O)
        val tree = treeWith(input)
        tree.process(0f)
        assertTrue(tree.hasPendingStep)
    }

    @Test
    fun `speed keys step timeScale up and down, clamped`() {
        val input = ShortcutKeyInput()
        val tree = treeWith(input)

        input.pressedKey = Key.I
        tree.process(0f)
        assertEquals(2f, tree.timeScale, "I steps speed up")

        input.pressedKey = Key.U
        tree.process(0f)
        assertEquals(1f, tree.timeScale, "U steps speed down")
    }

    @Test
    fun `shortcuts are inert while the Time widget is disabled`() {
        val input = ShortcutKeyInput(pressedKey = Key.P)
        val tree = SceneTree(Node()).also {
            it.start()
            it.input = input
        }
        // timeControls left disabled (the production default).
        tree.process(0f)
        assertFalse(tree.paused, "pause key must not fire when the Time widget is off")
    }
}
