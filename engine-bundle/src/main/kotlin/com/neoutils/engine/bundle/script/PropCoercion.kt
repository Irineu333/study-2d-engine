package com.neoutils.engine.bundle.script

import com.neoutils.engine.input.Key
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.serialization.NodeRef
import kotlinx.serialization.json.*
import kotlin.reflect.KClass

/**
 * Converts a `JsonElement` from a `scene.json` `properties` key (routed as a
 * script export) into the Kotlin type expected by the corresponding
 * [ExportedProperty.type].
 *
 * Supports primitives, `Vec2`, `Color`, `Rect`, `NodeRef`, `Key`, and any
 * Kotlin `enum class` reachable via [KClass.java].`isEnum`. Enum coercion
 * reads the JSON string and calls `<EnumClass>.valueOf(string)`; an unknown
 * string fails fast with the offending value, the enum class name, and the
 * list of valid entries.
 */
object PropCoercion {

    private val json = Json { ignoreUnknownKeys = true }

    fun coerce(
        element: JsonElement,
        type: KClass<*>,
        nullable: Boolean,
        propertyName: String? = null,
    ): Any? {
        if (element is JsonNull) {
            if (nullable) return null
            throw PropCoercionException(type, element, propertyName, reason = "null value for non-nullable export")
        }
        return try {
            when {
                type == Float::class -> (element as JsonPrimitive).double.toFloat()
                type == Int::class -> (element as JsonPrimitive).int
                type == Boolean::class -> (element as JsonPrimitive).boolean
                type == String::class -> (element as JsonPrimitive).content
                type == Vec2::class -> json.decodeFromJsonElement(Vec2.serializer(), element)
                type == Color::class -> json.decodeFromJsonElement(Color.serializer(), element)
                type == Rect::class -> json.decodeFromJsonElement(Rect.serializer(), element)
                type == NodeRef::class -> NodeRef<com.neoutils.engine.scene.Node>(
                    (element as JsonPrimitive).content
                )
                type == Key::class -> Key.valueOf((element as JsonPrimitive).content)
                type.java.isEnum -> coerceEnum(element, type, propertyName)
                else -> throw PropCoercionException(
                    type, element, propertyName,
                    reason = "unsupported type ${type.simpleName}",
                )
            }
        } catch (e: PropCoercionException) {
            throw e
        } catch (e: Exception) {
            throw PropCoercionException(type, element, propertyName, cause = e)
        }
    }

    private fun coerceEnum(element: JsonElement, type: KClass<*>, propertyName: String?): Any {
        val raw = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw PropCoercionException(
                type, element, propertyName,
                reason = "enum target ${type.simpleName} requires a JSON string",
            )
        @Suppress("UNCHECKED_CAST")
        val entries = (type.java.enumConstants as Array<Enum<*>>)
        val match = entries.firstOrNull { it.name == raw }
            ?: throw PropCoercionException(
                type, element, propertyName,
                reason = "value '$raw' is not a valid ${type.simpleName} (valid: ${entries.joinToString(", ") { it.name }})",
            )
        return match
    }
}

class PropCoercionException(
    type: KClass<*>,
    element: JsonElement,
    propertyName: String? = null,
    reason: String = "type mismatch",
    cause: Throwable? = null,
) : RuntimeException(
    buildString {
        append("Cannot coerce JSON value ")
        append(element)
        if (propertyName != null) {
            append(" for property '")
            append(propertyName)
            append("'")
        }
        append(" into ")
        append(type.simpleName)
        append(": ")
        append(reason)
    },
    cause,
)
