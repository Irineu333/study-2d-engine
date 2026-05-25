package com.neoutils.engine.serialization

import com.neoutils.engine.scene.Node
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

private class Sample : Node()
private class Other : Node()

class NodeRegistryTest {

    @BeforeTest fun setUp() { NodeRegistry.clear() }
    @AfterTest fun tearDown() { NodeRegistry.clear() }

    @Test
    fun `register by class derives identifier from qualified name`() {
        NodeRegistry.register(Sample::class) { Sample() }
        val typeName = Sample::class.qualifiedName!!
        val first = NodeRegistry.create(typeName)
        val second = NodeRegistry.create(typeName)
        assertTrue(first is Sample)
        assertNotSame(first, second)
    }

    @Test
    fun `register with explicit identifier resolves by that identifier`() {
        NodeRegistry.register("scripts/sample.nengine.kts", Sample::class) { Sample() }
        val instance = NodeRegistry.create("scripts/sample.nengine.kts")
        assertTrue(instance is Sample)
    }

    @Test
    fun `identifierFor returns the identifier under which a class was registered`() {
        NodeRegistry.register("scripts/sample.nengine.kts", Sample::class) { Sample() }
        assertEquals("scripts/sample.nengine.kts", NodeRegistry.identifierFor(Sample::class))
    }

    @Test
    fun `identifierFor returns null for unregistered classes`() {
        assertNull(NodeRegistry.identifierFor(Other::class))
    }

    @Test
    fun `unknown type throws with name in message`() {
        try {
            NodeRegistry.create("com.example.Mystery")
            fail("expected UnknownNodeTypeException")
        } catch (e: UnknownNodeTypeException) {
            assertEquals("com.example.Mystery", e.typeName)
            assertTrue(e.message!!.contains("com.example.Mystery"))
        }
    }

    @Test
    fun `registerEngineTypes registers built-ins`() {
        NodeRegistry.registerEngineTypes()
        assertTrue(NodeRegistry.isRegistered("com.neoutils.engine.scene.Node"))
        assertTrue(NodeRegistry.isRegistered("com.neoutils.engine.scene.Node2D"))
        assertTrue(NodeRegistry.isRegistered("com.neoutils.engine.scene.Camera2D"))
        assertTrue(NodeRegistry.isRegistered("com.neoutils.engine.scene.ColorRect"))
        assertTrue(NodeRegistry.isRegistered("com.neoutils.engine.scene.Circle2D"))
        assertTrue(NodeRegistry.isRegistered("com.neoutils.engine.scene.Line2D"))
        assertTrue(NodeRegistry.isRegistered("com.neoutils.engine.scene.Polygon2D"))
        assertTrue(NodeRegistry.isRegistered("com.neoutils.engine.scene.Label"))
        assertTrue(NodeRegistry.isRegistered("com.neoutils.engine.physics.BoxCollider"))
    }

    @Test
    fun `registerEngineTypes does not register Scene`() {
        NodeRegistry.registerEngineTypes()
        assertFalse(NodeRegistry.isRegistered("com.neoutils.engine.scene.Scene"))
        val ex = assertFailsWith<UnknownNodeTypeException> {
            NodeRegistry.create("com.neoutils.engine.scene.Scene")
        }
        assertEquals("com.neoutils.engine.scene.Scene", ex.typeName)
    }

    @Test
    fun `registerEngineTypes is idempotent`() {
        NodeRegistry.registerEngineTypes()
        // Second call must not throw nor duplicate; we observe equivalence by
        // confirming the engine identifiers still resolve.
        NodeRegistry.registerEngineTypes()
        val node = NodeRegistry.create("com.neoutils.engine.scene.Node")
        assertTrue(node is Node)
        assertEquals("com.neoutils.engine.scene.Node", NodeRegistry.identifierFor(Node::class))
    }
}
