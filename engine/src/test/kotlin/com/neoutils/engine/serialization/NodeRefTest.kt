package com.neoutils.engine.serialization

import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.scene.Scene
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
        val scene = Scene()
        val a = Named("A")
        val b = Named("B")
        val c = Named("C")
        scene.addChild(a)
        a.addChild(b)
        a.addChild(c)
        val ref = NodeRef<Node>(path = "../B")
        assertSame(b, ref.resolve(from = c))
    }

    @Test
    fun `returns null when target type does not match`() {
        val scene = Scene()
        val foo = Named("Foo")
        scene.addChild(foo)
        val ref = NodeRef<Node2D>(path = "Foo")
        assertNull(ref.resolve(from = scene))
    }

    @Test
    fun `returns null for invalid path`() {
        val scene = Scene()
        val a = Named("A")
        scene.addChild(a)
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
        val scene = Scene()
        val paddle = Tagged("Paddle")
        val ball = Tagged("Ball")
        scene.addChild(paddle)
        scene.addChild(ball)
        scene.start()

        val ref = NodeRef<Node2D>(path = "../Ball")
        assertSame(ball, ref.resolve(from = paddle))

        scene.removeChild(paddle)
        val sceneTwo = Scene()
        val newBall = Tagged("Ball")
        sceneTwo.addChild(paddle)
        sceneTwo.addChild(newBall)
        sceneTwo.start()

        val resolved = ref.resolve(from = paddle)
        assertSame(newBall, resolved)
        assertNotSame(ball, resolved)
    }
}
