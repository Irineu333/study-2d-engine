package com.neoutils.engine.tree

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.AspectMode
import com.neoutils.engine.scene.Camera2D
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.serialization.NodeRegistry
import com.neoutils.engine.serialization.SceneLoader
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class Spy(name: String, val log: MutableList<String>) : Node() {
    init { this.name = name }
    override fun onEnter() { log += "enter:$name" }
    override fun onExit() { log += "exit:$name" }
}

class SceneTreeTest {

    @BeforeTest
    fun setUp() {
        NodeRegistry.clear()
        NodeRegistry.registerEngineTypes()
    }

    @AfterTest
    fun tearDown() {
        NodeRegistry.clear()
    }

    @Test
    fun `start propagates tree to all descendants and fires onEnter pre-order`() {
        val log = mutableListOf<String>()
        val root = Spy("root", log)
        val a = Spy("a", log)
        val b = Spy("b", log)
        val c = Spy("c", log)
        root.addChild(a)
        a.addChild(b)
        root.addChild(c)
        val tree = SceneTree(root)
        tree.start()

        assertEquals(listOf("enter:root", "enter:a", "enter:b", "enter:c"), log)
        assertSame(tree, root.tree)
        assertSame(tree, a.tree)
        assertSame(tree, b.tree)
        assertSame(tree, c.tree)
        assertTrue(a.isLive)
    }

    @Test
    fun `stop clears tree on all descendants and fires onExit post-order`() {
        val log = mutableListOf<String>()
        val root = Spy("root", log)
        val a = Spy("a", log)
        val b = Spy("b", log)
        root.addChild(a)
        a.addChild(b)
        val tree = SceneTree(root)
        tree.start()
        log.clear()

        tree.stop()

        assertEquals(listOf("exit:b", "exit:a", "exit:root"), log)
        assertNull(root.tree)
        assertNull(a.tree)
        assertNull(b.tree)
        assertFalse(b.isLive)
    }

    @Test
    fun `addChild on live node propagates tree to new subtree`() {
        val log = mutableListOf<String>()
        val root = Spy("root", log)
        val tree = SceneTree(root)
        tree.start()
        log.clear()

        val a = Spy("a", log)
        val b = Spy("b", log)
        a.addChild(b)
        root.addChild(a)

        assertSame(tree, a.tree)
        assertSame(tree, b.tree)
        assertEquals(listOf("enter:a", "enter:b"), log)
    }

    @Test
    fun `removeChild on live node clears tree on detached subtree`() {
        val log = mutableListOf<String>()
        val root = Spy("root", log)
        val a = Spy("a", log)
        val b = Spy("b", log)
        a.addChild(b)
        root.addChild(a)
        val tree = SceneTree(root)
        tree.start()
        log.clear()

        root.removeChild(a)

        assertNull(a.tree)
        assertNull(b.tree)
        assertEquals(listOf("exit:b", "exit:a"), log)
    }

    @Test
    fun `onResize fires only when size actually changes`() {
        val tree = SceneTree(Node())
        val calls = mutableListOf<Pair<Float, Float>>()
        tree.onResize = { w, h -> calls += w to h }

        tree.resize(800f, 600f)
        tree.resize(800f, 600f) // no change, should not fire
        tree.resize(1024f, 600f)

        assertEquals(listOf(800f to 600f, 1024f to 600f), calls)
    }

    @Test
    fun `getNodesInGroup returns nodes in pre-order starting from root`() {
        val root = Node().apply { name = "root"; addToGroup("group") }
        val a = Node().apply { name = "a"; addToGroup("group") }
        val b = Node().apply { name = "b" }
        val c = Node().apply { name = "c"; addToGroup("group") }
        root.addChild(a)
        a.addChild(b)
        b.addChild(c)
        val tree = SceneTree(root)

        val result = tree.getNodesInGroup("group")

        assertEquals(listOf("root", "a", "c"), result.map { it.name })
    }

    @Test
    fun `currentCamera does pre-order tree walk from root`() {
        val root = Node()
        val branch = Node()
        val cameraA = Camera2D().apply { current = false }
        val cameraB = Camera2D().apply { current = true; name = "B" }
        val cameraC = Camera2D().apply { current = true; name = "C" }
        root.addChild(branch)
        branch.addChild(cameraA)
        branch.addChild(cameraB)
        root.addChild(cameraC)
        val tree = SceneTree(root)

        // Pre-order finds cameraB first (deeper but earlier in tree-walk).
        assertSame(cameraB, tree.currentCamera())
    }

    @Test
    fun `screenToWorld and worldToScreen honor current Camera2D bounds`() {
        val root = Node()
        val camera = Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))
            current = true
            aspectMode = AspectMode.FIT
        }
        root.addChild(camera)
        val tree = SceneTree(root)
        tree.resize(1280f, 900f)
        tree.start()

        val screenCenter = Vec2(640f, 450f)
        assertEquals(Vec2(400f, 300f), tree.screenToWorld(screenCenter))
        assertEquals(screenCenter, tree.worldToScreen(Vec2(400f, 300f)))
    }

    @Test
    fun `Node tree field does not appear in serialized JSON`() {
        val root = Node().apply { name = "root" }
        root.addChild(Node2D().apply { name = "child" })
        val tree = SceneTree(root)
        tree.start()

        val json = SceneLoader.save(root)
        assertFalse(json.contains("\"tree\""), "JSON must not include the @Transient tree field: $json")
    }
}
