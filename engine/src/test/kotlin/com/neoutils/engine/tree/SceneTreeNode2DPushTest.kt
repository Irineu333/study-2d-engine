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

        assertEquals(3, recorder.events.size, "got ${recorder.events}")
        val push = recorder.events[0] as RecordedEvent.Push
        assertEquals(Vec2(50f, 70f), push.translation)
        assertEquals(0f, push.rotation)
        assertEquals(Vec2(1f, 1f), push.scale)

        val draw = recorder.events[1] as RecordedEvent.Rect
        assertEquals(Vec2.ZERO, draw.rect.origin)
        assertEquals(Vec2(20f, 10f), draw.rect.size)

        assertTrue(recorder.events[2] is RecordedEvent.Pop)
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

        val pushes = recorder.events.filterIsInstance<RecordedEvent.Push>()
        assertEquals(2, pushes.size)
        assertEquals(Vec2(100f, 0f), pushes[0].translation)
        assertEquals(Vec2(0f, 50f), pushes[1].translation)
        // Order: push(parent), push(child), draw(child), pop(child), pop(parent)
        val tags = recorder.events.map {
            when (it) {
                is RecordedEvent.Push -> "push"
                is RecordedEvent.Pop -> "pop"
                is RecordedEvent.Rect -> "rect"
                else -> "other"
            }
        }
        assertEquals(listOf("push", "push", "rect", "pop", "pop"), tags)
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

        val pushes = recorder.events.filterIsInstance<RecordedEvent.Push>()
        assertEquals(1, pushes.size, "only the Node2D parent should push; got ${recorder.events}")
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

        val push = recorder.events.filterIsInstance<RecordedEvent.Push>().single()
        assertEquals((PI / 2.0).toFloat(), push.rotation)

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
        // drawRect, pop(rect), pop(view). The camera node itself is a Node2D
        // and also produces a push.
        val tags = recorder.events.map {
            when (it) {
                is RecordedEvent.Push -> "push"
                is RecordedEvent.Pop -> "pop"
                is RecordedEvent.Rect -> "rect"
                else -> "other"
            }
        }
        assertEquals(listOf("push", "push", "pop", "push", "rect", "pop", "pop"), tags)
    }
}

private class ThrowingDraw : Node2D() {
    override fun onDraw(renderer: Renderer) {
        throw RuntimeException("boom")
    }
}
