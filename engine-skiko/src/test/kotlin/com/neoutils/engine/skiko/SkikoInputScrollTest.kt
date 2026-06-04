package com.neoutils.engine.skiko

import com.neoutils.engine.math.Vec2
import java.awt.Component
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class SkikoInputScrollTest {

    private val dummySource: Component = JPanel()

    @Test
    fun `wheel-down produces a positive y delta for one tick`() {
        val input = SkikoInput()
        input.beginTick()
        // AWT's preciseWheelRotation is positive for a downward roll, matching
        // the SPI's "positive y = scroll down", so no inversion is applied.
        input.onAwtMouseWheel(wheelEvent(rotation = 1))
        input.beginTick()

        assertTrue(input.scrollDelta.y > 0f, "wheel-down is a positive y delta")
        assertEquals(0f, input.scrollDelta.x)

        input.beginTick()
        assertEquals(Vec2.ZERO, input.scrollDelta, "delta clears after one tick")
    }

    @Test
    fun `no wheel motion reads as zero`() {
        val input = SkikoInput()
        input.beginTick()
        assertEquals(Vec2.ZERO, input.scrollDelta)
    }

    @Test
    fun `scrollConsumed resets each tick`() {
        val input = SkikoInput()
        input.beginTick()
        input.scrollConsumed = true

        input.beginTick()

        assertFalse(input.scrollConsumed)
    }

    private fun wheelEvent(rotation: Int): MouseWheelEvent = MouseWheelEvent(
        dummySource,
        MouseEvent.MOUSE_WHEEL,
        System.currentTimeMillis(),
        0,
        0,
        0,
        0,
        false,
        MouseWheelEvent.WHEEL_UNIT_SCROLL,
        1,
        rotation,
    )
}
