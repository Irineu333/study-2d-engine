package com.neoutils.engine.lwjgl

import com.neoutils.engine.input.Input
import com.neoutils.engine.input.Key
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Vec2
import org.lwjgl.glfw.GLFW

/**
 * `Input` implementation ingesting GLFW key/mouse/cursor callbacks. Callbacks
 * are invoked synchronously from `glfwPollEvents()` on the same thread that
 * drives the render loop, so plain `HashSet`s are race-free. `@Volatile` on
 * `pointer` is defensive in case GLFW ever dispatches cursor events from
 * another thread on some platform; the cost is negligible.
 *
 * `beginTick()` is called by `LwjglHost` at the start of each tick to clear
 * the `pressedThisTick`/`clickedThisTick` sets so a press is reported by
 * `wasKeyPressed`/`wasMouseClicked` for exactly one tick following the event.
 */
class LwjglInput : Input {

    private val keysDown: MutableSet<Key> = HashSet()
    private val keysPressedThisTick: MutableSet<Key> = HashSet()
    private val mouseDown: MutableSet<MouseButton> = HashSet()
    private val mouseClickedThisTick: MutableSet<MouseButton> = HashSet()

    @Volatile private var pointer: Vec2 = Vec2.ZERO

    @Volatile override var mouseClickConsumed: Boolean = false

    @Volatile override var mouseDragConsumed: Boolean = false

    @Volatile override var scrollConsumed: Boolean = false

    override var scrollDelta: Vec2 = Vec2.ZERO
        private set

    // Accumulated on the loop thread by the GLFW scroll callback (fired from
    // glfwPollEvents), drained into `scrollDelta` at `beginTick()`.
    private var pendingWheelY: Float = 0f

    override val pointerPosition: Vec2 get() = pointer

    override fun isKeyDown(key: Key): Boolean = key in keysDown

    override fun wasKeyPressed(key: Key): Boolean = key in keysPressedThisTick

    override fun isMouseDown(button: MouseButton): Boolean = button in mouseDown

    override fun wasMouseClickedRaw(button: MouseButton): Boolean = button in mouseClickedThisTick

    fun beginTick() {
        keysPressedThisTick.clear()
        mouseClickedThisTick.clear()

        scrollConsumed = false
        // GLFW reports wheel-up as positive yoffset; the SPI's positive y means
        // scroll-down, so the sign is inverted.
        scrollDelta = if (pendingWheelY == 0f) Vec2.ZERO else Vec2(0f, -pendingWheelY)
        pendingWheelY = 0f
    }

    fun onGlfwScroll(xoffset: Float, yoffset: Float) {
        pendingWheelY += yoffset
    }

    fun onGlfwKey(glfwKey: Int, action: Int) {
        val key = glfwKeyToEngineKey(glfwKey) ?: return
        when (action) {
            GLFW.GLFW_PRESS -> if (keysDown.add(key)) keysPressedThisTick += key
            GLFW.GLFW_RELEASE -> keysDown.remove(key)
            // GLFW_REPEAT ignored: the SPI has no repeat semantics.
        }
    }

    fun onGlfwMouseButton(glfwButton: Int, action: Int) {
        val button = glfwMouseButtonToEngineButton(glfwButton) ?: return
        when (action) {
            GLFW.GLFW_PRESS -> if (mouseDown.add(button)) mouseClickedThisTick += button
            GLFW.GLFW_RELEASE -> mouseDown.remove(button)
        }
    }

    fun onGlfwCursorPos(x: Float, y: Float) {
        pointer = Vec2(x, y)
    }
}

internal fun glfwKeyToEngineKey(glfwKey: Int): Key? = when (glfwKey) {
    GLFW.GLFW_KEY_A -> Key.A; GLFW.GLFW_KEY_B -> Key.B; GLFW.GLFW_KEY_C -> Key.C
    GLFW.GLFW_KEY_D -> Key.D; GLFW.GLFW_KEY_E -> Key.E; GLFW.GLFW_KEY_F -> Key.F
    GLFW.GLFW_KEY_G -> Key.G; GLFW.GLFW_KEY_H -> Key.H; GLFW.GLFW_KEY_I -> Key.I
    GLFW.GLFW_KEY_J -> Key.J; GLFW.GLFW_KEY_K -> Key.K; GLFW.GLFW_KEY_L -> Key.L
    GLFW.GLFW_KEY_M -> Key.M; GLFW.GLFW_KEY_N -> Key.N; GLFW.GLFW_KEY_O -> Key.O
    GLFW.GLFW_KEY_P -> Key.P; GLFW.GLFW_KEY_Q -> Key.Q; GLFW.GLFW_KEY_R -> Key.R
    GLFW.GLFW_KEY_S -> Key.S; GLFW.GLFW_KEY_T -> Key.T; GLFW.GLFW_KEY_U -> Key.U
    GLFW.GLFW_KEY_V -> Key.V; GLFW.GLFW_KEY_W -> Key.W; GLFW.GLFW_KEY_X -> Key.X
    GLFW.GLFW_KEY_Y -> Key.Y; GLFW.GLFW_KEY_Z -> Key.Z
    GLFW.GLFW_KEY_0 -> Key.DIGIT_0; GLFW.GLFW_KEY_1 -> Key.DIGIT_1
    GLFW.GLFW_KEY_2 -> Key.DIGIT_2; GLFW.GLFW_KEY_3 -> Key.DIGIT_3
    GLFW.GLFW_KEY_4 -> Key.DIGIT_4; GLFW.GLFW_KEY_5 -> Key.DIGIT_5
    GLFW.GLFW_KEY_6 -> Key.DIGIT_6; GLFW.GLFW_KEY_7 -> Key.DIGIT_7
    GLFW.GLFW_KEY_8 -> Key.DIGIT_8; GLFW.GLFW_KEY_9 -> Key.DIGIT_9
    GLFW.GLFW_KEY_UP -> Key.ARROW_UP
    GLFW.GLFW_KEY_DOWN -> Key.ARROW_DOWN
    GLFW.GLFW_KEY_LEFT -> Key.ARROW_LEFT
    GLFW.GLFW_KEY_RIGHT -> Key.ARROW_RIGHT
    GLFW.GLFW_KEY_SPACE -> Key.SPACE
    GLFW.GLFW_KEY_ESCAPE -> Key.ESCAPE
    GLFW.GLFW_KEY_ENTER -> Key.ENTER
    GLFW.GLFW_KEY_TAB -> Key.TAB
    GLFW.GLFW_KEY_BACKSPACE -> Key.BACKSPACE
    GLFW.GLFW_KEY_LEFT_SHIFT -> Key.SHIFT_LEFT
    GLFW.GLFW_KEY_RIGHT_SHIFT -> Key.SHIFT_RIGHT
    GLFW.GLFW_KEY_LEFT_CONTROL -> Key.CTRL_LEFT
    GLFW.GLFW_KEY_RIGHT_CONTROL -> Key.CTRL_RIGHT
    GLFW.GLFW_KEY_LEFT_ALT -> Key.ALT_LEFT
    GLFW.GLFW_KEY_RIGHT_ALT -> Key.ALT_RIGHT
    GLFW.GLFW_KEY_F1 -> Key.F1
    GLFW.GLFW_KEY_F2 -> Key.F2
    GLFW.GLFW_KEY_F3 -> Key.F3
    else -> null
}

internal fun glfwMouseButtonToEngineButton(glfwButton: Int): MouseButton? = when (glfwButton) {
    GLFW.GLFW_MOUSE_BUTTON_LEFT -> MouseButton.Left
    GLFW.GLFW_MOUSE_BUTTON_RIGHT -> MouseButton.Right
    GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> MouseButton.Middle
    else -> null
}
