package com.neoutils.engine.scene

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.BoxCollider
import com.neoutils.engine.physics.Collider
import com.neoutils.engine.physics.PhysicsSystem
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class LifecycleSpy(name: String, val log: MutableList<String>) : Node() {
    init { this.name = name }
    override fun onEnter() { log += "enter:$name" }
    override fun onExit() { log += "exit:$name" }
}

class SceneMutationDuringTraversalTest {

    @Test
    fun `addChild during onProcess does not crash and applies before next phase`() {
        val root = Node()
        val tree = SceneTree(root)
        val log = mutableListOf<String>()
        val spawned = LifecycleSpy("spawn", log)
        val spawner = object : Node() {
            var didSpawn = false
            override fun onProcess(dt: Float) {
                if (!didSpawn) {
                    didSpawn = true
                    root.addChild(spawned)
                }
            }
        }
        root.addChild(spawner)
        tree.start()
        log.clear()

        tree.process(0.016f)
        tree.applyPending()

        assertTrue(spawned.isLive, "spawned child should be live after drain")
        assertEquals(listOf("enter:spawn"), log)
        assertTrue(root.children.contains(spawned))
    }

    @Test
    fun `removeChild during onProcess does not crash and applies before next phase`() {
        val root = Node()
        val tree = SceneTree(root)
        val log = mutableListOf<String>()
        val victim = LifecycleSpy("victim", log)
        root.addChild(victim)
        val killer = object : Node() {
            override fun onProcess(dt: Float) { root.removeChild(victim) }
        }
        root.addChild(killer)
        tree.start()
        log.clear()

        tree.process(0.016f)
        tree.applyPending()

        assertFalse(victim.isLive)
        assertEquals(listOf("exit:victim"), log)
        assertFalse(root.children.contains(victim))
    }

    @Test
    fun `addChild during onCollide does not crash and applies before next phase`() {
        val root = Node()
        val tree = SceneTree(root)
        val log = mutableListOf<String>()
        val spawned = LifecycleSpy("spawn", log)
        val a = object : BoxCollider() {
            var didSpawn = false
            override fun onCollide(other: Collider) {
                if (!didSpawn) {
                    didSpawn = true
                    root.addChild(spawned)
                }
            }
        }
        val b = BoxCollider().apply { size = Vec2(10f, 10f) }
        root.addChild(a)
        root.addChild(b)
        tree.start()
        log.clear()

        PhysicsSystem().step(tree)
        tree.applyPending()

        assertTrue(spawned.isLive)
        assertEquals(listOf("enter:spawn"), log)
    }

    @Test
    fun `removeChild during onCollide does not crash and applies before next phase`() {
        val root = Node()
        val tree = SceneTree(root)
        val log = mutableListOf<String>()
        val victim = LifecycleSpy("victim", log)
        root.addChild(victim)
        val a = object : BoxCollider() {
            override fun onCollide(other: Collider) { root.removeChild(victim) }
        }
        val b = BoxCollider().apply { size = Vec2(10f, 10f) }
        root.addChild(a)
        root.addChild(b)
        tree.start()
        log.clear()

        PhysicsSystem().step(tree)
        tree.applyPending()

        assertFalse(victim.isLive)
        assertEquals(listOf("exit:victim"), log)
    }
}
