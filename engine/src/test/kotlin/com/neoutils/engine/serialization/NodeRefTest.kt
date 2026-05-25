package com.neoutils.engine.serialization

import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.tree.SceneTree
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

private class Named(name: String) : Node() {
    init { this.name = name }
}

private class Tagged(name: String) : Node2D() {
    init { this.name = name }
}

class NodeRefTest {

    @Test
    fun `walks up and down`() {
        val root = Node()
        val a = Named("A")
        val b = Named("B")
        val c = Named("C")
        root.addChild(a)
        a.addChild(b)
        a.addChild(c)
        val ref = NodeRef<Node>(path = "../B")
        assertSame(b, ref.resolve(from = c))
    }

    @Test
    fun `returns null when target type does not match`() {
        val root = Node()
        val foo = Named("Foo")
        root.addChild(foo)
        val ref = NodeRef<Node2D>(path = "Foo")
        assertNull(ref.resolve(from = root))
    }

    @Test
    fun `returns null for invalid path`() {
        val root = Node()
        val a = Named("A")
        root.addChild(a)
        val ref = NodeRef<Node>(path = "Missing")
        assertNull(ref.resolve(from = a))
    }

    @Test
    fun `empty path resolves to bearer`() {
        val a = Named("A")
        val ref = NodeRef<Node>(path = "")
        assertSame(a, ref.resolve(from = a))
    }

    @Test
    fun `serializes and deserializes path`() {
        val original = NodeRef<Node2D>(path = "../Ball")
        val serializer = NodeRefSerializer<Node2D>()
        val encoded = Json.encodeToString(serializer, original)
        val decoded = Json.decodeFromString(serializer, encoded)
        assertEquals("../Ball", decoded.path)
        assertEquals("\"../Ball\"", encoded)
    }

    @Test
    fun `cache invalidates after bearer re-attach`() {
        val root = Node()
        val paddle = Tagged("Paddle")
        val ball = Tagged("Ball")
        root.addChild(paddle)
        root.addChild(ball)
        val tree = SceneTree(root)
        tree.start()

        val ref = NodeRef<Node2D>(path = "../Ball")
        assertSame(ball, ref.resolve(from = paddle))

        root.removeChild(paddle)
        val rootTwo = Node()
        val newBall = Tagged("Ball")
        rootTwo.addChild(paddle)
        rootTwo.addChild(newBall)
        val treeTwo = SceneTree(rootTwo)
        treeTwo.start()

        val resolved = ref.resolve(from = paddle)
        assertSame(newBall, resolved)
        assertNotSame(ball, resolved)
    }
}
