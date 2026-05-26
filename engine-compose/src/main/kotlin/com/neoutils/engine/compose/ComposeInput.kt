package com.neoutils.engine.compose

import androidx.compose.ui.input.key.Key as UiKey
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.neoutils.engine.input.Input
import com.neoutils.engine.input.Key
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Vec2

class ComposeInput : Input {

    private val downKeys: MutableSet<Key> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val pressedThisTick: MutableSet<Key> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val pendingPresses: MutableSet<Key> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    private val downButtons: MutableSet<MouseButton> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val pressedButtonsThisTick: MutableSet<MouseButton> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val pendingButtonPresses: MutableSet<MouseButton> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    override var pointerPosition: Vec2 = Vec2.ZERO
        internal set

    override fun isKeyDown(key: Key): Boolean = key in downKeys

    override fun wasKeyPressed(key: Key): Boolean = key in pressedThisTick

    override fun isMouseDown(button: MouseButton): Boolean = button in downButtons

    override fun wasMouseClicked(button: MouseButton): Boolean = button in pressedButtonsThisTick

    fun onKeyEvent(event: KeyEvent): Boolean {
        val mapped = event.key.toEngineKey() ?: return false
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (downKeys.add(mapped)) pendingPresses += mapped
            }
            KeyEventType.KeyUp -> downKeys.remove(mapped)
            else -> return false
        }
        return true
    }

    fun onPointerMove(x: Float, y: Float) {
        pointerPosition = Vec2(x, y)
    }

    fun onPointerButton(button: MouseButton, pressed: Boolean) {
        if (pressed) {
            if (downButtons.add(button)) pendingButtonPresses += button
        } else {
            downButtons.remove(button)
        }
    }

    /** Called by the runtime at the start of each tick. */
    fun beginTick() {
        pressedThisTick.clear()
        pressedThisTick.addAll(pendingPresses)
        pendingPresses.clear()

        pressedButtonsThisTick.clear()
        pressedButtonsThisTick.addAll(pendingButtonPresses)
        pendingButtonPresses.clear()
    }
}

private fun UiKey.toEngineKey(): Key? = when (this) {
    UiKey.A -> Key.A; UiKey.B -> Key.B; UiKey.C -> Key.C; UiKey.D -> Key.D
    UiKey.E -> Key.E; UiKey.F -> Key.F; UiKey.G -> Key.G; UiKey.H -> Key.H
    UiKey.I -> Key.I; UiKey.J -> Key.J; UiKey.K -> Key.K; UiKey.L -> Key.L
    UiKey.M -> Key.M; UiKey.N -> Key.N; UiKey.O -> Key.O; UiKey.P -> Key.P
    UiKey.Q -> Key.Q; UiKey.R -> Key.R; UiKey.S -> Key.S; UiKey.T -> Key.T
    UiKey.U -> Key.U; UiKey.V -> Key.V; UiKey.W -> Key.W; UiKey.X -> Key.X
    UiKey.Y -> Key.Y; UiKey.Z -> Key.Z
    UiKey.Zero -> Key.DIGIT_0; UiKey.One -> Key.DIGIT_1; UiKey.Two -> Key.DIGIT_2
    UiKey.Three -> Key.DIGIT_3; UiKey.Four -> Key.DIGIT_4; UiKey.Five -> Key.DIGIT_5
    UiKey.Six -> Key.DIGIT_6; UiKey.Seven -> Key.DIGIT_7; UiKey.Eight -> Key.DIGIT_8
    UiKey.Nine -> Key.DIGIT_9
    UiKey.DirectionUp -> Key.ARROW_UP
    UiKey.DirectionDown -> Key.ARROW_DOWN
    UiKey.DirectionLeft -> Key.ARROW_LEFT
    UiKey.DirectionRight -> Key.ARROW_RIGHT
    UiKey.Spacebar -> Key.SPACE
    UiKey.Escape -> Key.ESCAPE
    UiKey.Enter -> Key.ENTER
    UiKey.Tab -> Key.TAB
    UiKey.Backspace -> Key.BACKSPACE
    UiKey.ShiftLeft -> Key.SHIFT_LEFT
    UiKey.ShiftRight -> Key.SHIFT_RIGHT
    UiKey.CtrlLeft -> Key.CTRL_LEFT
    UiKey.CtrlRight -> Key.CTRL_RIGHT
    UiKey.AltLeft -> Key.ALT_LEFT
    UiKey.AltRight -> Key.ALT_RIGHT
    UiKey.F1 -> Key.F1
    UiKey.F2 -> Key.F2
    UiKey.F3 -> Key.F3
    else -> null
}
