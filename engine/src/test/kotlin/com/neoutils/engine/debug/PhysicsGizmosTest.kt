package com.neoutils.engine.debug

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.CharacterBody2D
import com.neoutils.engine.physics.CircleShape2D
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.PhysicsSystem
import com.neoutils.engine.physics.RigidBody2D
import com.neoutils.engine.physics.StaticBody2D
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PhysicsGizmosTest {

    private val eps = 1e-3f

    private fun near(a: Float, b: Float): Boolean = kotlin.math.abs(a - b) < eps

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
    fun `VelocityGizmoWidget anchors the arrow at the shape centroid, not the node origin`() {
        val rigid = RigidBody2D().apply {
            transform = Transform(position = Vec2(0f, 0f))
            linearVelocity = Vec2(100f, 0f)
        }
        // Circle centered at body-local (10, 0): node origin != shape center.
        rigid.addChild(CollisionShape2D().apply {
            transform = Transform(position = Vec2(10f, 0f))
            shape = CircleShape2D().apply { radius = 5f }
        })
        val tree = SceneTree(Node().apply { addChild(rigid) }).also { it.start() }
        val widget = tree.debug.velocityGizmo
        widget.enabled = true
        widget.velocityScale = 0.1f

        val recorder = RecordingRenderer()
        widget.drawDebug(recorder)

        // Arrow base at the shape center (10,0), NOT the node origin (0,0);
        // tip at (10,0) + v*scale = (10,0) + (10,0) = (20,0).
        val main = recorder.events.filterIsInstance<RecordedEvent.Line>().firstOrNull {
            near(it.from.x, 10f) && near(it.from.y, 0f) && near(it.to.x, 20f) && near(it.to.y, 0f)
        }
        assertNotNull(main, "arrow must start at the shape centroid; got ${recorder.events}")
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

    // --- Kinematic contact staging ---

    @Test
    fun `stage then takeStaged moves the pair into records and empties staging`() {
        val buffer = PhysicsContactBuffer()
        buffer.stage(Vec2(3f, 4f), Vec2(0f, 1f))
        buffer.takeStaged()
        assertEquals(1, buffer.records.size)
        assertEquals(Vec2(3f, 4f), buffer.records.single().point)
        assertEquals(Vec2(0f, 1f), buffer.records.single().normal)
        assertTrue(buffer.staged.isEmpty(), "takeStaged must empty the staging area")
    }

    @Test
    fun `the start-of-step clear plus takeStaged keeps only the staged pair`() {
        val buffer = PhysicsContactBuffer()
        // A record from a previous step plus a freshly staged kinematic contact.
        buffer.append(Vec2(99f, 99f), Vec2(1f, 0f))
        buffer.stage(Vec2(3f, 4f), Vec2(0f, 1f))
        // Mirror what PhysicsSystem.step does at the start when recording is on.
        buffer.clear()
        buffer.takeStaged()
        assertEquals(1, buffer.records.size)
        assertEquals(Vec2(3f, 4f), buffer.records.single().point)
        assertTrue(buffer.staged.isEmpty())
    }

    // --- moveAndCollide stages the kinematic contact ---

    @Test
    fun `moveAndCollide stages the contact only while recording is on`() {
        val (tree, character) = kinematicVsStaticTree()
        // recording off: a colliding move stages nothing.
        assertNotNull(character.moveAndCollide(Vec2(100f, 0f)))
        assertTrue(tree.debug.contacts.staged.isEmpty())

        // Reset the body and enable recording: the next colliding move stages one pair.
        character.position = Vec2(0f, 0f)
        tree.debug.contacts.recording = true
        val collision = character.moveAndCollide(Vec2(100f, 0f))
        assertNotNull(collision)
        val staged = tree.debug.contacts.staged.single()
        assertEquals(collision.point, staged.point)
        assertEquals(collision.normal, staged.normal)
    }

    @Test
    fun `a moveAndCollide miss stages nothing`() {
        val (tree, character) = kinematicVsStaticTree()
        tree.debug.contacts.recording = true
        // Move away from the static body: no contact.
        assertEquals(null, character.moveAndCollide(Vec2(-100f, 0f)))
        assertTrue(tree.debug.contacts.staged.isEmpty())
    }

    // --- step folds staged kinematic contacts alongside rigid ones ---

    @Test
    fun `kinematic and rigid contacts coexist in records after a step`() {
        val tree = collidingCirclesTree()
        tree.debug.contacts.recording = true
        // Simulate a moveAndCollide having staged a kinematic contact this substep.
        tree.debug.contacts.stage(Vec2(7f, 8f), Vec2(0f, 1f))
        PhysicsSystem().step(tree, 0.5f)
        val records = tree.debug.contacts.records
        assertTrue(records.any { it.point == Vec2(7f, 8f) }, "the staged kinematic contact must survive the step")
        assertTrue(records.size > 1, "rigid contacts must also be present")
        assertTrue(tree.debug.contacts.staged.isEmpty())
    }

    @Test
    fun `a purely kinematic substep populates records via the fold`() {
        val (tree, character) = kinematicVsStaticTree()
        tree.debug.contacts.recording = true
        // _physics_process: the script resolves a kinematic contact.
        assertNotNull(character.moveAndCollide(Vec2(100f, 0f)))
        // step: no RigidBody2D, but the staged contact folds into records.
        PhysicsSystem().step(tree, 1f / 60f)
        assertEquals(1, tree.debug.contacts.records.size)
        assertTrue(tree.debug.contacts.staged.isEmpty())
    }

    @Test
    fun `a substep with no contacts leaves records empty after the fold`() {
        val tree = nonCollidingTree()
        tree.debug.contacts.recording = true
        tree.debug.contacts.append(Vec2.ZERO, Vec2(1f, 0f))
        PhysicsSystem().step(tree, 1f / 60f)
        assertTrue(tree.debug.contacts.records.isEmpty())
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
    fun `the two physics gizmos are world-hosted built-ins present in the registry`() {
        val root = Node()
        val tree = SceneTree(root).also { it.start() }
        val container = (root.findChild(DebugLayer.NODE_NAME) as DebugLayer).worldContainer

        assertNotNull(tree.debug.velocityGizmo)
        assertNotNull(tree.debug.contactGizmo)
        assertSame(container, tree.debug.velocityGizmo.parent)
        assertSame(container, tree.debug.contactGizmo.parent)
        assertTrue(tree.debug.velocityGizmo in tree.debug.widgets)
        assertTrue(tree.debug.contactGizmo in tree.debug.widgets)
    }

    // The Pong case: a CharacterBody2D resolving a contact via moveAndCollide
    // in _physics_process, then a step with no RigidBody2D, populates records.
    @Test
    fun `the Pong case - kinematic body hitting a static wall populates records`() {
        val (tree, character) = kinematicVsStaticTree()
        tree.debug.contactGizmo.enabled = true // enabling mirrors recording on
        // _physics_process emulation.
        assertNotNull(character.moveAndCollide(Vec2(100f, 0f)))
        // Followed by the substep's step.
        PhysicsSystem().step(tree, 1f / 60f)
        assertTrue(tree.debug.contacts.records.isNotEmpty(), "the kinematic contact must reach the gizmo buffer")
    }

    private fun kinematicVsStaticTree(): Pair<SceneTree, CharacterBody2D> {
        val character = CharacterBody2D().apply {
            transform = Transform(position = Vec2(0f, 0f))
            addChild(CollisionShape2D().apply { shape = CircleShape2D().apply { radius = 5f } })
        }
        val wall = StaticBody2D().apply {
            transform = Transform(position = Vec2(20f, 0f))
            addChild(CollisionShape2D().apply { shape = CircleShape2D().apply { radius = 5f } })
        }
        val tree = SceneTree(Node().apply { addChild(character); addChild(wall) }).also { it.start() }
        return tree to character
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
