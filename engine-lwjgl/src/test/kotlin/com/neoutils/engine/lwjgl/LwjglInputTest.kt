package com.neoutils.engine.lwjgl

import com.neoutils.engine.input.Key
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Vec2
import org.lwjgl.glfw.GLFW
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LwjglInputTest {

    @Test
    fun `press registers in down and wasPressed during the tick`() {
        val input = LwjglInput()
        input.beginTick()

        input.onGlfwKey(GLFW.GLFW_KEY_W, GLFW.GLFW_PRESS)

        assertTrue(input.isKeyDown(Key.W))
        assertTrue(input.wasKeyPressed(Key.W))
    }

    @Test
    fun `release clears down but keeps wasPressed of the same tick`() {
        val input = LwjglInput()
        input.beginTick()

        input.onGlfwKey(GLFW.GLFW_KEY_W, GLFW.GLFW_PRESS)
        input.onGlfwKey(GLFW.GLFW_KEY_W, GLFW.GLFW_RELEASE)

        assertFalse(input.isKeyDown(Key.W))
        assertTrue(input.wasKeyPressed(Key.W))
    }

    @Test
    fun `beginTick clears wasPressed`() {
        val input = LwjglInput()
        input.beginTick()
        input.onGlfwKey(GLFW.GLFW_KEY_W, GLFW.GLFW_PRESS)
        assertTrue(input.wasKeyPressed(Key.W))

        input.beginTick()

        assertFalse(input.wasKeyPressed(Key.W))
    }

    @Test
    fun `repeat does not re-register the press`() {
        val input = LwjglInput()
        input.beginTick()
        input.onGlfwKey(GLFW.GLFW_KEY_W, GLFW.GLFW_PRESS)
        input.beginTick()
        assertFalse(input.wasKeyPressed(Key.W))

        input.onGlfwKey(GLFW.GLFW_KEY_W, GLFW.GLFW_REPEAT)

        assertTrue(input.isKeyDown(Key.W))
        assertFalse(input.wasKeyPressed(Key.W))
    }

    @Test
    fun `mouse button press and release follow the same pattern as keys`() {
        val input = LwjglInput()
        input.beginTick()

        input.onGlfwMouseButton(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS)
        assertTrue(input.isMouseDown(MouseButton.Left))
        assertTrue(input.wasMouseClicked(MouseButton.Left))

        input.onGlfwMouseButton(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE)
        assertFalse(input.isMouseDown(MouseButton.Left))
        assertTrue(input.wasMouseClicked(MouseButton.Left))

        input.beginTick()
        assertFalse(input.wasMouseClicked(MouseButton.Left))
    }

    @Test
    fun `pointerPosition reflects last cursor`() {
        val input = LwjglInput()

        input.onGlfwCursorPos(150f, 220f)

        assertEquals(Vec2(150f, 220f), input.pointerPosition)
    }

    @Test
    fun `unmapped key returns silently and is not tracked`() {
        val input = LwjglInput()
        input.beginTick()

        input.onGlfwKey(GLFW.GLFW_KEY_HOME, GLFW.GLFW_PRESS)

        // No engine Key for HOME → callback must be a no-op; nothing is tracked.
        assertNull(glfwKeyToEngineKey(GLFW.GLFW_KEY_HOME))
        Key.values().forEach { k ->
            assertFalse(input.isKeyDown(k), "no key should be marked down after unmapped event: $k")
            assertFalse(input.wasKeyPressed(k), "no key should be marked pressed after unmapped event: $k")
        }
    }

    @Test
    fun `wheel-up produces a negative y delta for one tick`() {
        val input = LwjglInput()
        input.beginTick()
        // GLFW reports wheel-up as positive yoffset; the SPI inverts it so
        // positive y means scroll-down — wheel-up is therefore negative.
        input.onGlfwScroll(0f, 1f)
        input.beginTick()

        assertEquals(-1f, input.scrollDelta.y)
        assertEquals(0f, input.scrollDelta.x)

        input.beginTick()
        assertEquals(Vec2.ZERO, input.scrollDelta, "delta clears after one tick")
    }

    @Test
    fun `no wheel motion reads as zero`() {
        val input = LwjglInput()
        input.beginTick()
        assertEquals(Vec2.ZERO, input.scrollDelta)
    }

    @Test
    fun `scrollConsumed resets each tick`() {
        val input = LwjglInput()
        input.beginTick()
        input.scrollConsumed = true

        input.beginTick()

        assertFalse(input.scrollConsumed)
    }

    @Test
    fun `mouse button mapping covers LEFT RIGHT MIDDLE and rejects others`() {
        assertEquals(MouseButton.Left, glfwMouseButtonToEngineButton(GLFW.GLFW_MOUSE_BUTTON_LEFT))
        assertEquals(MouseButton.Right, glfwMouseButtonToEngineButton(GLFW.GLFW_MOUSE_BUTTON_RIGHT))
        assertEquals(MouseButton.Middle, glfwMouseButtonToEngineButton(GLFW.GLFW_MOUSE_BUTTON_MIDDLE))
        assertNull(glfwMouseButtonToEngineButton(GLFW.GLFW_MOUSE_BUTTON_4))
    }
}
