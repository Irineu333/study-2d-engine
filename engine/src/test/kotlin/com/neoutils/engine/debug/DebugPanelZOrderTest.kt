package com.neoutils.engine.debug

import com.neoutils.engine.input.Input
import com.neoutils.engine.input.Key
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.CanvasLayer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Fixed-size screen panel (no body controls) for exercising z-order. */
private class ZPanel(
    override val title: String,
    private val size: Vec2,
) : ScreenDebugWidget() {
    init { enabled = true }
    override fun bodySize(): Vec2 = size
    override fun drawDebug(renderer: Renderer) {}
}

private class ZInput(
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

class DebugPanelZOrderTest {

    private val hh = DebugTheme.headerHeight

    private fun startedTree(w: Float = 800f, h: Float = 600f): SceneTree =
        SceneTree(Node()).also { it.resize(w, h); it.start() }

    /** One full tick: reset consume flags (and press owner), then process. */
    private fun tick(tree: SceneTree, input: ZInput) {
        tree.input = input
        tree.hitTestUI(input)
        tree.process(0f)
    }

    /** The z-order: the `ScreenDebugCanvas`'s child list (last paints on top). */
    private fun siblings(panel: ScreenDebugWidget): List<Node> = panel.parent!!.children

    @Test
    fun `press on the overlap arms only the top panel`() {
        val tree = startedTree()
        val bottom = ZPanel("Bottom", Vec2(200f, 100f))
        val top = ZPanel("Top", Vec2(200f, 100f))
        tree.debug.register(bottom) // earlier child
        tree.debug.register(top)    // later child → painted on top
        bottom.floatingPosition = Vec2(100f, 100f)
        top.floatingPosition = Vec2(120f, 120f)
        tree.debug.dock.relayout(tree.size)

        // A header point of `top` that also lands inside `bottom`'s rect.
        val overlap = top.origin + Vec2(80f, hh / 2f)
        val input = ZInput(pointer = overlap, clicked = true, down = true)
        tick(tree, input)

        assertTrue(top.isDragging, "the top panel under the pointer arms the drag")
        assertFalse(bottom.isDragging, "the lower panel must not arm a drag on the same press")
    }

    @Test
    fun `press on a covered panel brings it to the front`() {
        val tree = startedTree()
        val covered = ZPanel("Covered", Vec2(200f, 100f))
        val front = ZPanel("Front", Vec2(200f, 100f))
        tree.debug.register(covered) // earlier child
        tree.debug.register(front)   // later child → on top initially
        covered.floatingPosition = Vec2(100f, 100f)
        front.floatingPosition = Vec2(250f, 100f) // overlaps to the right
        tree.debug.dock.relayout(tree.size)

        // Press the body of `covered` in its exposed (left) region.
        val bodyPoint = covered.origin + Vec2(40f, hh + 10f)
        val input = ZInput(pointer = bodyPoint, clicked = true, down = true)
        tick(tree, input)

        assertSame(covered, siblings(covered).last(), "pressing a covered panel raises it to the top")
        assertFalse(covered.isDragging, "a body press brings to front without starting a drag")
    }

    @Test
    fun `window control of the top panel does not leak to the panel below`() {
        val tree = startedTree()
        val below = ZPanel("Below", Vec2(200f, 140f))
        val above = ZPanel("Above", Vec2(200f, 100f))
        tree.debug.register(below) // earlier child
        tree.debug.register(above) // later child → on top
        below.floatingPosition = Vec2(150f, 90f)
        above.floatingPosition = Vec2(100f, 100f)
        tree.debug.dock.relayout(tree.size)

        val close = above.closeRect()
        val center = Vec2((close.left + close.right) / 2f, (close.top + close.bottom) / 2f)
        assertTrue(below.contentSize().let { it.x > 0f } && run {
            val o = below.origin; val s = below.contentSize()
            center.x in o.x..(o.x + s.x) && center.y in o.y..(o.y + s.y)
        }, "the close control sits over the lower panel too")

        val input = ZInput(pointer = center, clicked = true, down = true)
        tick(tree, input)

        assertFalse(above.enabled, "the close control of the top panel runs its action")
        assertTrue(below.enabled, "the lower panel does not react to the press")
        assertFalse(below.isDragging, "the lower panel does not start a drag either")
    }

    @Test
    fun `z-order survives resize and enable toggle`() {
        val tree = startedTree(800f, 600f)
        val a = ZPanel("A", Vec2(200f, 100f))
        val b = ZPanel("B", Vec2(200f, 100f))
        tree.debug.register(a)
        tree.debug.register(b)
        a.floatingPosition = Vec2(100f, 100f)
        b.floatingPosition = Vec2(140f, 120f)
        tree.debug.dock.relayout(tree.size)

        // Bring A to front by pressing its exposed corner (top-left, not under B).
        val input = ZInput(pointer = a.origin + Vec2(20f, hh / 2f), clicked = true, down = true)
        tick(tree, input)
        assertSame(a, siblings(a).last(), "A is now on top")

        // Resize does not rebuild the child list — order is retained.
        tree.resize(400f, 300f)
        tree.debug.dock.relayout(tree.size)
        assertSame(a, siblings(a).last(), "z-order survives a resize")

        // Disable then re-enable A: it keeps its position in the sibling order.
        a.enabled = false
        tick(tree, ZInput())
        a.enabled = true
        tick(tree, ZInput())
        assertSame(a, siblings(a).last(), "z-order survives an enable/disable toggle")
    }

    @Test
    fun `a Button keeps precedence over panel arbitration`() {
        val tree = startedTree()
        val panel = ZPanel("P", Vec2(200f, 100f))
        tree.debug.register(panel)
        panel.floatingPosition = Vec2(100f, 100f)
        tree.debug.dock.relayout(tree.size)

        // A game-layer Button overlapping the panel.
        val layer = CanvasLayer().apply { layer = 0 }
        val button = Button().apply {
            name = "Btn"
            transform = Transform(position = Vec2(120f, 120f))
            size = Vec2(40f, 30f)
        }
        layer.addChild(button)
        tree.root.addChild(layer)
        tree.applyPending()

        val input = ZInput(pointer = Vec2(130f, 130f), clicked = true, down = true)
        tree.input = input
        tree.hitTestUI(input)

        assertTrue(input.mouseClickConsumed, "the Button absorbs the click")
        assertNull(tree.debug.pressOwner, "a Button absorbs first — no panel becomes the press owner")
        assertFalse(panel.isDragging, "the panel under the button does not arm a drag")
    }

    @Test
    fun `pressing a Button inside a covered panel brings the panel to the front`() {
        val tree = startedTree()
        val covered = ZPanel("Covered", Vec2(200f, 100f))
        val front = ZPanel("Front", Vec2(200f, 100f))
        tree.debug.register(covered) // earlier child
        tree.debug.register(front)   // later child → on top initially
        covered.floatingPosition = Vec2(100f, 100f)
        front.floatingPosition = Vec2(300f, 100f)
        // A Button living inside the covered panel, in its exposed body region.
        val button = Button().apply {
            name = "PanelBtn"
            transform = Transform(position = Vec2(130f, 100f + hh + 10f))
            size = Vec2(40f, 30f)
        }
        covered.addChild(button)
        tree.debug.dock.relayout(tree.size)

        var fired = false
        button.pressed.connect { fired = true }
        val center = button.transform.position + Vec2(20f, 15f)
        val input = ZInput(pointer = center, clicked = true, down = true)
        tree.input = input
        tree.hitTestUI(input)

        assertTrue(input.mouseClickConsumed, "the Button absorbs the click")
        assertNull(tree.debug.pressOwner, "a Button press does not make the panel a drag owner")
        assertSame(covered, siblings(covered).last(), "pressing a panel's Button still raises the panel")

        // Release inside: the button's press still emits — its behavior is intact.
        input.clicked = false
        input.down = false
        tree.process(0f)
        assertTrue(fired, "the Button's press fires normally after the panel was raised")
    }

    @Test
    fun `raising a panel does not disturb the docked layout`() {
        val tree = startedTree()
        val a = ZPanel("A", Vec2(200f, 100f))
        val b = ZPanel("B", Vec2(200f, 100f))
        // Different default slots so they never stack/overlap.
        tree.debug.register(a)
        tree.debug.register(b)
        a.currentSlot = DockSlot.TOP_LEFT
        b.currentSlot = DockSlot.TOP_RIGHT
        tree.debug.dock.relayout(tree.size)
        val aDock = a.dockOrigin
        val bDock = b.dockOrigin

        // Reorder children: the dock places by (slot, orderInSlot), not child order.
        tree.debug.raisePanelToTop(a)
        tree.debug.dock.relayout(tree.size)

        assertSame(a, siblings(a).last(), "A moved to the top of the child order")
        assertTrue(aDock == a.dockOrigin && bDock == b.dockOrigin, "docked positions are unchanged by the reorder")
    }
}
