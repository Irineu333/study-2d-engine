package com.neoutils.engine.bundle.script

import com.neoutils.engine.input.Key
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.serialization.NodeRef
import kotlinx.serialization.json.*
import kotlin.reflect.KClass

/**
 * Converts a `JsonElement` from `scene.json` `props` into the Kotlin type
 * expected by the corresponding [ExportedProperty.type].
 */
object PropCoercion {

    private val json = Json { ignoreUnknownKeys = true }

    fun coerce(element: JsonElement, type: KClass<*>, nullable: Boolean): Any? {
        if (element is JsonNull) {
            if (nullable) return null
            throw PropCoercionException(type, element, reason = "null value for non-nullable export")
        }
        return try {
            when (type) {
                Float::class -> (element as JsonPrimitive).double.toFloat()
                Int::class -> (element as JsonPrimitive).int
                Boolean::class -> (element as JsonPrimitive).boolean
                String::class -> (element as JsonPrimitive).content
                Vec2::class -> json.decodeFromJsonElement(Vec2.serializer(), element)
                Color::class -> json.decodeFromJsonElement(Color.serializer(), element)
                Rect::class -> json.decodeFromJsonElement(Rect.serializer(), element)
                NodeRef::class -> NodeRef<com.neoutils.engine.scene.Node>(
                    (element as JsonPrimitive).content
                )
                Key::class -> Key.valueOf((element as JsonPrimitive).content)
                else -> throw PropCoercionException(type, element, reason = "unsupported type ${type.simpleName}")
            }
        } catch (e: PropCoercionException) {
            throw e
        } catch (e: Exception) {
            throw PropCoercionException(type, element, cause = e)
        }
    }
}

class PropCoercionException(
    type: KClass<*>,
    element: JsonElement,
    reason: String = "type mismatch",
    cause: Throwable? = null,
) : RuntimeException(
    "Cannot coerce JSON value $element into ${type.simpleName}: $reason",
    cause
)
