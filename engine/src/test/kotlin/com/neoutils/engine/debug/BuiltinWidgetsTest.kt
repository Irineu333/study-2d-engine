package com.neoutils.engine.debug

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.Area2D
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.PhysicsSystem
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.physics.RigidBody2D
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuiltinWidgetsTest {

    @Test
    fun `ColliderWidget in AABB mode draws one rect per active CollisionShape2D`() {
        val area = Area2D().apply { name = "Trigger" }
        area.addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { size = Vec2(20f, 20f) } })
        area.transform = Transform(position = Vec2(50f, 50f))

        val body = RigidBody2D().apply { name = "Body" }
        body.addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { size = Vec2(30f, 30f) } })
        body.transform = Transform(position = Vec2(120f, 80f))

        val root = Node().apply {
            addChild(area)
            addChild(body)
        }
        val tree = SceneTree(root)
        tree.physicsSystem = PhysicsSystem()
        tree.start()
        tree.debug.colliders.apply {
            mode = ColliderDrawMode.AABB
            enabled = true
        }

        val recorder = RecordingRenderer()
        tree.render(recorder)

        val outlines = recorder.events.filterIsInstance<RecordedEvent.Rect>().filter { !it.filled }
        assertEquals(2, outlines.size, "got ${recorder.events}")
        // One green (Area), one red-ish (Body).
        assertTrue(outlines.any { it.color.g > 0.9f })
        assertTrue(outlines.any { it.color.r > 0.9f })
    }

    @Test
    fun `ColliderWidget disabled emits no outlines`() {
        val area = Area2D().apply { name = "Trigger" }
        area.addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { size = Vec2(20f, 20f) } })
        val root = Node().apply { addChild(area) }
        val tree = SceneTree(root)
        tree.physicsSystem = PhysicsSystem()
        tree.start()
        tree.debug.colliders.enabled = false

        val recorder = RecordingRenderer()
        tree.render(recorder)
        val outlines = recorder.events.filterIsInstance<RecordedEvent.Rect>().filter { !it.filled }
        assertEquals(0, outlines.size)
    }
}

@Suppress("UNUSED")
private val unused: Rect = Rect(Vec2.ZERO, Vec2.ZERO)
