package com.neoutils.engine.debug

import com.neoutils.engine.input.Input
import com.neoutils.engine.input.Key
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fixed-size screen widget for exercising the dynamic dock. */
private class DockWidget(
    override val defaultSlot: DockSlot,
    private val size: Vec2 = Vec2(200f, 100f),
    override val title: String = "Dock",
) : ScreenDebugWidget() {
    init { enabled = true }
    override fun bodySize(): Vec2 = size
    override fun drawDebug(renderer: Renderer) {}
}

private class DockInput(
    var pointer: Vec2 = Vec2.ZERO,
    var clicked: Boolean = false,
    var down: Boolean = false,
) : Input {
    override val pointerPosition: Vec2 get() = pointer
    override var mouseClickConsumed: Boolean = false
    override var mouseDragConsumed: Boolean = false
    override fun isKeyDown(key: Key): Boolean = false
    override fun wasKeyPressed(key: Key): Boolean = false
    override fun isMouseDown(button: MouseButton): Boolean = button == MouseButton.Left && down
    override fun wasMouseClickedRaw(button: MouseButton): Boolean = button == MouseButton.Left && clicked
}

class DebugDynamicDockTest {

    private fun startedTree(w: Float = 800f, h: Float = 600f): SceneTree =
        SceneTree(Node()).also { it.resize(w, h); it.start() }

    private fun tick(tree: SceneTree, input: DockInput) {
        tree.input = input
        tree.hitTestUI(input)
        tree.process(0f)
    }

    private fun register(tree: SceneTree, vararg widgets: DockWidget) {
        for (w in widgets) tree.debug.register(w)
        tree.debug.dock.relayout(tree.size)
    }

    /** Drags [widget] by its header and releases the pointer at [releaseAt]. */
    private fun dragAndRelease(tree: SceneTree, widget: DockWidget, releaseAt: Vec2) {
        val grab = widget.origin + Vec2(widget.contentSize().x / 2f, DebugTheme.headerHeight / 2f)
        val input = DockInput(pointer = grab, clicked = true, down = true)
        tick(tree, input) // press starts the drag
        input.clicked = false
        input.pointer = releaseAt
        tick(tree, input) // move while held
        input.down = false
        tick(tree, input) // release resolves the drop target
    }

    // ---- Drop-target resolution (2.x) --------------------------------------

    @Test
    fun `each band third resolves to its slot and the miolo floats`() {
        val tree = startedTree()
        tree.debug.dock.relayout(tree.size)
        val dock = tree.debug.dock

        assertEquals(DropTarget.Dock(DockSlot.TOP_LEFT, 0), dock.resolveDropTarget(Vec2(50f, 10f)))
        assertEquals(DropTarget.Dock(DockSlot.TOP_CENTER, 0), dock.resolveDropTarget(Vec2(400f, 10f)))
        assertEquals(DropTarget.Dock(DockSlot.TOP_RIGHT, 0), dock.resolveDropTarget(Vec2(700f, 10f)))
        assertEquals(DropTarget.Dock(DockSlot.BOTTOM_LEFT, 0), dock.resolveDropTarget(Vec2(50f, 590f)))
        assertEquals(DropTarget.Dock(DockSlot.BOTTOM_CENTER, 0), dock.resolveDropTarget(Vec2(400f, 590f)))
        assertEquals(DropTarget.Dock(DockSlot.BOTTOM_RIGHT, 0), dock.resolveDropTarget(Vec2(700f, 590f)))
        assertEquals(DropTarget.Floating, dock.resolveDropTarget(Vec2(400f, 300f)))
    }

    @Test
    fun `band-miolo and third-third boundaries resolve on the expected side`() {
        val tree = startedTree()
        tree.debug.dock.relayout(tree.size)
        val dock = tree.debug.dock
        val band = DebugTheme.dockBandThickness

        // Band ↔ miolo: y < band docks, y == band floats.
        assertIs<DropTarget.Dock>(dock.resolveDropTarget(Vec2(50f, band - 1f)))
        assertEquals(DropTarget.Floating, dock.resolveDropTarget(Vec2(50f, band)))

        // Third ↔ third on the top band, at the 1/3 seam (≈266.67).
        val seam = 800f / 3f
        assertEquals(DockSlot.TOP_LEFT, (dock.resolveDropTarget(Vec2(seam - 1f, 10f)) as DropTarget.Dock).slot)
        assertEquals(DockSlot.TOP_CENTER, (dock.resolveDropTarget(Vec2(seam + 1f, 10f)) as DropTarget.Dock).slot)
    }

    @Test
    fun `magnetism uses the whole window - an edge reaching a band docks while the header stays in the miolo`() {
        val tree = startedTree()
        tree.debug.dock.relayout(tree.size)
        val dock = tree.debug.dock
        val band = DebugTheme.dockBandThickness // 96 on a 600-tall surface → miolo is [96, 504]

        // A panel whose top edge (the grabbed header) is in the miolo but whose
        // bottom edge dips into the bottom band: it docks bottom.
        val bottomReaching = Rect(Vec2(50f, 470f), Vec2(200f, 120f)) // bottom = 590 > 504
        assertEquals(DropTarget.Dock(DockSlot.BOTTOM_LEFT, 0), dock.resolveDropTarget(bottomReaching))

        // A panel fully inside the miolo (no edge in any band) floats.
        val fullyInside = Rect(Vec2(50f, 250f), Vec2(200f, 120f)) // [250, 370] ⊂ miolo
        assertEquals(DropTarget.Floating, dock.resolveDropTarget(fullyInside))
    }

    @Test
    fun `the magnetic zone follows the slot's occupied stack`() {
        val tree = startedTree()
        val a = DockWidget(DockSlot.TOP_LEFT, title = "A")
        val b = DockWidget(DockSlot.TOP_LEFT, title = "B")
        register(tree, a, b)
        val dock = tree.debug.dock

        // a occupies [12, 132] — already past the base band (96). Pretend b is the
        // dragged panel, so the stack to dock against is just a.
        dock.beginDrag(b)
        dock.relayout(tree.size)

        // b dropped well below a (a band's worth past a's bottom, far beyond the
        // fixed 96px band): it still docks below a — the catch zone is band-thick
        // past the stack, so the pull is as strong as it was for the first panel.
        val belowA = Rect(Vec2(50f, 200f), Vec2(200f, 120f))
        assertEquals(DropTarget.Dock(DockSlot.TOP_LEFT, 1), dock.resolveDropTarget(belowA))

        // The same rect in an empty slot's third (no occupied stack) floats: an
        // empty slot keeps the base band measured from the edge.
        val emptyThird = Rect(Vec2(400f, 200f), Vec2(200f, 120f))
        assertEquals(DropTarget.Floating, dock.resolveDropTarget(emptyThird))

        dock.endDrag(b)
    }

    @Test
    fun `insertion index follows the pointer past a stacked panel`() {
        val tree = startedTree()
        val a = DockWidget(DockSlot.TOP_LEFT)
        register(tree, a)
        val dock = tree.debug.dock

        // a is at (12,12), height 120 → center y ≈ 72. Above it inserts at 0,
        // below its center (still inside the band) inserts at 1.
        assertEquals(DropTarget.Dock(DockSlot.TOP_LEFT, 0), dock.resolveDropTarget(Vec2(50f, 10f)))
        assertEquals(DropTarget.Dock(DockSlot.TOP_LEFT, 1), dock.resolveDropTarget(Vec2(50f, 90f)))
    }

    // ---- Ordered slot & reorder (3.x) --------------------------------------

    @Test
    fun `stacking follows orderInSlot, not registration order`() {
        val tree = startedTree()
        val a = DockWidget(DockSlot.TOP_LEFT, title = "A")
        val b = DockWidget(DockSlot.TOP_LEFT, title = "B")
        register(tree, a, b)
        // Registration puts a above b.
        assertTrue(a.dockOrigin.y < b.dockOrigin.y)

        // Move b to index 0: it now stacks above a.
        tree.debug.dock.dockWidget(b, DockSlot.TOP_LEFT, 0)
        tree.debug.dock.relayout(tree.size)
        assertTrue(b.dockOrigin.y < a.dockOrigin.y, "b stacks above a after the reorder")
        assertEquals(0, b.orderInSlot)
        assertEquals(1, a.orderInSlot)
    }

    @Test
    fun `inserting at an index shifts the others stably`() {
        val tree = startedTree()
        val a = DockWidget(DockSlot.TOP_LEFT, title = "A")
        val b = DockWidget(DockSlot.TOP_LEFT, title = "B")
        val c = DockWidget(DockSlot.TOP_LEFT, title = "C")
        register(tree, a, b, c)

        // Drop c at the very top: c=0, then a, b shift to 1, 2 in order.
        tree.debug.dock.dockWidget(c, DockSlot.TOP_LEFT, 0)
        assertEquals(0, c.orderInSlot)
        assertEquals(1, a.orderInSlot)
        assertEquals(2, b.orderInSlot)
    }

    // ---- Drag termination (4.x) --------------------------------------------

    @Test
    fun `releasing over a band re-docks without floating`() {
        val tree = startedTree()
        val w = DockWidget(DockSlot.TOP_LEFT)
        register(tree, w)

        dragAndRelease(tree, w, releaseAt = Vec2(700f, 590f)) // bottom-right band

        assertEquals(DockSlot.BOTTOM_RIGHT, w.currentSlot, "re-docked into the resolved slot")
        assertEquals(DockSlot.TOP_LEFT, w.defaultSlot, "the default slot is preserved")
        assertNull(w.floatingPosition, "a band drop never floats")
        assertFalse(w.isDragging)
    }

    @Test
    fun `releasing in the miolo floats the panel`() {
        val tree = startedTree()
        val w = DockWidget(DockSlot.TOP_LEFT)
        register(tree, w)

        dragAndRelease(tree, w, releaseAt = Vec2(400f, 300f))

        assertTrue(w.isFloating, "a miolo drop floats the panel")
        assertEquals(DockSlot.TOP_LEFT, w.currentSlot, "currentSlot is untouched while floating")
    }

    // ---- Insertion indicator (5.x) -----------------------------------------

    @Test
    fun `the insertion indicator is drawn over a band and absent in the miolo`() {
        val tree = startedTree()
        val w = DockWidget(DockSlot.TOP_LEFT)
        register(tree, w)

        // Begin a drag and hold over the bottom band: the dock has a Dock target.
        val grab = w.origin + Vec2(w.contentSize().x / 2f, DebugTheme.headerHeight / 2f)
        val input = DockInput(pointer = grab, clicked = true, down = true)
        tick(tree, input)
        input.clicked = false
        input.pointer = Vec2(700f, 590f)
        tick(tree, input)
        assertEquals(1, indicatorCount(tree), "an indicator shows over a dock band")

        // Move into the miolo: the target is floating, no indicator.
        input.pointer = Vec2(400f, 300f)
        tick(tree, input)
        assertEquals(0, indicatorCount(tree), "no indicator over the miolo")
    }

    private fun indicatorCount(tree: SceneTree): Int {
        val recorder = RecordingRenderer()
        tree.render(recorder)
        return recorder.events.filterIsInstance<RecordedEvent.Rect>()
            .count { it.filled && it.color == DebugTheme.insertionIndicatorColor }
    }

    // ---- Reset & session memory (6.x) --------------------------------------

    @Test
    fun `reset restores the default slot and order and un-floats`() {
        val tree = startedTree()
        val a = DockWidget(DockSlot.TOP_LEFT, title = "A")
        val b = DockWidget(DockSlot.TOP_LEFT, title = "B")
        register(tree, a, b)

        // Move a to another slot and reorder b to the front, then float b.
        tree.debug.dock.dockWidget(a, DockSlot.BOTTOM_RIGHT, 0)
        tree.debug.dock.dockWidget(b, DockSlot.TOP_LEFT, 0)
        dragAndRelease(tree, b, releaseAt = Vec2(400f, 300f))
        assertTrue(b.isFloating)

        tree.debug.resetAllPanelPositions()
        assertEquals(DockSlot.TOP_LEFT, a.currentSlot, "a returns to its default slot")
        assertEquals(a.defaultOrder, a.orderInSlot, "a returns to its default order")
        assertNull(b.floatingPosition, "b is un-floated")
        assertEquals(DockSlot.TOP_LEFT, b.currentSlot)
        assertEquals(b.defaultOrder, b.orderInSlot)
    }

    @Test
    fun `resize re-clamps a floating panel and re-flows the docked ones`() {
        val tree = startedTree(800f, 600f)
        val docked = DockWidget(DockSlot.TOP_LEFT, title = "Docked")
        val floating = DockWidget(DockSlot.TOP_RIGHT, title = "Floating")
        register(tree, docked, floating)

        // Float the second panel toward the right, kept inside the miolo.
        dragAndRelease(tree, floating, releaseAt = Vec2(760f, 300f))
        assertTrue(floating.isFloating)

        tree.resize(400f, 300f)
        tree.debug.dock.relayout(tree.size)

        val size = floating.contentSize()
        val pos = floating.floatingPosition!!
        assertTrue(
            pos.x + size.x <= 400f + 0.01f && pos.y + size.y <= 300f + 0.01f,
            "the floating panel is re-clamped into the shrunk viewport: $pos",
        )
        // The docked panel re-anchors to the (smaller) top-left margin.
        assertEquals(DebugTheme.margin, docked.dockOrigin.x)
        assertEquals(DebugTheme.margin, docked.dockOrigin.y)
    }

    @Test
    fun `a dragged panel blocks clicks at its current position, not its dock origin`() {
        val tree = startedTree()
        val w = DockWidget(DockSlot.TOP_LEFT)
        register(tree, w)
        val dockOrigin = w.dockOrigin

        // Begin a drag and hold the panel out in the miolo.
        val grab = w.origin + Vec2(w.contentSize().x / 2f, DebugTheme.headerHeight / 2f)
        val input = DockInput(pointer = grab, clicked = true, down = true)
        tick(tree, input)
        input.clicked = false
        input.pointer = Vec2(400f, 300f)
        tick(tree, input)

        val here = w.origin + Vec2(10f, 10f)
        assertTrue(tree.debug.isOverScreenPanel(here), "the panel blocks clicks where it is now")
        assertFalse(
            tree.debug.isOverScreenPanel(dockOrigin + Vec2(10f, 10f)),
            "the vacated dock origin no longer blocks clicks",
        )
    }
}
