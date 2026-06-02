package com.neoutils.engine.debug

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertTrue

/** Minimal screen widget with a fixed size/slot, for exercising the dock. */
private class FakeScreenWidget(
    override val defaultSlot: DockSlot,
    private val size: Vec2,
    override val title: String = "Fake",
) : ScreenDebugWidget() {
    init { enabled = true }
    override fun bodySize(): Vec2 = size
    override fun drawDebug(renderer: Renderer) {}
}

private fun rectOf(widget: ScreenDebugWidget): Pair<Vec2, Vec2> =
    widget.dockOrigin to widget.contentSize()

private fun overlaps(a: Pair<Vec2, Vec2>, b: Pair<Vec2, Vec2>): Boolean {
    val (ao, asz) = a
    val (bo, bsz) = b
    val sepX = ao.x + asz.x <= bo.x || bo.x + bsz.x <= ao.x
    val sepY = ao.y + asz.y <= bo.y || bo.y + bsz.y <= ao.y
    return !(sepX || sepY)
}

class DebugShellTest {

    private fun startedTree(w: Float = 800f, h: Float = 600f): SceneTree =
        SceneTree(Node()).also { it.resize(w, h); it.start() }

    @Test
    fun `two widgets in the same slot stack without overlapping`() {
        val tree = startedTree()
        val top = FakeScreenWidget(DockSlot.TOP_LEFT, Vec2(120f, 30f), "Top")
        val bottom = FakeScreenWidget(DockSlot.TOP_LEFT, Vec2(120f, 40f), "Bottom")
        tree.debug.register(top)
        tree.debug.register(bottom)

        tree.debug.dock.relayout(tree.size)

        assertTrue(!overlaps(rectOf(top), rectOf(bottom)), "same-slot widgets must not overlap")
        // Registration order stacks inward from the corner: first on top.
        assertTrue(
            top.dockOrigin.y + top.contentSize().y <= bottom.dockOrigin.y,
            "second TOP_LEFT widget sits below the first (top=${top.dockOrigin}, bottom=${bottom.dockOrigin})",
        )
    }

    @Test
    fun `bottom slot grows upward from the corner`() {
        val tree = startedTree()
        val first = FakeScreenWidget(DockSlot.BOTTOM_LEFT, Vec2(100f, 30f), "First")
        val second = FakeScreenWidget(DockSlot.BOTTOM_LEFT, Vec2(100f, 30f), "Second")
        tree.debug.register(first)
        tree.debug.register(second)

        tree.debug.dock.relayout(tree.size)

        assertTrue(!overlaps(rectOf(first), rectOf(second)))
        // First registered hugs the bottom; the second stacks above it.
        assertTrue(first.dockOrigin.y > second.dockOrigin.y, "first hugs the bottom edge")
    }

    @Test
    fun `relayout on resize keeps widgets inside the viewport and re-anchored`() {
        val tree = startedTree(800f, 600f)
        val widget = FakeScreenWidget(DockSlot.BOTTOM_RIGHT, Vec2(200f, 100f), "Panel")
        tree.debug.register(widget)

        tree.debug.dock.relayout(tree.size)
        val first = widget.dockOrigin

        tree.resize(400f, 300f)
        tree.debug.dock.relayout(tree.size)
        val second = widget.dockOrigin

        assertTrue(first != second, "the widget re-anchors when the surface shrinks")
        val size = widget.contentSize()
        assertTrue(second.x >= 0f && second.y >= 0f, "origin stays on-screen: $second")
        assertTrue(
            second.x + size.x <= 400f + 0.001f && second.y + size.y <= 300f + 0.001f,
            "widget stays within the viewport after resize: origin=$second size=$size",
        )
    }

    @Test
    fun `theme is applied so panel chrome is identical across widgets`() {
        val tree = startedTree()
        tree.debug.profiler.enabled = true
        tree.debug.timeControls.enabled = true
        tree.debug.frameProfile.apply {
            hitTestNanos = 1_000_000L
            physicsNanos = 1_000_000L
            processNanos = 1_000_000L
            renderNanos = 1_000_000L
            totalNanos = 4_000_000L
        }
        // Two ticks so the profiler samples the FrameProfile into its window.
        tree.process(0.016f)
        tree.process(0.016f)

        val recorder = RecordingRenderer()
        tree.render(recorder)
        val rects = recorder.events.filterIsInstance<RecordedEvent.Rect>()
        val fills = rects.filter { it.filled && it.color == DebugTheme.panelBackground }
        val borders = rects.filter { !it.filled && it.color == DebugTheme.panelBorderColor }
        // Profiler and Time each contribute one themed fill + one themed border.
        assertTrue(fills.size >= 2, "both panels share the theme background, got ${fills.size}")
        assertTrue(borders.size >= 2, "both panels share the theme border, got ${borders.size}")
    }
}
