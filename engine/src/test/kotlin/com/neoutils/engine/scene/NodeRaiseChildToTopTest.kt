package com.neoutils.engine.scene

import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class RaiseSpy(name: String, val log: MutableList<String>) : Node() {
    init { this.name = name }
    override fun onEnter() { log += "enter:$name" }
    override fun onExit() { log += "exit:$name" }
}

class NodeRaiseChildToTopTest {

    private fun names(node: Node): List<String> = node.children.map { it.name }

    @Test
    fun `raising a child moves it to the end preserving the others`() {
        val parent = Node()
        val a = Node().apply { name = "a" }
        val b = Node().apply { name = "b" }
        val c = Node().apply { name = "c" }
        parent.addChild(a); parent.addChild(b); parent.addChild(c)

        parent.raiseChildToTop(a)

        assertEquals(listOf("b", "c", "a"), names(parent))
        assertSame(parent, a.parent, "parent reference is unchanged")
    }

    @Test
    fun `raising the already-top child is a no-op`() {
        val parent = Node()
        val a = Node().apply { name = "a" }
        val b = Node().apply { name = "b" }
        parent.addChild(a); parent.addChild(b)

        parent.raiseChildToTop(b)

        assertEquals(listOf("a", "b"), names(parent))
    }

    @Test
    fun `raising a non-child is a no-op`() {
        val parent = Node()
        val a = Node().apply { name = "a" }
        val b = Node().apply { name = "b" }
        parent.addChild(a); parent.addChild(b)
        val stranger = Node().apply { name = "x" }

        parent.raiseChildToTop(stranger)

        assertEquals(listOf("a", "b"), names(parent))
    }

    @Test
    fun `raising fires no lifecycle hook and keeps the child live`() {
        val log = mutableListOf<String>()
        val root = Node()
        val parent = Node().apply { name = "parent" }
        val a = RaiseSpy("a", log)
        val b = RaiseSpy("b", log)
        parent.addChild(a); parent.addChild(b)
        root.addChild(parent)
        SceneTree(root).start()
        log.clear()

        parent.raiseChildToTop(a)

        assertTrue(log.isEmpty(), "no onEnter/onExit fired on a reorder, got $log")
        assertTrue(a.isLive, "the child stays attached to the live tree")
        assertEquals(listOf("b", "a"), names(parent))
    }

    @Test
    fun `raise during traversal defers and does not corrupt the list`() {
        val root = Node()
        val parent = Node().apply { name = "parent" }
        val a = Node().apply { name = "a" }
        val b = Node().apply { name = "b" }
        val c = Node().apply { name = "c" }
        parent.addChild(a); parent.addChild(b); parent.addChild(c)
        root.addChild(parent)
        val tree = SceneTree(root); tree.start()

        // Simulate a traversal in progress (isMutationDeferred == true).
        tree.beginPhysicsPhase()
        parent.raiseChildToTop(a)
        // The live list is untouched until the drain — no partial state.
        assertEquals(listOf("a", "b", "c"), names(parent), "list unchanged mid-traversal")
        tree.endPhysicsPhase()

        tree.applyPending()
        assertEquals(listOf("b", "c", "a"), names(parent), "reorder visible after the drain")
    }
}
