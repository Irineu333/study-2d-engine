package com.neoutils.engine.physics

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class RecordingBox(boxSize: Vec2) : BoxCollider() {
    init { size = boxSize }
    val partners: MutableList<Collider> = mutableListOf()
    override fun onCollide(other: Collider) { partners += other }
}

class PhysicsSystemTest {

    @Test
    fun `bounds derived from transform position`() {
        val root = Node()
        val box = BoxCollider().apply {
            size = Vec2(10f, 20f)
            transform = Transform(position = Vec2(5f, 7f))
        }
        root.addChild(box)
        SceneTree(root).start()
        val b = box.bounds()
        assertEquals(Vec2(5f, 7f), b.origin)
        assertEquals(Vec2(10f, 20f), b.size)
    }

    @Test
    fun `non-overlapping colliders do not fire onCollide`() {
        val root = Node()
        val a = RecordingBox(Vec2(10f, 10f)).apply { transform = Transform(position = Vec2(0f, 0f)) }
        val b = RecordingBox(Vec2(10f, 10f)).apply { transform = Transform(position = Vec2(100f, 100f)) }
        root.addChild(a)
        root.addChild(b)
        val tree = SceneTree(root)
        tree.start()
        PhysicsSystem().step(tree)
        assertEquals(0, a.partners.size)
        assertEquals(0, b.partners.size)
    }

    @Test
    fun `overlapping pair fires onCollide exactly once each`() {
        val root = Node()
        val a = RecordingBox(Vec2(10f, 10f)).apply { transform = Transform(position = Vec2(0f, 0f)) }
        val b = RecordingBox(Vec2(10f, 10f)).apply { transform = Transform(position = Vec2(5f, 5f)) }
        root.addChild(a)
        root.addChild(b)
        val tree = SceneTree(root)
        tree.start()
        PhysicsSystem().step(tree)
        assertEquals(listOf<Collider>(b), a.partners)
        assertEquals(listOf<Collider>(a), b.partners)
    }

    @Test
    fun `bounds account for parent scale`() {
        val root = Node()
        val parent = com.neoutils.engine.scene.Node2D().apply {
            transform = Transform(scale = Vec2(2f, 3f))
        }
        val box = BoxCollider().apply { size = Vec2(10f, 20f) }
        parent.addChild(box)
        root.addChild(parent)
        SceneTree(root).start()
        val b = box.bounds()
        assertEquals(Vec2(20f, 60f), b.size)
    }

    @Test
    fun `bounds expand to AABB of OBB when parent is rotated`() {
        val root = Node()
        val parent = com.neoutils.engine.scene.Node2D().apply {
            transform = Transform(rotation = (PI / 4.0).toFloat())
        }
        val box = BoxCollider().apply { size = Vec2(10f, 10f) }
        parent.addChild(box)
        root.addChild(parent)
        SceneTree(root).start()
        val expected = 10f * sqrt(2f)
        val b = box.bounds()
        assertTrue(kotlin.math.abs(b.size.x - expected) < 1e-3f, "width=${b.size.x}, expected≈$expected")
        assertTrue(kotlin.math.abs(b.size.y - expected) < 1e-3f, "height=${b.size.y}, expected≈$expected")
    }

    @Test
    fun `bounds account for parent transform`() {
        val root = Node()
        val parent = com.neoutils.engine.scene.Node2D().apply {
            transform = Transform(position = Vec2(100f, 50f))
        }
        val box = BoxCollider().apply {
            size = Vec2(10f, 10f)
            transform = Transform(position = Vec2(5f, 5f))
        }
        parent.addChild(box)
        root.addChild(parent)
        SceneTree(root).start()
        assertEquals(Vec2(105f, 55f), box.bounds().origin)
    }
}
