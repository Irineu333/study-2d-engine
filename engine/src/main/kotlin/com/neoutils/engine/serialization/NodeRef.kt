package com.neoutils.engine.serialization

import com.neoutils.engine.scene.Node
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.reflect.KClass

/**
 * Typed, path-based reference to another node in the scene graph. The path is
 * relative to the resolving caller: `..` walks up to the parent, segments
 * separated by `/` walk down by `findChild`, and an empty path resolves to
 * the caller itself.
 *
 * Serialized as a bare path string so scene files stay legible. Resolution
 * is lazy and cached until [invalidate] is called or the bearer re-attaches
 * to the live tree (detected by `Node.attachGeneration`). When constructed
 * via the reified factory `NodeRef<T>(path)`, [targetType] is filled in so
 * `resolve` can return `null` for type mismatches; raw instances built by
 * the serializer have `targetType == null` and rely on the caller's `as?`.
 */
@Serializable(with = NodeRefSerializer::class)
class NodeRef<T : Node> @PublishedApi internal constructor(
    var path: String,
    @Transient internal var targetType: KClass<out Node>? = null,
) {

    @Transient
    private var cached: Node? = null

    @Transient
    private var resolvedFrom: Node? = null

    @Transient
    private var cachedGeneration: Long = -1L

    /** Drop any cached resolution. Called when the bearer re-attaches. */
    fun invalidate() {
        cached = null
        resolvedFrom = null
        cachedGeneration = -1L
    }

    @Suppress("UNCHECKED_CAST")
    fun resolve(from: Node): T? {
        if (cached != null &&
            resolvedFrom === from &&
            cachedGeneration == from.attachGeneration
        ) return cached as? T
        val resolved = walk(from, path) ?: run {
            invalidate()
            return null
        }
        val type = targetType
        if (type != null && !type.isInstance(resolved)) {
            invalidate()
            return null
        }
        cached = resolved
        resolvedFrom = from
        cachedGeneration = from.attachGeneration
        return resolved as T?
    }

    private fun walk(start: Node, path: String): Node? {
        if (path.isEmpty()) return start
        var current: Node? = start
        for (segment in path.split('/')) {
            if (current == null) return null
            current = when (segment) {
                "" -> current
                ".." -> current.parent
                else -> current.findChild(segment)
            }
        }
        return current
    }
}

/**
 * Reified "fake constructor" for `NodeRef<T>`. Captures `T::class` so
 * `resolve` can refuse a target whose runtime type does not match `T`. JVM
 * erasure prevents the cast `as? T` from doing this on its own.
 */
@Suppress("FunctionName")
inline fun <reified T : Node> NodeRef(path: String = ""): NodeRef<T> {
    return NodeRef<T>(path, targetType = T::class)
}

/**
 * Encodes `NodeRef<T>` as a bare path string. Generic over `T` only at the
 * Kotlin type level — the wire form is just the path — so the serializer
 * does not need a `KSerializer<T>` and stays decoupled from whether the
 * target `Node` subclass is itself `@Serializable`.
 */
class NodeRefSerializer<T : Node> : KSerializer<NodeRef<T>> {

    override val descriptor: SerialDescriptor = String.serializer().descriptor

    override fun serialize(encoder: Encoder, value: NodeRef<T>) {
        encoder.encodeString(value.path)
    }

    override fun deserialize(decoder: Decoder): NodeRef<T> {
        return NodeRef<T>(decoder.decodeString(), targetType = null)
    }
}
