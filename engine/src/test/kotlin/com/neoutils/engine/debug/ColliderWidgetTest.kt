package com.neoutils.engine.debug

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.Area2D
import com.neoutils.engine.physics.CircleShape2D
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.physics.RigidBody2D
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Panel
import com.neoutils.engine.tree.SceneTree
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `AABB mode draws the broad-phase rect and no real geometry for a rectangle`() {
        val body = RigidBody2D().apply { transform = Transform(position = Vec2(100f, 80f)) }
        body.addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { size = Vec2(30f, 30f) } })
        val tree = SceneTree(Node().apply { addChild(body) }).also { it.start() }
        tree.debug.colliders.apply { mode = ColliderDrawMode.AABB; enabled = true }

        val recorder = RecordingRenderer()
        tree.debug.colliders.drawDebug(recorder)

        assertEquals(1, recorder.events.count { it is RecordedEvent.Rect && !it.filled })
        assertEquals(0, recorder.events.count { it is RecordedEvent.Line }, "AABB must not draw the real quad")
    }

    // --- ColliderModePanel: the screen-space control surface ---

    @Test
    fun `the mode panel is hidden until the collider gizmo is enabled`() {
        val tree = SceneTree(Node()).also { it.resize(800f, 600f); it.start() }
        val panel = tree.debug.colliderModePanel
        // colliders off (default): the panel mirrors its enabled and draws nothing.
        tree.process(0f)
        assertTrue(!panel.enabled, "panel follows colliders.enabled")
        assertEquals(0, panel.children.filterIsInstance<Panel>().size)

        tree.debug.colliders.enabled = true
        assertTrue(panel.enabled, "enabling colliders shows the panel")
        tree.process(0f)
        tree.applyPending()
        assertEquals(1, panel.children.filterIsInstance<Panel>().size)
    }

    @Test
    fun `clicking a segment sets the collider mode`() {
        val tree = SceneTree(Node()).also { it.resize(800f, 600f); it.start() }
        tree.debug.colliders.enabled = true
        tree.process(0f)
        tree.applyPending()
        val buttons = segmentButtons(tree)
        assertEquals(2, buttons.size, "one segment per ColliderDrawMode")

        buttons.single { it.text == "AABB" }.pressed.emit(Unit)
        assertEquals(ColliderDrawMode.AABB, tree.debug.colliders.mode)
        buttons.single { it.text == "REAL" }.pressed.emit(Unit)
        assertEquals(ColliderDrawMode.REAL, tree.debug.colliders.mode)
    }

    @Test
    fun `the active segment is highlighted`() {
        val tree = SceneTree(Node()).also { it.resize(800f, 600f); it.start() }
        tree.debug.colliders.apply { mode = ColliderDrawMode.REAL; enabled = true }
        tree.process(0f)
        tree.applyPending()
        tree.process(0f) // a tick so refreshSegments recolors
        val buttons = segmentButtons(tree)
        val real = buttons.single { it.text == "REAL" }
        val aabb = buttons.single { it.text == "AABB" }
        assertTrue(real.normalColor != aabb.normalColor, "the active segment reads differently from the inactive ones")
    }

    @Test
    fun `closing the panel disables the collider gizmo`() {
        val tree = SceneTree(Node()).also { it.resize(800f, 600f); it.start() }
        tree.debug.colliders.enabled = true
        // The panel's [x] sets enabled = false, which proxies back to colliders.
        tree.debug.colliderModePanel.enabled = false
        assertTrue(!tree.debug.colliders.enabled, "closing the panel turns the gizmo off")
    }

    private fun segmentButtons(tree: SceneTree): List<Button> =
        tree.debug.colliderModePanel.children
            .filterIsInstance<Panel>()
            .single()
            .children
            .filterIsInstance<Button>()
}
