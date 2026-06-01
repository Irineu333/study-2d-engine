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
    fun `FpsWidget emits no draw calls when disabled`() {
        val tree = SceneTree(Node())
        tree.start()
        tree.debug.fps.enabled = false
        val recorder = RecordingRenderer()
        tree.render(recorder)
        assertEquals(0, recorder.events.count { it is RecordedEvent.Text })
    }

    @Test
    fun `FpsWidget enabled draws an fps text`() {
        val tree = SceneTree(Node())
        tree.start()
        tree.debug.fps.enabled = true
        // Two onProcess ticks so the FpsCounter has at least two samples and
        // can compute a non-zero rate.
        tree.process(0.016f)
        tree.process(0.016f)
        val recorder = RecordingRenderer()
        tree.render(recorder)
        // Texts: the panel title-bar ("FPS") plus the body readout.
        val body = recorder.events.filterIsInstance<RecordedEvent.Text>()
            .single { it.text.startsWith("fps ") }
        assertTrue(body.text.startsWith("fps "), "got '${body.text}'")
    }

    @Test
    fun `ColliderWidget draws one rect per active CollisionShape2D`() {
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
        tree.debug.colliders.enabled = true

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

    @Test
    fun `MomentumWidget disabled produces no draw calls`() {
        val tree = SceneTree(Node())
        tree.start()
        tree.debug.momentum.enabled = false
        // Even after physicsProcess ticks, no samples should be recorded.
        tree.physicsProcess(0.016f)
        tree.physicsProcess(0.016f)
        val recorder = RecordingRenderer()
        tree.render(recorder)
        assertEquals(0, recorder.events.count { it is RecordedEvent.Text })
        assertEquals(0, recorder.events.count { it is RecordedEvent.Line })
    }

    @Test
    fun `MomentumWidget enabled records on physicsProcess`() {
        val rigid = RigidBody2D().apply {
            mass = 2f
            linearVelocity = Vec2(3f, 0f)
        }
        rigid.addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { size = Vec2(10f, 10f) } })
        val root = Node().apply { addChild(rigid) }
        val tree = SceneTree(root).also {
            it.resize(800f, 600f)
            it.physicsSystem = PhysicsSystem()
            it.start()
        }
        tree.debug.momentum.enabled = true
        // Two physics ticks to populate the ring buffer.
        tree.physicsProcess(0.016f)
        tree.physicsProcess(0.016f)
        val recorder = RecordingRenderer()
        tree.render(recorder)
        // Three readout lines (Σp, ΣL, ΣKE) when samples exist, plus the
        // title-bar text drawn by the shared panel chrome.
        val readouts = recorder.events.filterIsInstance<RecordedEvent.Text>()
            .count { it.text.startsWith("Σ") }
        assertEquals(3, readouts)
    }

    @Test
    fun `MomentumWidget reset on enable flip clears previous samples`() {
        val rigid = RigidBody2D().apply {
            mass = 1f
            linearVelocity = Vec2(5f, 0f)
        }
        rigid.addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { size = Vec2(10f, 10f) } })
        val root = Node().apply { addChild(rigid) }
        val tree = SceneTree(root).also {
            it.resize(800f, 600f)
            it.physicsSystem = PhysicsSystem()
            it.start()
        }
        tree.debug.momentum.enabled = true
        // Fill some samples.
        repeat(5) { tree.physicsProcess(0.016f) }
        // Toggle off then on — buffer should reset.
        tree.debug.momentum.enabled = false
        tree.debug.momentum.enabled = true
        // No physicsProcess between flip and draw — buffer is empty.
        val recorder = RecordingRenderer()
        tree.render(recorder)
        // With size == 0 the widget emits nothing.
        assertEquals(0, recorder.events.count { it is RecordedEvent.Text })
        assertEquals(0, recorder.events.count { it is RecordedEvent.Line })
    }
}

@Suppress("UNUSED")
private val unused: Rect = Rect(Vec2.ZERO, Vec2.ZERO)
