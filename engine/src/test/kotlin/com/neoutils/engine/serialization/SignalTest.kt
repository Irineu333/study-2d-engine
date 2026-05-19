package com.neoutils.engine.serialization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignalTest {

    @Test
    fun `plusAssign registers a handler`() {
        val signal = Signal<Int>()
        var received: Int? = null
        signal += { value -> received = value }
        signal.emit(42)
        assertEquals(42, received)
    }

    @Test
    fun `minusAssign unregisters a handler`() {
        val signal = Signal<Int>()
        var invoked = false
        val handler: (Int) -> Unit = { invoked = true }
        signal += handler
        signal -= handler
        signal.emit(5)
        assertFalse(invoked)
    }

    @Test
    fun `emit invokes handlers in registration order`() {
        val signal = Signal<Int>()
        val order = mutableListOf<String>()
        signal += { order += "h1" }
        signal += { order += "h2" }
        signal += { order += "h3" }
        signal.emit(0)
        assertEquals(listOf("h1", "h2", "h3"), order)
    }

    @Test
    fun `registration during emit only affects the next emission`() {
        val signal = Signal<Int>()
        val log = mutableListOf<String>()
        val h2: (Int) -> Unit = { log += "h2" }
        val h1: (Int) -> Unit = {
            log += "h1"
            signal += h2
        }
        signal += h1
        signal.emit(1)
        assertEquals(listOf("h1"), log)
        signal.emit(2)
        assertEquals(listOf("h1", "h1", "h2"), log)
    }

    @Test
    fun `removal during emit does not affect the current snapshot`() {
        val signal = Signal<Int>()
        val log = mutableListOf<String>()
        lateinit var h2: (Int) -> Unit
        val h1: (Int) -> Unit = {
            log += "h1"
            signal -= h2
        }
        h2 = { log += "h2" }
        signal += h1
        signal += h2
        signal.emit(1)
        assertEquals(listOf("h1", "h2"), log)
        signal.emit(2)
        assertTrue(log.last() == "h1")
        assertEquals(listOf("h1", "h2", "h1"), log)
    }
}
