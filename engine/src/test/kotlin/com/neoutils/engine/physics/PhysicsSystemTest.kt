package com.neoutils.engine.physics

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Scene
import kotlin.test.Test
import kotlin.test.assertEquals

private class RecordingBox(size: Vec2) : BoxCollider(size) {
    val partners: MutableList<Collider> = mutableListOf()
    override fun onCollide(other: Collider) { partners += other }
}

class PhysicsSystemTest {

    @Test
    fun `bounds derived from transform position`() {
        val scene = Scene()
        val box = BoxCollider(Vec2(10f, 20f)).apply {
            transform = Transform(position = Vec2(5f, 7f))
        }
        scene.addChild(box)
        scene.start()
        val b = box.bounds()
        assertEquals(Vec2(5f, 7f), b.origin)
        assertEquals(Vec2(10f, 20f), b.size)
    }

    @Test
    fun `non-overlapping colliders do not fire onCollide`() {
        val scene = Scene()
        val a = RecordingBox(Vec2(10f, 10f)).apply { transform = Transform(position = Vec2(0f, 0f)) }
        val b = RecordingBox(Vec2(10f, 10f)).apply { transform = Transform(position = Vec2(100f, 100f)) }
        scene.addChild(a)
        scene.addChild(b)
        scene.start()
        PhysicsSystem().step(scene)
        assertEquals(0, a.partners.size)
        assertEquals(0, b.partners.size)
    }

    @Test
    fun `overlapping pair fires onCollide exactly once each`() {
        val scene = Scene()
        val a = RecordingBox(Vec2(10f, 10f)).apply { transform = Transform(position = Vec2(0f, 0f)) }
        val b = RecordingBox(Vec2(10f, 10f)).apply { transform = Transform(position = Vec2(5f, 5f)) }
        scene.addChild(a)
        scene.addChild(b)
        scene.start()
        PhysicsSystem().step(scene)
        assertEquals(listOf<Collider>(b), a.partners)
        assertEquals(listOf<Collider>(a), b.partners)
    }

    @Test
    fun `bounds account for parent transform`() {
        val scene = Scene()
        val parent = com.neoutils.engine.scene.Node2D().apply {
            transform = Transform(position = Vec2(100f, 50f))
        }
        val box = BoxCollider(Vec2(10f, 10f)).apply { transform = Transform(position = Vec2(5f, 5f)) }
        parent.addChild(box)
        scene.addChild(parent)
        scene.start()
        assertEquals(Vec2(105f, 55f), box.bounds().origin)
    }
}
