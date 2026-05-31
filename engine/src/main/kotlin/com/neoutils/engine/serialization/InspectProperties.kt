package com.neoutils.engine.serialization

import com.neoutils.engine.scene.Node
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

/**
 * One `@Inspect` property of a node resolved to its display name and current
 * value. [displayName] is the annotation's `displayName` when non-empty, else
 * the property name; [value] is the live getter result (so a `Vec2`/`Color`
 * comes out via its data-class `toString()`).
 */
data class InspectEntry(val displayName: String, val value: Any?)

/**
 * Enumerates every `@Inspect` property of [node] with its current value,
 * reusing the loader's reflection pattern (`memberProperties` +
 * `findAnnotation<Inspect>()` + getter). `@Transient` runtime state is never
 * included — by convention those fields carry `@Transient`, not `@Inspect`, so
 * the annotation filter excludes them. Read by the scene-picker panel to show
 * a node's configuration; [SceneLoader] keeps its own private equivalent for
 * serialization and is intentionally left untouched.
 */
fun inspectProperties(node: Node): List<InspectEntry> {
    val out = mutableListOf<InspectEntry>()
    for (property in node::class.memberProperties) {
        val inspect = property.findAnnotation<Inspect>() ?: continue
        @Suppress("UNCHECKED_CAST")
        val getter = property as KProperty1<Any, Any?>
        val displayName = inspect.displayName.ifEmpty { property.name }
        out += InspectEntry(displayName, getter.get(node))
    }
    return out
}
