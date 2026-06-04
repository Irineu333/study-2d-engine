package com.neoutils.engine.debug

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.FakeInput
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Screen widget with a controllable content extent and a no-op body draw. */
private class ScrollTestWidget(
    override val defaultSlot: DockSlot = DockSlot.TOP_LEFT,
    var extent: Vec2 = Vec2(200f, 1000f),
) : ScreenDebugWidget() {
    init { enabled = true }
    override val title: String = "Scroll"
    override fun bodySize(): Vec2 = extent
    override fun drawDebug(renderer: Renderer) {}
}

class ScreenDebugScrollTest {

    private fun startedTree(w: Float = 800f, h: Float = 600f): SceneTree =
        SceneTree(Node()).also { it.resize(w, h); it.start() }

    private fun docked(tree: SceneTree, widget: ScreenDebugWidget) {
        tree.debug.register(widget)
        tree.debug.dock.relayout(tree.size)
    }

    private fun center(r: Rect): Vec2 = Vec2((r.left + r.right) / 2f, (r.top + r.bottom) / 2f)

    /** The body's scroll translation this frame (the `-offset` push under the body clip). */
    private fun bodyOffset(tree: SceneTree, widget: ScreenDebugWidget): Float {
        val rec = RecordingRenderer()
        tree.render(rec)
        val events = rec.events
        for (i in events.indices) {
            val e = events[i]
            if (e is RecordedEvent.PushClip &&
                e.rect.left == widget.bodyOrigin.x && e.rect.top == widget.bodyOrigin.y
            ) {
                val next = events.getOrNull(i + 1)
                if (next is RecordedEvent.Push) return -next.translation.y
            }
        }
        return 0f
    }

    private fun grabberRect(tree: SceneTree, widget: ScreenDebugWidget): Rect? {
        val rec = RecordingRenderer()
        tree.render(rec)
        return rec.events.filterIsInstance<RecordedEvent.Rect>()
            .firstOrNull { it.filled && it.color == DebugTheme.scrollGrabberColor }
            ?.rect
    }

    private fun hasScrollbar(tree: SceneTree, widget: ScreenDebugWidget): Boolean {
        val rec = RecordingRenderer()
        tree.render(rec)
        return rec.events.filterIsInstance<RecordedEvent.Rect>()
            .any { it.filled && it.color == DebugTheme.scrollTrackColor }
    }

    @Test
    fun `offset clamps to content bounds`() {
        val tree = startedTree(h = 600f)
        val widget = ScrollTestWidget(extent = Vec2(200f, 1000f))
        docked(tree, widget)
        // maxBodyHeight = 600 - 24 - 20 = 556; scrollable = 1000 - 556 = 444.
        val pointer = Vec2(widget.bodyOrigin.x + 5f, widget.bodyOrigin.y + 10f)

        widget.applyScroll(pointer, 1000f) // way past the end
        assertEquals(444f, bodyOffset(tree, widget), 0.5f, "offset clamps to extent - viewport")

        widget.applyScroll(pointer, -1000f) // way before the start
        assertEquals(0f, bodyOffset(tree, widget), 0.5f, "offset clamps to zero")
    }

    @Test
    fun `resize re-clamps the offset with no special branch`() {
        val tree = startedTree(h = 600f)
        val widget = ScrollTestWidget(extent = Vec2(200f, 1000f))
        docked(tree, widget)
        val pointer = Vec2(widget.bodyOrigin.x + 5f, widget.bodyOrigin.y + 10f)
        widget.applyScroll(pointer, 1000f)
        assertTrue(bodyOffset(tree, widget) > 0f, "scrolled near the bottom")

        // Grow the window so the viewport now contains the whole content.
        tree.resize(800f, 1200f)
        tree.debug.dock.relayout(tree.size)
        assertEquals(0f, bodyOffset(tree, widget), 0.5f, "the offset re-clamps to zero — content fits")
        assertFalse(hasScrollbar(tree, widget), "no scrollbar once the content fits")
    }

    @Test
    fun `grabber is proportional to the visible fraction`() {
        val tree = startedTree(h = 600f)
        // maxBodyHeight = 556; choose extent so the viewport shows exactly half.
        val widget = ScrollTestWidget(extent = Vec2(200f, 1112f))
        docked(tree, widget)

        val grabber = grabberRect(tree, widget)
        assertTrue(grabber != null, "a scrollable panel draws a grabber")
        // viewport (556) is half the extent (1112) → grabber is half the track (556).
        assertEquals(278f, grabber!!.size.y, 1f, "grabber height is half the track")
    }

    @Test
    fun `no scrollbar when content fits`() {
        val tree = startedTree(h = 600f)
        val widget = ScrollTestWidget(extent = Vec2(200f, 100f))
        docked(tree, widget)
        assertFalse(hasScrollbar(tree, widget), "content within the viewport draws no scrollbar")
    }

    @Test
    fun `wheel over a scrollable panel scrolls it and is consumed`() {
        val tree = startedTree(h = 600f)
        val widget = ScrollTestWidget(extent = Vec2(200f, 1000f))
        docked(tree, widget)
        tree.render(RecordingRenderer())

        val input = FakeInput(
            pointer = Vec2(widget.bodyOrigin.x + 5f, widget.bodyOrigin.y + 10f),
            scrollDelta = Vec2(0f, 3f),
        )
        tree.input = input
        tree.hitTestUI(input)

        assertTrue(input.scrollConsumed, "the wheel is consumed by the panel under the pointer")
        assertTrue(bodyOffset(tree, widget) > 0f, "the panel scrolled down")
    }

    @Test
    fun `wheel outside any panel passes through unconsumed`() {
        val tree = startedTree(h = 600f)
        val widget = ScrollTestWidget(extent = Vec2(200f, 1000f))
        docked(tree, widget)
        tree.render(RecordingRenderer())

        // A pointer far from the panel (bottom-right corner, panel docks top-left).
        val input = FakeInput(pointer = Vec2(780f, 580f), scrollDelta = Vec2(0f, 3f))
        tree.input = input
        tree.hitTestUI(input)

        assertFalse(input.scrollConsumed, "no panel under the pointer — the wheel passes through")
        assertEquals(0f, bodyOffset(tree, widget), 0.5f, "the panel did not scroll")
    }

    @Test
    fun `dragging the grabber scrolls the content and consumes the drag`() {
        val tree = startedTree(h = 600f)
        val widget = ScrollTestWidget(extent = Vec2(200f, 1000f))
        docked(tree, widget)

        val grabber = grabberRect(tree, widget)!!
        val input = FakeInput(pointer = center(grabber), leftClicked = true, leftDown = true)
        tree.input = input
        tree.hitTestUI(input)
        tree.process(0f)
        assertTrue(widget.isScrollbarDragging, "pressing the grabber starts a scrollbar drag")

        // Hold and drag downward along the track.
        input.leftClicked = false
        input.pointer = center(grabber) + Vec2(0f, 120f)
        tree.hitTestUI(input)
        tree.process(0f)

        assertTrue(input.mouseDragConsumed, "a grabber drag consumes the drag")
        assertTrue(bodyOffset(tree, widget) > 0f, "the content scrolled down with the grabber")
    }
}
