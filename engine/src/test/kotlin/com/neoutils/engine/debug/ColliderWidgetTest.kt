package com.neoutils.engine.debug

import com.neoutils.engine.input.Input
import com.neoutils.engine.input.Key
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.Area2D
import com.neoutils.engine.physics.CircleShape2D
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.physics.RigidBody2D
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class ColliderKeyInput(var pressedKey: Key? = null) : Input {
    override val pointerPosition: Vec2 get() = Vec2.ZERO
    override var mouseClickConsumed: Boolean = false
    override fun isKeyDown(key: Key): Boolean = false
    override fun wasKeyPressed(key: Key): Boolean = pressedKey == key
    override fun isMouseDown(button: MouseButton): Boolean = false
    override fun wasMouseClickedRaw(button: MouseButton): Boolean = false
}

class ColliderWidgetTest {

    private val eps = 1e-3f

    @Test
    fun `default mode is REAL`() {
        val tree = SceneTree(Node()).also { it.start() }
        assertEquals(ColliderDrawMode.REAL, tree.debug.colliders.mode)
    }

    @Test
    fun `REAL mode draws a circle outline at the world center`() {
        val area = Area2D().apply { transform = Transform(position = Vec2(50f, 50f)) }
        area.addChild(CollisionShape2D().apply { shape = CircleShape2D().apply { radius = 7f } })
        val tree = SceneTree(Node().apply { addChild(area) }).also { it.start() }
        tree.debug.colliders.apply { mode = ColliderDrawMode.REAL; enabled = true }

        val recorder = RecordingRenderer()
        tree.debug.colliders.drawDebug(recorder)

        val circle = recorder.events.filterIsInstance<RecordedEvent.Circle>().single()
        assertEquals(50f, circle.center.x, eps)
        assertEquals(50f, circle.center.y, eps)
        assertEquals(7f, circle.radius, eps)
        assertTrue(!circle.filled, "real circle must be a non-filled outline")
        // No transform bookkeeping inside the widget — the world pass owns it.
        assertEquals(0, recorder.events.count { it is RecordedEvent.Push || it is RecordedEvent.Pop })
    }

    @Test
    fun `REAL mode draws a rotated rectangle as a non-axis-aligned quad`() {
        val body = RigidBody2D().apply {
            transform = Transform(position = Vec2(100f, 80f), rotation = (PI / 6).toFloat())
        }
        body.addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { size = Vec2(30f, 30f) } })
        val tree = SceneTree(Node().apply { addChild(body) }).also { it.start() }
        tree.debug.colliders.apply { mode = ColliderDrawMode.REAL; enabled = true }

        val recorder = RecordingRenderer()
        tree.debug.colliders.drawDebug(recorder)

        val lines = recorder.events.filterIsInstance<RecordedEvent.Line>()
        assertEquals(4, lines.size, "a rectangle outlines as four edges")
        // A rotated quad has at least one diagonal edge — impossible for an AABB.
        assertTrue(lines.any { it.from.x != it.to.x && it.from.y != it.to.y })
        // REAL mode must not draw the broad-phase rect.
        assertEquals(0, recorder.events.count { it is RecordedEvent.Rect })
    }

    @Test
    fun `AABB mode draws one rect per active shape and no real geometry`() {
        val area = Area2D().apply { transform = Transform(position = Vec2(50f, 50f)) }
        area.addChild(CollisionShape2D().apply { shape = CircleShape2D().apply { radius = 7f } })
        val tree = SceneTree(Node().apply { addChild(area) }).also { it.start() }
        tree.debug.colliders.apply { mode = ColliderDrawMode.AABB; enabled = true }

        val recorder = RecordingRenderer()
        tree.debug.colliders.drawDebug(recorder)

        assertEquals(1, recorder.events.count { it is RecordedEvent.Rect && !it.filled })
        assertEquals(0, recorder.events.count { it is RecordedEvent.Circle })
    }

    @Test
    fun `BOTH mode draws the AABB before the real geometry`() {
        val body = RigidBody2D().apply { transform = Transform(position = Vec2(100f, 80f)) }
        body.addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { size = Vec2(30f, 30f) } })
        val tree = SceneTree(Node().apply { addChild(body) }).also { it.start() }
        tree.debug.colliders.apply { mode = ColliderDrawMode.BOTH; enabled = true }

        val recorder = RecordingRenderer()
        tree.debug.colliders.drawDebug(recorder)

        val firstRect = recorder.events.indexOfFirst { it is RecordedEvent.Rect && !it.filled }
        val firstLine = recorder.events.indexOfFirst { it is RecordedEvent.Line }
        assertTrue(firstRect >= 0, "BOTH must draw the broad-phase rect")
        assertEquals(4, recorder.events.count { it is RecordedEvent.Line }, "BOTH must draw the real quad")
        assertTrue(firstRect < firstLine, "the AABB must be drawn before the real geometry")
    }

    @Test
    fun `the shortcut node cycles the mode while the collider gizmo is enabled`() {
        val input = ColliderKeyInput()
        val tree = SceneTree(Node()).also {
            it.start()
            it.input = input
        }
        tree.debug.colliders.enabled = true
        assertEquals(ColliderDrawMode.REAL, tree.debug.colliders.mode)

        input.pressedKey = Key.C
        tree.process(0f)
        assertEquals(ColliderDrawMode.BOTH, tree.debug.colliders.mode)
        tree.process(0f)
        assertEquals(ColliderDrawMode.AABB, tree.debug.colliders.mode)
        tree.process(0f)
        assertEquals(ColliderDrawMode.REAL, tree.debug.colliders.mode, "cycles back to REAL")
    }

    @Test
    fun `the shortcut node is inert while the collider gizmo is disabled`() {
        val input = ColliderKeyInput(pressedKey = Key.C)
        val tree = SceneTree(Node()).also {
            it.start()
            it.input = input
        }
        // colliders left disabled (the production default).
        tree.process(0f)
        assertEquals(ColliderDrawMode.REAL, tree.debug.colliders.mode, "mode must not cycle when the gizmo is off")
    }
}
