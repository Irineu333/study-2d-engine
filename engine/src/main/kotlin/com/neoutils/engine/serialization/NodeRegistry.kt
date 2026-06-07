package com.neoutils.engine.serialization

import com.neoutils.engine.physics.Area2D
import com.neoutils.engine.physics.CharacterBody2D
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.RigidBody2D
import com.neoutils.engine.physics.StaticBody2D
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.Camera2D
import com.neoutils.engine.scene.CanvasLayer
import com.neoutils.engine.scene.Circle2D
import com.neoutils.engine.scene.ColorRect
import com.neoutils.engine.scene.Label
import com.neoutils.engine.scene.Line2D
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.scene.Panel
import com.neoutils.engine.scene.Polygon2D
import com.neoutils.engine.scene.AnimatedSprite2D
import com.neoutils.engine.scene.Sprite2D
import com.neoutils.engine.scene.TileMap
import com.neoutils.engine.scene.Timer
import kotlin.reflect.KClass

/**
 * Maps node identifiers (strings used in scene files) to a `(KClass, factory)`
 * pair, with a reverse map from class back to identifier. The identifier is the
 * value that appears in the `type` field of a serialized scene entry — always
 * the FQN of a native Node type (e.g. `com.neoutils.engine.scene.Node2D`).
 * Scripts attach to a Node via `NodeEntry.script`, not via `type`.
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
        if (identifierByClass.containsKey(Node::class)) return
        register(Node::class) { Node() }
        register(Node2D::class) { Node2D() }
        register(Camera2D::class) { Camera2D() }
        register(ColorRect::class) { ColorRect() }
        register(Circle2D::class) { Circle2D() }
        register(Line2D::class) { Line2D() }
        register(Polygon2D::class) { Polygon2D() }
        register(Sprite2D::class) { Sprite2D() }
        register(AnimatedSprite2D::class) { AnimatedSprite2D() }
        register(TileMap::class) { TileMap() }
        register(Label::class) { Label() }
        register(CanvasLayer::class) { CanvasLayer() }
        register(Panel::class) { Panel() }
        register(Button::class) { Button() }
        register(Area2D::class) { Area2D() }
        register(StaticBody2D::class) { StaticBody2D() }
        register(CharacterBody2D::class) { CharacterBody2D() }
        register(RigidBody2D::class) { RigidBody2D() }
        register("engine.RigidBody2D", RigidBody2D::class) { RigidBody2D() }
        register(CollisionShape2D::class) { CollisionShape2D() }
        register(Timer::class) { Timer() }
        register("engine.Timer", Timer::class) { Timer() }
    }
}

class UnknownNodeTypeException(val typeName: String) :
    RuntimeException("No factory registered for node type: $typeName")
