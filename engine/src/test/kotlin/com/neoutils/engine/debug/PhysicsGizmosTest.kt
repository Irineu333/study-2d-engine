package com.neoutils.engine.debug

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.Area2D
import com.neoutils.engine.physics.CharacterBody2D
import com.neoutils.engine.physics.CircleShape2D
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.PhysicsSystem
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.physics.RigidBody2D
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PhysicsGizmosTest {

    private val eps = 1e-3f

    private fun near(a: Float, b: Float): Boolean = kotlin.math.abs(a - b) < eps

    // --- ShapeGizmoWidget ---

    @Test
    fun `ShapeGizmoWidget draws circle as outline at world center`() {
        val area = Area2D().apply { transform = Transform(position = Vec2(50f, 50f)) }
        area.addChild(CollisionShape2D().apply { shape = CircleShape2D().apply { radius = 7f } })
        val tree = SceneTree(Node().apply { addChild(area) }).also { it.start() }
        tree.debug.shapeGizmo.enabled = true

        val recorder = RecordingRenderer()
        tree.debug.shapeGizmo.drawDebug(recorder)

        val circle = recorder.events.filterIsInstance<RecordedEvent.Circle>().single()
        assertEquals(50f, circle.center.x, eps)
        assertEquals(50f, circle.center.y, eps)
        assertEquals(7f, circle.radius, eps)
        assertTrue(!circle.filled, "shape circle must be a non-filled outline")
        // No transform bookkeeping inside the widget — the world pass owns it.
        assertEquals(0, recorder.events.count { it is RecordedEvent.Push || it is RecordedEvent.Pop })
    }

    @Test
    fun `ShapeGizmoWidget draws rotated rectangle as a non-axis-aligned quad`() {
        val body = RigidBody2D().apply { transform = Transform(position = Vec2(100f, 80f), rotation = (PI / 6).toFloat()) }
        body.addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { size = Vec2(30f, 30f) } })
        val tree = SceneTree(Node().apply { addChild(body) }).also { it.start() }
        tree.debug.shapeGizmo.enabled = true

        val recorder = RecordingRenderer()
        tree.debug.shapeGizmo.drawDebug(recorder)

        val lines = recorder.events.filterIsInstance<RecordedEvent.Line>()
        assertEquals(4, lines.size, "a rectangle outlines as four edges")
        // A rotated quad has at least one diagonal edge (endpoints differ in
        // both x and y) — impossible for an axis-aligned worldBounds() box.
        assertTrue(lines.any { it.from.x != it.to.x && it.from.y != it.to.y })
    }

    @Test
    fun `ShapeGizmoWidget disabled emits no draws`() {
        val area = Area2D().apply { transform = Transform(position = Vec2(50f, 50f)) }
        area.addChild(CollisionShape2D().apply { shape = CircleShape2D().apply { radius = 7f } })
        val tree = SceneTree(Node().apply { addChild(area) }).also { it.start() }
        // shapeGizmo defaults to disabled.
        val recorder = RecordingRenderer()
        tree.render(recorder)
        assertEquals(0, recorder.events.count { it is RecordedEvent.Circle })
    }

    // --- VelocityGizmoWidget ---

    @Test
    fun `VelocityGizmoWidget draws a velocity line for a moving rigid body`() {
        val rigid = RigidBody2D().apply {
            transform = Transform(position = Vec2(100f, 100f))
            linearVelocity = Vec2(200f, 0f)
        }
        rigid.addChild(CollisionShape2D().apply { shape = CircleShape2D().apply { radius = 5f } })
        val tree = SceneTree(Node().apply { addChild(rigid) }).also { it.start() }
        val widget = tree.debug.velocityGizmo
        widget.enabled = true
        widget.velocityScale = 0.1f

        val recorder = RecordingRenderer()
        widget.drawDebug(recorder)

        // Line from p to p + v*scale = (100,100) → (120,100).
        val main = recorder.events.filterIsInstance<RecordedEvent.Line>().firstOrNull {
            near(it.from.x, 100f) && near(it.from.y, 100f) && near(it.to.x, 120f) && near(it.to.y, 100f)
        }
        assertNotNull(main, "expected a p→p+v*s velocity line; got ${recorder.events}")
    }

    @Test
    fun `VelocityGizmoWidget draws nothing for a stationary body`() {
        val character = CharacterBody2D().apply {
            transform = Transform(position = Vec2(40f, 40f))
            velocity = Vec2.ZERO
        }
        character.addChild(CollisionShape2D().apply { shape = CircleShape2D().apply { radius = 5f } })
        val tree = SceneTree(Node().apply { addChild(character) }).also { it.start() }
        tree.debug.velocityGizmo.enabled = true

        val recorder = RecordingRenderer()
        tree.debug.velocityGizmo.drawDebug(recorder)
        assertEquals(0, recorder.events.count { it is RecordedEvent.Line })
    }

    // --- Contact buffer + PhysicsSystem seam ---

    @Test
    fun `contacts are not recorded while recording is off`() {
        val tree = collidingCirclesTree()
        // recording defaults to off.
        PhysicsSystem().step(tree, 0.5f)
        assertTrue(tree.debug.contacts.records.isEmpty())
    }

    @Test
    fun `contacts are recorded while recording is on`() {
        val tree = collidingCirclesTree()
        tree.debug.contacts.recording = true
        PhysicsSystem().step(tree, 0.5f)
        val records = tree.debug.contacts.records
        assertTrue(records.isNotEmpty(), "expected at least one resolved contact")
        // Normals come out unit-length from the solver's SweepResult.
        assertTrue(records.all { kotlin.math.abs(it.normal.length - 1f) < 1e-3f })
    }

    @Test
    fun `buffer is cleared at the start of each recording step`() {
        val tree = nonCollidingTree()
        tree.debug.contacts.recording = true
        // Stale entry from a hypothetical prior step.
        tree.debug.contacts.append(Vec2.ZERO, Vec2(1f, 0f))
        PhysicsSystem().step(tree, 1f / 60f)
        assertTrue(tree.debug.contacts.records.isEmpty(), "a step with no contacts must clear the buffer")
    }

    // --- ContactGizmoWidget ---

    @Test
    fun `ContactGizmoWidget draws a marker and a normal line per contact`() {
        val tree = SceneTree(Node()).also { it.start() }
        tree.debug.contacts.append(Vec2(10f, 20f), Vec2(0f, 1f))
        val widget = tree.debug.contactGizmo
        widget.enabled = true

        val recorder = RecordingRenderer()
        widget.drawDebug(recorder)

        val marker = recorder.events.filterIsInstance<RecordedEvent.Circle>().single()
        assertEquals(10f, marker.center.x, eps)
        assertEquals(20f, marker.center.y, eps)
        assertTrue(marker.filled)
        val normalLine = recorder.events.filterIsInstance<RecordedEvent.Line>().single()
        assertEquals(10f, normalLine.from.x, eps)
        assertEquals(20f, normalLine.from.y, eps)
        // Tip lies along the normal (0,1): same x, larger y.
        assertEquals(10f, normalLine.to.x, eps)
        assertTrue(normalLine.to.y > normalLine.from.y)
        assertEquals(0, recorder.events.count { it is RecordedEvent.Push || it is RecordedEvent.Pop })
    }

    @Test
    fun `enabling the ContactGizmoWidget turns recording on`() {
        val tree = collidingCirclesTree()
        assertTrue(tree.debug.contacts.records.isEmpty())
        tree.debug.contactGizmo.enabled = true
        PhysicsSystem().step(tree, 0.5f)
        assertTrue(tree.debug.contacts.records.isNotEmpty())
    }

    // --- Built-in registration ---

    @Test
    fun `the three physics gizmos are world-hosted built-ins present in the registry`() {
        val root = Node()
        val tree = SceneTree(root).also { it.start() }
        val container = (root.findChild(DebugLayer.NODE_NAME) as DebugLayer).worldContainer

        assertNotNull(tree.debug.shapeGizmo)
        assertNotNull(tree.debug.velocityGizmo)
        assertNotNull(tree.debug.contactGizmo)
        assertSame(container, tree.debug.shapeGizmo.parent)
        assertSame(container, tree.debug.velocityGizmo.parent)
        assertSame(container, tree.debug.contactGizmo.parent)
        assertTrue(tree.debug.shapeGizmo in tree.debug.widgets)
        assertTrue(tree.debug.velocityGizmo in tree.debug.widgets)
        assertTrue(tree.debug.contactGizmo in tree.debug.widgets)
    }

    private fun collidingCirclesTree(): SceneTree {
        val a = RigidBody2D().apply {
            transform = Transform(position = Vec2(0f, 0f))
            mass = 1f; restitution = 1f; friction = 0f
            linearVelocity = Vec2(100f, 0f)
            addChild(CollisionShape2D().apply { shape = CircleShape2D().apply { radius = 5f } })
        }
        val b = RigidBody2D().apply {
            transform = Transform(position = Vec2(20f, 0f))
            mass = 1f; restitution = 1f; friction = 0f
            addChild(CollisionShape2D().apply { shape = CircleShape2D().apply { radius = 5f } })
        }
        return SceneTree(Node().apply { addChild(a); addChild(b) }).also { it.start() }
    }

    private fun nonCollidingTree(): SceneTree {
        val a = RigidBody2D().apply {
            transform = Transform(position = Vec2(0f, 0f))
            addChild(CollisionShape2D().apply { shape = CircleShape2D().apply { radius = 5f } })
        }
        val b = RigidBody2D().apply {
            transform = Transform(position = Vec2(500f, 0f))
            addChild(CollisionShape2D().apply { shape = CircleShape2D().apply { radius = 5f } })
        }
        return SceneTree(Node().apply { addChild(a); addChild(b) }).also { it.start() }
    }
}
