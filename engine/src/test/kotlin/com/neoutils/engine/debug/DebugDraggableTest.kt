package com.neoutils.engine.debug

import com.neoutils.engine.input.Input
import com.neoutils.engine.input.Key
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.ColorRect
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fixed-size screen widget (no controls) for exercising the drag layer. */
private class FixedScreenWidget(
    override val defaultSlot: DockSlot,
    private val size: Vec2,
    override val title: String = "Fixed",
) : ScreenDebugWidget() {
    init { enabled = true }
    override fun bodySize(): Vec2 = size
    override fun drawDebug(renderer: Renderer) {}
}

/** Records whether it would act on a held drag while honoring the consume flag. */
private class GameplayDragConsumer : Node() {
    var panned: Boolean = false
    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        val input = tree?.input ?: return
        if (input.isMouseDown(MouseButton.Left) && !input.mouseDragConsumed) panned = true
    }
}

private class DragInput(
    var pointer: Vec2 = Vec2.ZERO,
    var clicked: Boolean = false,
    var down: Boolean = false,
    var pressedKey: Key? = null,
) : Input {
    override val pointerPosition: Vec2 get() = pointer
    override var mouseClickConsumed: Boolean = false
    override var mouseDragConsumed: Boolean = false
    override fun isKeyDown(key: Key): Boolean = false
    override fun wasKeyPressed(key: Key): Boolean = key == pressedKey
    override fun isMouseDown(button: MouseButton): Boolean = button == MouseButton.Left && down
    override fun wasMouseClickedRaw(button: MouseButton): Boolean = button == MouseButton.Left && clicked
}

class DebugDraggableTest {

    private fun startedTree(w: Float = 800f, h: Float = 600f): SceneTree =
        SceneTree(Node()).also { it.resize(w, h); it.start() }

    /** One full tick: reset consume flags, then process. */
    private fun tick(tree: SceneTree, input: DragInput) {
        tree.input = input
        tree.hitTestUI(input)
        tree.process(0f)
    }

    @Test
    fun `dragging a panel moves it and flags the drag as consumed`() {
        val tree = startedTree()
        val widget = FixedScreenWidget(DockSlot.TOP_LEFT, Vec2(200f, 100f))
        tree.debug.register(widget)
        tree.debug.dock.relayout(tree.size)
        val docked = widget.dockOrigin
        val grabPoint = docked + Vec2(40f, 8f)

        val input = DragInput(pointer = grabPoint, clicked = true, down = true)
        tick(tree, input) // press inside the grab zone begins the drag
        assertTrue(input.mouseDragConsumed, "starting a drag flags the pointer as consumed")
        assertTrue(widget.isDragging, "pressing the header begins a drag")

        input.clicked = false
        input.pointer = grabPoint + Vec2(150f, 120f) // into the miolo
        tick(tree, input) // move while held
        assertEquals(docked + Vec2(150f, 120f), widget.origin, "panel follows the pointer by the grab offset")
        assertTrue(input.mouseDragConsumed, "an ongoing drag keeps the consume flag set")

        input.down = false
        tick(tree, input) // release in the miolo floats the panel
        assertFalse(widget.isDragging, "releasing ends the drag")
        assertNotNull(widget.floatingPosition, "released in the miolo, the panel is floating")
        // After release the per-tick flag was reset by hitTestUI and not re-set.
        assertFalse(input.mouseDragConsumed, "releasing stops consuming the drag")
    }

    @Test
    fun `an ongoing panel drag does not leak to a gameplay drag consumer`() {
        val tree = startedTree()
        val widget = FixedScreenWidget(DockSlot.TOP_LEFT, Vec2(200f, 100f))
        tree.debug.register(widget)
        // Consumer lives under the root, after the auto-inserted DebugLayer, so it
        // processes once the panel (inside that layer) has already claimed the drag
        // this tick. Pressing the panel raises it to the end of the canvas children
        // (bring-to-front), so a consumer placed as a canvas sibling would run
        // first; rooting it after the whole debug subtree keeps the panel ahead.
        val consumer = GameplayDragConsumer()
        tree.debug.dock.relayout(tree.size)
        tree.root.addChild(consumer)
        tree.applyPending()

        val grabPoint = widget.dockOrigin + Vec2(40f, 8f)
        val input = DragInput(pointer = grabPoint, clicked = true, down = true)
        tick(tree, input) // begin drag

        input.clicked = false
        input.pointer = grabPoint + Vec2(60f, 40f)
        tick(tree, input) // ongoing drag — flag set before consumer runs

        assertFalse(consumer.panned, "a consumer that honors the flag stays put during a panel drag")
        assertTrue(input.mouseDragConsumed)
    }

    @Test
    fun `a held drag outside any panel still reaches a gameplay consumer`() {
        val tree = startedTree()
        val widget = FixedScreenWidget(DockSlot.TOP_LEFT, Vec2(120f, 40f))
        tree.debug.register(widget)
        val consumer = GameplayDragConsumer()
        tree.debug.dock.relayout(tree.size)
        widget.parent!!.addChild(consumer)
        tree.applyPending()

        // Press far from the panel: no drag is captured, so the flag stays clear.
        val input = DragInput(pointer = Vec2(600f, 500f), clicked = true, down = true)
        tick(tree, input)
        assertFalse(input.mouseDragConsumed, "pressing empty space does not consume the drag")
        assertTrue(consumer.panned, "gameplay still sees the held pointer")
    }

    @Test
    fun `floating position survives toggle and is re-clamped on resize`() {
        val tree = startedTree(800f, 600f)
        val widget = FixedScreenWidget(DockSlot.TOP_LEFT, Vec2(200f, 100f))
        tree.debug.register(widget)
        tree.debug.dock.relayout(tree.size)
        val grabPoint = widget.dockOrigin + Vec2(40f, 8f)

        val input = DragInput(pointer = grabPoint, clicked = true, down = true)
        tick(tree, input)
        input.clicked = false
        // Release toward the right, but with the whole window kept inside the
        // central band gap (no edge in a band), so it floats (and clamps).
        input.pointer = Vec2(760f, 300f)
        tick(tree, input)
        input.down = false
        tick(tree, input)
        val floated = widget.floatingPosition
        assertNotNull(floated, "released in the miolo, the panel is floating")

        // Toggle off then on: the floating position must persist.
        widget.enabled = false
        tick(tree, DragInput())
        widget.enabled = true
        tick(tree, DragInput())
        assertEquals(floated, widget.floatingPosition, "the floating position survives a disable/enable cycle")

        // Shrink the surface: the floating position is re-clamped into the viewport.
        tree.resize(400f, 300f)
        tree.debug.dock.relayout(tree.size)
        val clamped = widget.floatingPosition
        assertNotNull(clamped)
        assertTrue(clamped.x in 0f..200f && clamped.y in 0f..200f, "re-clamped inside the shrunk viewport: $clamped")
    }

    @Test
    fun `reset clears the override and the panel flows back to its slot`() {
        val tree = startedTree()
        val widget = FixedScreenWidget(DockSlot.TOP_LEFT, Vec2(200f, 100f))
        tree.debug.register(widget)
        tree.debug.dock.relayout(tree.size)
        val slotOrigin = widget.dockOrigin
        val grabPoint = slotOrigin + Vec2(40f, 8f)

        val input = DragInput(pointer = grabPoint, clicked = true, down = true)
        tick(tree, input)
        input.clicked = false
        input.pointer = grabPoint + Vec2(120f, 90f) // miolo
        tick(tree, input)
        input.down = false
        tick(tree, input) // release floats it
        assertNotNull(widget.floatingPosition)

        tree.debug.resetAllPanelPositions()
        assertNull(widget.floatingPosition, "reset un-floats the panel")
        tree.debug.dock.relayout(tree.size)
        assertEquals(slotOrigin, widget.origin, "the panel is positioned by the dock again")
    }

    @Test
    fun `Time HUD button stays clickable - the grab zone excludes controls`() {
        val tree = startedTree()
        val tc = tree.debug.timeControls
        tc.enabled = true
        tree.process(0f) // schedules the panel + buttons (deferred during traversal)
        tree.applyPending() // attaches them to the live tree
        tree.debug.dock.relayout(tree.size)
        tree.render(RecordingRenderer()) // positions the panel at its dock origin

        val pause = findButton(tc, "TimeControlPause")
        assertNotNull(pause, "the Time panel exposes a pause button")
        val rect = pause.screenRect()
        val center = Vec2((rect.left + rect.right) / 2f, (rect.top + rect.bottom) / 2f)

        val pausedBefore = tree.paused
        val input = DragInput(pointer = center, clicked = true, down = true)
        tick(tree, input) // press on the button: hitTestUI arms it, no drag begins
        assertTrue(input.mouseClickConsumed, "the button absorbs the click")
        assertFalse(tc.isDragging, "pressing a control does not start a drag")

        input.clicked = false
        input.down = false
        tick(tree, input) // release inside emits the press
        assertEquals(!pausedBefore, tree.paused, "the pause button still toggles the tree")
        assertFalse(tc.isDragging, "still no drag after a normal button click")
    }

    @Test
    fun `clicking a debug panel is consumed and does not fall through to the picker`() {
        // A world node under the cursor that the picker would otherwise select.
        val target = ColorRect().apply {
            name = "Target"
            size = Vec2(300f, 300f)
            transform = Transform(position = Vec2.ZERO)
        }
        val tree = SceneTree(Node().apply { addChild(target) })
            .also { it.resize(800f, 600f); it.start() }
        val panel = FixedScreenWidget(DockSlot.TOP_LEFT, Vec2(200f, 100f))
        tree.debug.register(panel)
        tree.debug.scenePicker.enabled = true
        tree.debug.dock.relayout(tree.size)

        // A point on the panel that also sits over the world target behind it.
        val overPanel = panel.origin + Vec2(20f, 20f)
        val input = DragInput(pointer = overPanel, clicked = true, down = true)
        tree.input = input
        tree.hitTestUI(input)
        tree.hitTestPick(input)

        assertTrue(input.mouseClickConsumed, "the panel absorbs the click")
        assertNull(tree.debug.scenePicker.selected, "the picker must not pick the node behind the panel")
    }

    @Test
    fun `header draws a drag-grip affordance`() {
        val tree = startedTree()
        val widget = FixedScreenWidget(DockSlot.TOP_LEFT, Vec2(200f, 100f))
        tree.debug.register(widget)
        tree.debug.dock.relayout(tree.size)

        val recorder = RecordingRenderer()
        tree.render(recorder)
        val gripDots = recorder.events.filterIsInstance<RecordedEvent.Rect>()
            .count { it.filled && it.color == DebugTheme.headerGripColor }
        assertEquals(6, gripDots, "the header shows a 2x3 grip of dots")
    }

    private fun findButton(node: Node, name: String): Button? {
        if (node is Button && node.name == name) return node
        for (child in node.children) findButton(child, name)?.let { return it }
        return null
    }
}
