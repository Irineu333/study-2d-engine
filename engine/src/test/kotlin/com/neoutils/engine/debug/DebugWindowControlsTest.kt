package com.neoutils.engine.debug

import com.neoutils.engine.input.Input
import com.neoutils.engine.input.Key
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fixed-size screen widget whose body size is stable across collapse. */
private class ControlsScreenWidget(
    override val slot: DockSlot,
    private val size: Vec2,
    override val title: String = "Fixed",
) : ScreenDebugWidget() {
    init { enabled = true }
    override fun bodySize(): Vec2 = size
    override fun drawDebug(renderer: Renderer) {}
}

private class ControlsInput(
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

class DebugWindowControlsTest {

    private fun startedTree(w: Float = 800f, h: Float = 600f): SceneTree =
        SceneTree(Node()).also { it.resize(w, h); it.start() }

    private fun tick(tree: SceneTree, input: ControlsInput) {
        tree.input = input
        tree.hitTestUI(input)
        tree.process(0f)
    }

    private fun center(r: Rect): Vec2 = Vec2((r.left + r.right) / 2f, (r.top + r.bottom) / 2f)

    private fun docked(tree: SceneTree, widget: ScreenDebugWidget) {
        tree.debug.register(widget)
        tree.debug.dock.relayout(tree.size)
    }

    @Test
    fun `pressing close disables the widget`() {
        val tree = startedTree()
        val widget = ControlsScreenWidget(DockSlot.TOP_LEFT, Vec2(200f, 100f))
        docked(tree, widget)

        val input = ControlsInput(pointer = center(widget.closeRect()), clicked = true, down = true)
        tick(tree, input)

        assertFalse(widget.enabled, "close sets enabled = false")
        assertTrue(input.mouseDragConsumed, "the control press is consumed and never starts a drag")
        assertNull(widget.customOrigin, "pressing close does not start a drag")
    }

    @Test
    fun `pressing collapse toggles the body and the reported height`() {
        val tree = startedTree()
        val widget = ControlsScreenWidget(DockSlot.TOP_LEFT, Vec2(200f, 100f))
        docked(tree, widget)
        val expanded = widget.contentSize().y

        val input = ControlsInput(pointer = center(widget.collapseRect()), clicked = true, down = true)
        tick(tree, input)
        assertTrue(widget.collapsed, "collapse toggles the state")
        assertEquals(DebugTheme.headerHeight, widget.contentSize().y, "collapsed height is just the header")
        assertNull(widget.customOrigin, "pressing collapse does not start a drag")

        // A fresh press edge re-expands.
        input.pointer = center(widget.collapseRect())
        tick(tree, input)
        assertFalse(widget.collapsed, "collapsing again expands")
        assertEquals(expanded, widget.contentSize().y, "expanded height includes the body again")
    }

    @Test
    fun `the collapse glyph differs between minimize and maximize`() {
        val tree = startedTree()
        val widget = ControlsScreenWidget(DockSlot.TOP_LEFT, Vec2(200f, 100f))
        docked(tree, widget)

        // Expanded: minimize affordance is a dash (a line), no hollow control box.
        assertEquals(0, controlBoxes(tree, widget), "expanded shows a dash, not a box")

        tick(tree, ControlsInput(pointer = center(widget.collapseRect()), clicked = true, down = true))
        tree.debug.dock.relayout(tree.size)
        // Collapsed: maximize affordance is a hollow box drawn in the control color.
        assertEquals(1, controlBoxes(tree, widget), "collapsed shows a hollow maximize box")
    }

    private fun controlBoxes(tree: SceneTree, widget: ScreenDebugWidget): Int {
        val recorder = RecordingRenderer()
        tree.render(recorder)
        val box = widget.collapseRect()
        return recorder.events.filterIsInstance<RecordedEvent.Rect>().count {
            !it.filled && it.color == DebugTheme.headerControlColor &&
                box.contains(Vec2((it.rect.left + it.rect.right) / 2f, (it.rect.top + it.rect.bottom) / 2f))
        }
    }

    @Test
    fun `collapsing a child-node panel un-mounts its buttons`() {
        val tree = startedTree()
        val hud = tree.debug.hud
        hud.enabled = true
        tree.process(0f)
        tree.applyPending() // build the panel + rows
        tree.debug.dock.relayout(tree.size)
        tree.render(RecordingRenderer()) // position the panel at its dock origin
        assertTrue(countButtons(hud) > 0, "the expanded HUD mounts its rows")

        val input = ControlsInput(pointer = center(hud.collapseRect()), clicked = true, down = true)
        tick(tree, input)
        tree.applyPending() // apply the deferred tear-down
        assertTrue(hud.collapsed)
        assertEquals(0, countButtons(hud), "collapsing tears the rows down — zero draw, zero hit-test")

        // A click where the body used to be hits no button.
        val bodyPoint = hud.bodyOrigin + Vec2(20f, 20f)
        val clickBody = ControlsInput(pointer = bodyPoint, clicked = true, down = true)
        tree.input = clickBody
        tree.hitTestUI(clickBody)
        assertNull(findHitButton(hud, bodyPoint), "no button remains under the collapsed body")
    }

    @Test
    fun `collapse survives the enable toggle and a resize`() {
        val tree = startedTree(800f, 600f)
        val widget = ControlsScreenWidget(DockSlot.TOP_LEFT, Vec2(200f, 100f))
        docked(tree, widget)

        tick(tree, ControlsInput(pointer = center(widget.collapseRect()), clicked = true, down = true))
        assertTrue(widget.collapsed)

        widget.enabled = false
        tick(tree, ControlsInput())
        widget.enabled = true
        tick(tree, ControlsInput())
        assertTrue(widget.collapsed, "collapse survives a disable/enable cycle")

        tree.resize(400f, 300f)
        tree.debug.dock.relayout(tree.size)
        assertTrue(widget.collapsed, "collapse survives a resize")
    }

    @Test
    fun `the grip and controls are carved out of the drag zone`() {
        val tree = startedTree()
        val widget = ControlsScreenWidget(DockSlot.TOP_LEFT, Vec2(200f, 100f))
        docked(tree, widget)

        // Pressing the grip does not begin a drag.
        tick(tree, ControlsInput(pointer = center(widget.gripRect()), clicked = true, down = true))
        assertNull(widget.customOrigin, "the grip is not part of the drag zone")
        assertTrue(widget.enabled, "pressing the grip leaves the widget enabled")
        assertFalse(widget.collapsed, "pressing the grip does not collapse")

        // Pressing the bare header (right of the title, left of the controls) drags.
        val bareHeader = Vec2(widget.collapseRect().left - 8f, widget.origin.y + DebugTheme.headerHeight / 2f)
        val drag = ControlsInput(pointer = bareHeader, clicked = true, down = true)
        tick(tree, drag)
        drag.clicked = false
        drag.pointer = bareHeader + Vec2(40f, 30f)
        tick(tree, drag)
        assertNotNull(widget.customOrigin, "the bare header still starts a drag")
    }

    @Test
    fun `reset expands collapsed panels and clears the override`() {
        val tree = startedTree()
        val widget = ControlsScreenWidget(DockSlot.TOP_LEFT, Vec2(200f, 100f))
        docked(tree, widget)

        // Collapse then drag.
        tick(tree, ControlsInput(pointer = center(widget.collapseRect()), clicked = true, down = true))
        val bareHeader = Vec2(widget.collapseRect().left - 8f, widget.origin.y + DebugTheme.headerHeight / 2f)
        val drag = ControlsInput(pointer = bareHeader, clicked = true, down = true)
        tick(tree, drag)
        drag.clicked = false
        drag.pointer = bareHeader + Vec2(50f, 0f)
        tick(tree, drag)
        assertTrue(widget.collapsed)
        assertNotNull(widget.customOrigin)

        tree.debug.resetAllPanelPositions()
        assertFalse(widget.collapsed, "reset expands the panel")
        assertNull(widget.customOrigin, "reset clears the drag override")
    }

    private fun countButtons(node: Node): Int {
        var n = if (node is Button) 1 else 0
        for (child in node.children) n += countButtons(child)
        return n
    }

    private fun findHitButton(node: Node, pointer: Vec2): Button? {
        if (node is Button && node.screenRect().contains(pointer)) return node
        for (child in node.children) findHitButton(child, pointer)?.let { return it }
        return null
    }
}
