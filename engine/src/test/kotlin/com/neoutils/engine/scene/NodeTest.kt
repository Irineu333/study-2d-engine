package com.neoutils.engine.scene

import com.neoutils.engine.math.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class Spy(name: String, val log: MutableList<String>) : Node() {
    init { this.name = name }
    override fun onEnter() { log += "enter:$name" }
    override fun onExit() { log += "exit:$name" }
}

class NodeTest {

    @Test
    fun `addChild attaches child to parent`() {
        val log = mutableListOf<String>()
        val parent = Spy("p", log)
        val child = Spy("c", log)
        parent.addChild(child)
        assertSame(parent, child.parent)
        assertTrue(parent.children.contains(child))
    }

    @Test
    fun `removeChild detaches child`() {
        val log = mutableListOf<String>()
        val parent = Spy("p", log)
        val child = Spy("c", log)
        parent.addChild(child)
        parent.removeChild(child)
        assertNull(child.parent)
        assertFalse(parent.children.contains(child))
    }

    @Test
    fun `adding to a non-live parent does not fire onEnter`() {
        val log = mutableListOf<String>()
        val parent = Spy("p", log)
        val child = Spy("c", log)
        parent.addChild(child)
        assertTrue(log.isEmpty())
    }

    @Test
    fun `scene start fires onEnter pre-order`() {
        val log = mutableListOf<String>()
        val scene = Scene()
        val a = Spy("a", log)
        val b = Spy("b", log)
        val c = Spy("c", log)
        scene.addChild(a)
        a.addChild(b)
        scene.addChild(c)
        scene.start()
        assertEquals(listOf("enter:a", "enter:b", "enter:c"), log)
    }

    @Test
    fun `adding to a live tree fires onEnter on new subtree`() {
        val log = mutableListOf<String>()
        val scene = Scene()
        scene.start()
        val a = Spy("a", log)
        val b = Spy("b", log)
        a.addChild(b)
        scene.addChild(a)
        assertEquals(listOf("enter:a", "enter:b"), log)
    }

    @Test
    fun `removing from live tree fires onExit post-order`() {
        val log = mutableListOf<String>()
        val scene = Scene()
        val a = Spy("a", log)
        val b = Spy("b", log)
        a.addChild(b)
        scene.addChild(a)
        scene.start()
        log.clear()
        scene.removeChild(a)
        assertEquals(listOf("exit:b", "exit:a"), log)
        assertFalse(a.isLive)
        assertFalse(b.isLive)
    }

    @Test
    fun `worldPosition sums ancestor transforms`() {
        val scene = Scene()
        val parent = Node2D().apply { transform = transform.copy(position = Vec2(100f, 50f)) }
        val child = Node2D().apply { transform = transform.copy(position = Vec2(5f, 7f)) }
        scene.addChild(parent)
        parent.addChild(child)
        assertEquals(Vec2(105f, 57f), child.worldPosition())
    }

    @Test
    fun `transform isolation between siblings`() {
        val scene = Scene()
        val left = Node2D()
        val right = Node2D()
        scene.addChild(left)
        scene.addChild(right)
        left.transform = left.transform.copy(position = Vec2(5f, 0f))
        assertEquals(Vec2(5f, 0f), left.transform.position)
        assertEquals(Vec2.ZERO, right.transform.position)
    }

    @Test
    fun `scene update propagates to all live nodes`() {
        val scene = Scene()
        var ticks = 0
        val node = object : Node() { override fun onUpdate(dt: Float) { ticks++ } }
        scene.addChild(node)
        scene.start()
        scene.update(0.016f)
        assertEquals(1, ticks)
    }

    @Test
    fun `addChild auto-suffixes on name conflict`() {
        val log = mutableListOf<String>()
        val parent = Spy("p", log)
        val first = Spy("Ball", log)
        val second = Spy("Ball", log)
        parent.addChild(first)
        parent.addChild(second)
        assertEquals("Ball", first.name)
        assertEquals("Ball_2", second.name)
    }

    @Test
    fun `addChild increments past existing suffixed siblings`() {
        val log = mutableListOf<String>()
        val parent = Spy("p", log)
        parent.addChild(Spy("Ball", log))
        parent.addChild(Spy("Ball", log))
        val third = Spy("Ball", log)
        parent.addChild(third)
        assertEquals("Ball_3", third.name)
    }

    @Test
    fun `addChild keeps name when no conflict`() {
        val log = mutableListOf<String>()
        val parent = Spy("p", log)
        val paddle = Spy("Paddle", log)
        parent.addChild(paddle)
        assertEquals("Paddle", paddle.name)
    }

    @Test
    fun `auto-suffixed name survives detach`() {
        val log = mutableListOf<String>()
        val parent = Spy("p", log)
        parent.addChild(Spy("Ball", log))
        val second = Spy("Ball", log)
        parent.addChild(second)
        parent.removeChild(second)
        assertEquals("Ball_2", second.name)
    }

    @Test
    fun `removing a child does not renumber siblings`() {
        val log = mutableListOf<String>()
        val parent = Spy("p", log)
        val first = Spy("Ball", log)
        val second = Spy("Ball", log)
        val third = Spy("Ball", log)
        parent.addChild(first)
        parent.addChild(second)
        parent.addChild(third)
        parent.removeChild(first)
        assertEquals("Ball_2", second.name)
        assertEquals("Ball_3", third.name)
    }

    @Test
    fun `findChild returns the matching child`() {
        val log = mutableListOf<String>()
        val parent = Spy("p", log)
        val b = Spy("B", log)
        parent.addChild(Spy("A", log))
        parent.addChild(b)
        parent.addChild(Spy("C", log))
        assertSame(b, parent.findChild("B"))
    }

    @Test
    fun `findChild returns null for missing name`() {
        val log = mutableListOf<String>()
        val parent = Spy("p", log)
        parent.addChild(Spy("A", log))
        parent.addChild(Spy("B", log))
        assertNull(parent.findChild("Z"))
    }

    @Test
    fun `findChild does not descend into grandchildren`() {
        val log = mutableListOf<String>()
        val parent = Spy("p", log)
        val a = Spy("A", log)
        a.addChild(Spy("Target", log))
        parent.addChild(a)
        assertNull(parent.findChild("Target"))
    }

    @Test
    fun `scene render visits parents before children in pre-order`() {
        val scene = Scene()
        val order = mutableListOf<String>()
        class R(val tag: String) : Node() {
            override fun onRender(renderer: com.neoutils.engine.render.Renderer) { order += tag }
        }
        val a = R("a")
        val b = R("b")
        val c = R("c")
        scene.addChild(a)
        a.addChild(b)
        scene.addChild(c)
        scene.start()
        scene.render(NoopRenderer)
        assertEquals(listOf("a", "b", "c"), order)
    }
}

private object NoopRenderer : com.neoutils.engine.render.Renderer {
    override fun clear(color: com.neoutils.engine.render.Color) {}
    override fun drawRect(rect: com.neoutils.engine.math.Rect, color: com.neoutils.engine.render.Color, filled: Boolean) {}
    override fun drawCircle(center: Vec2, radius: Float, color: com.neoutils.engine.render.Color, filled: Boolean, thickness: Float) {}
    override fun drawLine(from: Vec2, to: Vec2, thickness: Float, color: com.neoutils.engine.render.Color) {}
    override fun drawText(text: String, position: Vec2, size: Float, color: com.neoutils.engine.render.Color) {}
    override fun measureText(text: String, size: Float): Vec2 = Vec2.ZERO
}
