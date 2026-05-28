package com.neoutils.engine.debug

import com.neoutils.engine.input.Input
import com.neoutils.engine.input.Key
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class KeyInput(var pressedKey: Key? = null) : Input {
    override val pointerPosition: Vec2 get() = Vec2.ZERO
    override var mouseClickConsumed: Boolean = false
    override fun isKeyDown(key: Key): Boolean = false
    override fun wasKeyPressed(key: Key): Boolean = pressedKey == key
    override fun isMouseDown(button: MouseButton): Boolean = false
    override fun wasMouseClickedRaw(button: MouseButton): Boolean = false
}

class DebugToggleNodeTest {

    @Test
    fun `pressing F1 toggles tree debug hud enabled`() {
        val tree = SceneTree(Node()).also { it.start() }
        val input = KeyInput()
        tree.input = input

        assertEquals(false, tree.debug.hud.enabled)
        input.pressedKey = Key.F1
        tree.process(0.016f)
        tree.applyPending()
        assertEquals(true, tree.debug.hud.enabled)

        input.pressedKey = Key.F1
        tree.process(0.016f)
        tree.applyPending()
        assertEquals(false, tree.debug.hud.enabled)
    }

    @Test
    fun `pressing the wrong key does nothing`() {
        val tree = SceneTree(Node()).also { it.start() }
        val input = KeyInput(pressedKey = Key.F2)
        tree.input = input
        tree.process(0.016f)
        assertEquals(false, tree.debug.hud.enabled)
    }

    @Test
    fun `custom debugHudKey is honored`() {
        val tree = SceneTree(Node()).also { it.start() }
        tree.debugHudKey = Key.F3
        val input = KeyInput(pressedKey = Key.F3)
        tree.input = input
        tree.process(0.016f)
        assertTrue(tree.debug.hud.enabled)
    }
}
