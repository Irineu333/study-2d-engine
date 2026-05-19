package com.neoutils.engine.skiko

import com.neoutils.engine.input.Key
import java.awt.Component
import java.awt.event.KeyEvent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkikoInputKeyMappingTest {

    private val dummySource: Component = JPanel()

    @Test
    fun `F1 press is reported by isKeyDown and wasKeyPressed for one tick`() {
        val input = SkikoInput()
        input.beginTick()

        input.onAwtKey(keyEvent(KeyEvent.VK_F1), pressed = true)
        input.beginTick()

        assertTrue(input.isKeyDown(Key.F1))
        assertTrue(input.wasKeyPressed(Key.F1))

        input.beginTick()
        assertTrue(input.isKeyDown(Key.F1))
        assertFalse(input.wasKeyPressed(Key.F1))

        input.onAwtKey(keyEvent(KeyEvent.VK_F1), pressed = false)
        input.beginTick()
        assertFalse(input.isKeyDown(Key.F1))
    }

    @Test
    fun `F2 maps independently from F1`() {
        val input = SkikoInput()
        input.beginTick()

        input.onAwtKey(keyEvent(KeyEvent.VK_F2), pressed = true)
        input.beginTick()

        assertTrue(input.isKeyDown(Key.F2))
        assertFalse(input.isKeyDown(Key.F1))
    }

    @Test
    fun `letters and arrows map correctly`() {
        val input = SkikoInput()
        input.beginTick()

        input.onAwtKey(keyEvent(KeyEvent.VK_A), pressed = true)
        input.onAwtKey(keyEvent(KeyEvent.VK_UP), pressed = true)
        input.beginTick()

        assertTrue(input.isKeyDown(Key.A))
        assertTrue(input.isKeyDown(Key.ARROW_UP))
        assertTrue(input.wasKeyPressed(Key.A))
        assertTrue(input.wasKeyPressed(Key.ARROW_UP))
    }

    private fun keyEvent(vk: Int): KeyEvent = KeyEvent(
        dummySource,
        KeyEvent.KEY_PRESSED,
        System.currentTimeMillis(),
        0,
        vk,
        KeyEvent.CHAR_UNDEFINED,
    )
}
