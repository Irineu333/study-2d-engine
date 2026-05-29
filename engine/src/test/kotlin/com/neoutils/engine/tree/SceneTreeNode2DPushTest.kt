package com.neoutils.engine.tree

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.AspectMode
import com.neoutils.engine.scene.Camera2D
import com.neoutils.engine.scene.ColorRect
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.scene.Polygon2D
import com.neoutils.engine.scene.Timer
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SceneTreeNode2DPushTest {

    @Test
    fun `single ColorRect produces one push, one draw, one pop`() {
        val rect = ColorRect().apply {
            transform = Transform(position = Vec2(50f, 70f))
            size = Vec2(20f, 10f)
        }
        val tree = SceneTree(rect)
        tree.start()

        val recorder = RecordingRenderer()
        tree.render(recorder)

        // Outer pair belongs to the rect (which is root); the inner three
        // pairs are the auto-inserted `WorldDebugContainer` and its
        // `ImmediateWorldDrawNode` + `ColliderWidget` (all Node2D, identity).
        val pushes = recorder.events.filterIsInstance<RecordedEvent.Push>()
        val pops = recorder.events.filterIsInstance<RecordedEvent.Pop>()
        assertEquals(4, pushes.size, "got ${recorder.events}")
        assertEquals(4, pops.size, "got ${recorder.events}")

        val outerPush = pushes[0]
        assertEquals(Vec2(50f, 70f), outerPush.translation)
        assertEquals(0f, outerPush.rotation)
        assertEquals(Vec2(1f, 1f), outerPush.scale)

        val draw = recorder.events.filterIsInstance<RecordedEvent.Rect>().single()
        assertEquals(Vec2.ZERO, draw.rect.origin)
        assertEquals(Vec2(20f, 10f), draw.rect.size)

        assertTrue(recorder.events.last() is RecordedEvent.Pop)
    }

    @Test
    fun `nested Node2D pushes compose via the renderer stack`() {
        val parent = Node2D().apply { transform = Transform(position = Vec2(100f, 0f)) }
        val child = ColorRect().apply {
            transform = Transform(position = Vec2(0f, 50f))
            size = Vec2(10f, 10f)
        }
        parent.addChild(child)
        val tree = SceneTree(parent)
        tree.start()

        val recorder = RecordingRenderer()
        tree.render(recorder)

        // Pushes: parent + child + WorldDebugContainer (Node2D, identity)
        // + ImmediateWorldDrawNode (Node2D, identity) + ColliderWidget.
        val pushes = recorder.events.filterIsInstance<RecordedEvent.Push>()
        assertEquals(5, pushes.size)
        assertEquals(Vec2(100f, 0f), pushes[0].translation)
        assertEquals(Vec2(0f, 50f), pushes[1].translation)
        // Order: push(parent), push(child), draw(child), pop(child),
        // push(WC), push(immediate), pop(immediate), push(collider),
        // pop(collider), pop(WC), pop(parent).
        val tags = recorder.events.map {
            when (it) {
                is RecordedEvent.Push -> "push"
                is RecordedEvent.Pop -> "pop"
                is RecordedEvent.Rect -> "rect"
                else -> "other"
            }
        }
        assertEquals(
            listOf(
                "push", "push", "rect", "pop",
                "push", "push", "pop", "push", "pop", "pop", "pop",
            ),
            tags,
        )
    }

    @Test
    fun `Timer child of a Node2D does NOT produce its own push`() {
        val parent = Node2D().apply { transform = Transform(position = Vec2(10f, 10f)) }
        val timer = Timer().apply { name = "tick" }
        parent.addChild(timer)
        val tree = SceneTree(parent)
        tree.start()

        val recorder = RecordingRenderer()
        tree.render(recorder)

        // Parent's push + WorldDebugContainer + ImmediateWorldDrawNode +
        // ColliderWidget (all Node2D, identity) auto-inserted by the engine;
        // the Timer (plain Node) contributes none.
        val pushes = recorder.events.filterIsInstance<RecordedEvent.Push>()
        assertEquals(4, pushes.size, "got ${recorder.events}")
        assertEquals(Vec2(10f, 10f), pushes[0].translation)
        assertEquals(Vec2.ZERO, pushes[1].translation)
        assertEquals(Vec2.ZERO, pushes[2].translation)
        assertEquals(Vec2.ZERO, pushes[3].translation)
    }

    @Test
    fun `Polygon2D with rotation pushes the rotation and draws local-space points`() {
        val points = listOf(Vec2(0f, 0f), Vec2(10f, 0f), Vec2(0f, 10f))
        val poly = Polygon2D().apply {
            transform = Transform(rotation = (PI / 2.0).toFloat())
            this.points = points
        }
        val tree = SceneTree(poly)
        tree.start()

        val recorder = RecordingRenderer()
        tree.render(recorder)

        // Polygon push + WorldDebugContainer + ImmediateWorldDrawNode +
        // ColliderWidget (identity) auto-insertion.
        val pushes = recorder.events.filterIsInstance<RecordedEvent.Push>()
        assertEquals(4, pushes.size)
        assertEquals((PI / 2.0).toFloat(), pushes[0].rotation)

        val draw = recorder.events.filterIsInstance<RecordedEvent.Polygon>().single()
        assertEquals(points, draw.points)
    }

    @Test
    fun `exception inside onDraw still triggers popTransform`() {
        val throwing = ThrowingDraw().apply { transform = Transform(position = Vec2(1f, 2f)) }
        val tree = SceneTree(throwing)
        tree.start()

        val recorder = RecordingRenderer()
        assertFailsWith<RuntimeException> { tree.render(recorder) }

        val pushes = recorder.events.count { it is RecordedEvent.Push }
        val pops = recorder.events.count { it is RecordedEvent.Pop }
        assertEquals(pushes, pops, "push/pop must balance even when onDraw throws; got ${recorder.events}")
    }

    @Test
    fun `Camera2D view push wraps per-Node2D pushes inside it`() {
        val root = Node()
        val camera = Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))
            current = true
            aspectMode = AspectMode.FIT
        }
        val rect = ColorRect().apply { size = Vec2(10f, 10f) }
        root.addChild(camera)
        root.addChild(rect)
        val tree = SceneTree(root)
        tree.resize(800f, 600f)
        tree.start()

        val recorder = RecordingRenderer()
        tree.render(recorder)

        // Sequence: push(view), push(camera), pop(camera), push(rect),
        // drawRect, pop(rect), push(WC), push(immediate), pop(immediate),
        // push(collider), pop(collider), pop(WC), pop(view). The camera node
        // itself is a Node2D and produces a push; the engine-inserted
        // `WorldDebugContainer` with its `ImmediateWorldDrawNode` and
        // `ColliderWidget` are Node2Ds too and add identity pushes inside the
        // camera's view transform.
        val tags = recorder.events.map {
            when (it) {
                is RecordedEvent.Push -> "push"
                is RecordedEvent.Pop -> "pop"
                is RecordedEvent.Rect -> "rect"
                else -> "other"
            }
        }
        assertEquals(
            listOf(
                "push", "push", "pop", "push", "rect", "pop",
                "push", "push", "pop", "push", "pop", "pop", "pop",
            ),
            tags,
        )
    }
}

private class ThrowingDraw : Node2D() {
    override fun onDraw(renderer: Renderer) {
        throw RuntimeException("boom")
    }
}
