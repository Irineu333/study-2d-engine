package com.neoutils.engine.skiko

import com.neoutils.engine.input.Input
import com.neoutils.engine.input.Key
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Vec2
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * `Input` implementation aggregating AWT key/mouse callbacks into snapshot
 * state queryable per tick. Mirrors `ComposeInput`'s pending/pressed-this-tick
 * pattern so a press is reported by `wasKeyPressed`/`wasMouseClicked` for
 * exactly one tick following the event.
 */
class SkikoInput : Input {

    private val downKeys: MutableSet<Key> = ConcurrentHashMap.newKeySet()
    private val pressedThisTick: MutableSet<Key> = ConcurrentHashMap.newKeySet()
    private val pendingPresses: MutableSet<Key> = ConcurrentHashMap.newKeySet()

    private val downButtons: MutableSet<MouseButton> = ConcurrentHashMap.newKeySet()
    private val pressedButtonsThisTick: MutableSet<MouseButton> = ConcurrentHashMap.newKeySet()
    private val pendingButtonPresses: MutableSet<MouseButton> = ConcurrentHashMap.newKeySet()

    @Volatile override var pointerPosition: Vec2 = Vec2.ZERO

    override fun isKeyDown(key: Key): Boolean = key in downKeys

    override fun wasKeyPressed(key: Key): Boolean = key in pressedThisTick

    override fun isMouseDown(button: MouseButton): Boolean = button in downButtons

    override fun wasMouseClicked(button: MouseButton): Boolean = button in pressedButtonsThisTick

    fun onAwtKey(event: KeyEvent, pressed: Boolean) {
        val mapped = event.keyCode.awtVkToEngineKey() ?: return
        if (pressed) {
            if (downKeys.add(mapped)) pendingPresses += mapped
        } else {
            downKeys.remove(mapped)
        }
    }

    fun onAwtMouseMoved(event: MouseEvent, contentScale: Float) {
        // AWT MouseEvent.x/y are in component-local *logical* pixels, but the
        // engine sizes `tree.size` from the Skiko render buffer in *physical*
        // pixels (layer.size * contentScale). Without this multiplication,
        // hit-tests via `Camera2D.screenToWorld` go off by `contentScale` on
        // HiDPI monitors and the displacement appears when dragging the window
        // between monitors with different scales.
        pointerPosition = Vec2(event.x * contentScale, event.y * contentScale)
    }

    fun onAwtMouseButton(event: MouseEvent, pressed: Boolean) {
        val mapped = event.button.awtButtonToEngine() ?: return
        if (pressed) {
            if (downButtons.add(mapped)) pendingButtonPresses += mapped
        } else {
            downButtons.remove(mapped)
        }
    }

    /** Called by the host at the start of each tick. */
    fun beginTick() {
        pressedThisTick.clear()
        pressedThisTick.addAll(pendingPresses)
        pendingPresses.clear()

        pressedButtonsThisTick.clear()
        pressedButtonsThisTick.addAll(pendingButtonPresses)
        pendingButtonPresses.clear()
    }
}

private fun Int.awtButtonToEngine(): MouseButton? = when (this) {
    MouseEvent.BUTTON1 -> MouseButton.Left
    MouseEvent.BUTTON2 -> MouseButton.Middle
    MouseEvent.BUTTON3 -> MouseButton.Right
    else -> null
}

internal fun Int.awtVkToEngineKey(): Key? = when (this) {
    KeyEvent.VK_A -> Key.A; KeyEvent.VK_B -> Key.B; KeyEvent.VK_C -> Key.C
    KeyEvent.VK_D -> Key.D; KeyEvent.VK_E -> Key.E; KeyEvent.VK_F -> Key.F
    KeyEvent.VK_G -> Key.G; KeyEvent.VK_H -> Key.H; KeyEvent.VK_I -> Key.I
    KeyEvent.VK_J -> Key.J; KeyEvent.VK_K -> Key.K; KeyEvent.VK_L -> Key.L
    KeyEvent.VK_M -> Key.M; KeyEvent.VK_N -> Key.N; KeyEvent.VK_O -> Key.O
    KeyEvent.VK_P -> Key.P; KeyEvent.VK_Q -> Key.Q; KeyEvent.VK_R -> Key.R
    KeyEvent.VK_S -> Key.S; KeyEvent.VK_T -> Key.T; KeyEvent.VK_U -> Key.U
    KeyEvent.VK_V -> Key.V; KeyEvent.VK_W -> Key.W; KeyEvent.VK_X -> Key.X
    KeyEvent.VK_Y -> Key.Y; KeyEvent.VK_Z -> Key.Z
    KeyEvent.VK_0 -> Key.DIGIT_0; KeyEvent.VK_1 -> Key.DIGIT_1
    KeyEvent.VK_2 -> Key.DIGIT_2; KeyEvent.VK_3 -> Key.DIGIT_3
    KeyEvent.VK_4 -> Key.DIGIT_4; KeyEvent.VK_5 -> Key.DIGIT_5
    KeyEvent.VK_6 -> Key.DIGIT_6; KeyEvent.VK_7 -> Key.DIGIT_7
    KeyEvent.VK_8 -> Key.DIGIT_8; KeyEvent.VK_9 -> Key.DIGIT_9
    KeyEvent.VK_UP -> Key.ARROW_UP
    KeyEvent.VK_DOWN -> Key.ARROW_DOWN
    KeyEvent.VK_LEFT -> Key.ARROW_LEFT
    KeyEvent.VK_RIGHT -> Key.ARROW_RIGHT
    KeyEvent.VK_SPACE -> Key.SPACE
    KeyEvent.VK_ESCAPE -> Key.ESCAPE
    KeyEvent.VK_ENTER -> Key.ENTER
    KeyEvent.VK_TAB -> Key.TAB
    KeyEvent.VK_BACK_SPACE -> Key.BACKSPACE
    KeyEvent.VK_SHIFT -> Key.SHIFT_LEFT
    KeyEvent.VK_CONTROL -> Key.CTRL_LEFT
    KeyEvent.VK_ALT -> Key.ALT_LEFT
    KeyEvent.VK_F1 -> Key.F1
    KeyEvent.VK_F2 -> Key.F2
    KeyEvent.VK_F3 -> Key.F3
    else -> null
}
