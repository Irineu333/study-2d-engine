package com.neoutils.engine.serialization

import com.neoutils.engine.physics.BoxCollider
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.scene.Scene
import com.neoutils.engine.scene.Shape
import com.neoutils.engine.scene.Text
import kotlin.reflect.KClass

/**
 * Maps node identifiers (strings used in scene files) to a `(KClass, factory)`
 * pair, with a reverse map from class back to identifier. The identifier is the
 * value that appears in the `type` field of a serialized scene entry: for
 * compiled Kotlin types it is typically the FQN; for scripts it is the script
 * path relative to its bundle (e.g. `scripts/paddle.nengine.kts`).
 */
object NodeRegistry {

    private data class Entry(val klass: KClass<out Node>, val factory: () -> Node)

    private val byIdentifier: MutableMap<String, Entry> = mutableMapOf()
    private val identifierByClass: MutableMap<KClass<out Node>, String> = mutableMapOf()

    fun register(identifier: String, klass: KClass<out Node>, factory: () -> Node) {
        byIdentifier[identifier] = Entry(klass, factory)
        identifierByClass[klass] = identifier
    }

    fun register(klass: KClass<out Node>, factory: () -> Node) {
        val identifier = klass.qualifiedName
            ?: error("Cannot register a node type with no qualified name: $klass")
        register(identifier, klass, factory)
    }

    fun create(identifier: String): Node {
        val entry = byIdentifier[identifier] ?: throw UnknownNodeTypeException(identifier)
        return entry.factory()
    }

    fun identifierFor(klass: KClass<out Node>): String? = identifierByClass[klass]

    fun isRegistered(identifier: String): Boolean = identifier in byIdentifier

    fun findBySimpleName(simpleName: String): KClass<out Node>? =
        identifierByClass.keys.firstOrNull { it.simpleName == simpleName }

    /** Drops every registration. Intended for tests; production code typically
     *  registers once at startup and never clears. */
    fun clear() {
        byIdentifier.clear()
        identifierByClass.clear()
    }

    /** Registers every concrete `Node` subclass shipped by `:engine`. Idempotent:
     *  re-entry is a no-op when the engine types are already registered. */
    fun registerEngineTypes() {
        if (identifierByClass.containsKey(Scene::class)) return
        register(Scene::class) { Scene() }
        register(Node2D::class) { Node2D() }
        register(Shape::class) { Shape() }
        register(Text::class) { Text() }
        register(BoxCollider::class) { BoxCollider() }
    }
}

class UnknownNodeTypeException(val typeName: String) :
    RuntimeException("No factory registered for node type: $typeName")
