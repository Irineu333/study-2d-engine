package com.neoutils.engine.serialization

import com.neoutils.engine.scene.Node
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Typed, path-based reference to another node in the scene graph. The path is
 * relative to the resolving caller: `..` walks up to the parent, segments
 * separated by `/` walk down by `findChild`, and an empty path resolves to
 * the caller itself.
 *
 * The class is `@Serializable` so the path persists in scene files;
 * resolution is lazy and cached until [invalidate] is called or the bearer
 * is re-attached to the live tree.
 */
@Serializable
class NodeRef<T : Node>(
    var path: String = "",
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
        cached = resolved
        resolvedFrom = from
        cachedGeneration = from.attachGeneration
        return resolved as? T
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
