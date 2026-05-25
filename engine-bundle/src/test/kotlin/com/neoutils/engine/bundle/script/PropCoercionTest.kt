package com.neoutils.engine.bundle.script

import com.neoutils.engine.scene.TimerMode
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private enum class LocalDummyEnum { A, B, C }

class PropCoercionTest {

    @Test
    fun `coerces IDLE string to TimerMode_IDLE`() {
        val v = PropCoercion.coerce(JsonPrimitive("IDLE"), TimerMode::class, nullable = false, propertyName = "mode")
        assertEquals(TimerMode.IDLE, v)
    }

    @Test
    fun `coerces PHYSICS string to TimerMode_PHYSICS`() {
        val v = PropCoercion.coerce(JsonPrimitive("PHYSICS"), TimerMode::class, nullable = false)
        assertEquals(TimerMode.PHYSICS, v)
    }

    @Test
    fun `lowercase 'physics' fails with rich message`() {
        val ex = assertFailsWith<PropCoercionException> {
            PropCoercion.coerce(JsonPrimitive("physics"), TimerMode::class, nullable = false, propertyName = "mode")
        }
        val msg = ex.message!!
        assertTrue(msg.contains("'mode'"), "property name: $msg")
        assertTrue(msg.contains("physics"), "offending value: $msg")
        assertTrue(msg.contains("TimerMode"), "enum class: $msg")
        assertTrue(msg.contains("PHYSICS"), "valid entries: $msg")
        assertTrue(msg.contains("IDLE"), "valid entries: $msg")
    }

    @Test
    fun `coercion is generic over any enum class`() {
        val a = PropCoercion.coerce(JsonPrimitive("A"), LocalDummyEnum::class, nullable = false)
        val b = PropCoercion.coerce(JsonPrimitive("B"), LocalDummyEnum::class, nullable = false)
        assertEquals(LocalDummyEnum.A, a)
        assertEquals(LocalDummyEnum.B, b)
    }

    @Test
    fun `unknown enum value lists valid entries`() {
        val ex = assertFailsWith<PropCoercionException> {
            PropCoercion.coerce(JsonPrimitive("Z"), LocalDummyEnum::class, nullable = false)
        }
        val msg = ex.message!!
        assertTrue(msg.contains("Z"))
        assertTrue(msg.contains("A"))
        assertTrue(msg.contains("B"))
        assertTrue(msg.contains("C"))
    }
}
