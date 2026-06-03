package com.neoutils.engine.debug

import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.render.TextMeasurer
import com.neoutils.engine.scene.Node

/**
 * Master arm of the debug **Inspector**: a screen-space, navigable view of the
 * live scene-graph hierarchy as an indented list of node rows (one per node,
 * indented by depth), rooted at `SceneTree.root`. This is the widget registered
 * in the HUD (the single "Inspector" row) and the owner of the selection —
 * [selected] plus [select] — read by the slave [NodeInspectorWidget] detail
 * panel and the world-space [SelectionGizmoWidget].
 *
 * Two sources write the selection, one owner holds it:
 *  - **world pick** → `SceneTree.hitTestPick` → [applyPick], preserving the
 *    front-most cycling state.
 *  - **tree-row click** → [select], which resets the cycling state (an explicit
 *    selection is not a geometric pick).
 *
 * The tree is recomputed every frame so it tracks a mutating scene graph, and
 * it excludes the engine-inserted `DebugLayer` (the `"__debug"` subtree). Its
 * full window chrome (collapse `[_]`, close `[x]`) governs the whole tool:
 * closing it disables the Inspector (all arms turn off), reopenable from the
 * HUD. Lives under `ScreenDebugCanvas`, docked at `BOTTOM_LEFT` (the slave
 * detail panel docks at `BOTTOM_RIGHT`).
 */
class SceneTreeWidget : ScreenDebugWidget() {

    override val title: String = "Inspector"

    override val defaultSlot: DockSlot = DockSlot.BOTTOM_LEFT

    init { name = "SceneTreeWidget" }

    /** Currently selected node, or `null`. Read by the slave arms. */
    var selected: Node? = null
        private set

    private var lastPickPoint: Vec2? = null
    private var cycleIndex: Int = 0

    // Layout computed once per frame in bodySize (the dock measures there), then
    // drawn from in drawDebug and read by the row hit-test so all three agree.
    private var layout: TreeLayout? = null

    /**
     * Records a world pick at [pickPoint] (screen pixels) over [frontToBack] —
     * the overlapping candidates ordered front-most first. A fresh point (beyond
     * [CYCLE_EPSILON] from the previous one) selects the front-most and resets
     * the cycle; a near-same point advances the cycle modulo the candidate
     * count. An empty candidate list clears the selection. Called only by
     * `SceneTree.hitTestPick`.
     */
    internal fun applyPick(pickPoint: Vec2, frontToBack: List<Node>) {
        if (frontToBack.isEmpty()) {
            selected = null
            lastPickPoint = pickPoint
            cycleIndex = 0
            return
        }
        val last = lastPickPoint
        val near = last != null && (pickPoint - last).length <= CYCLE_EPSILON
        cycleIndex = if (near) (cycleIndex + 1) % frontToBack.size else 0
        lastPickPoint = pickPoint
        selected = frontToBack[cycleIndex]
    }

    /**
     * Selects [node] explicitly (a tree-row click), resetting the world-pick
     * cycling state — an explicit selection is not a geometric pick and must not
     * inherit a prior cycle.
     */
    internal fun select(node: Node) {
        selected = node
        lastPickPoint = null
        cycleIndex = 0
    }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        val current = selected
        if (current != null && !current.isLive) {
            selected = null
            lastPickPoint = null
            cycleIndex = 0
        }
        hitTestRows()
    }

    /**
     * Polling row hit-test (same style as the base's drag): when this panel owns
     * the current press (so a click over another panel does not leak here) and
     * the press landed on a node row, select that node and consume the click so
     * it neither re-picks the world nor reaches gameplay.
     */
    private fun hitTestRows() {
        if (!bodyVisible) return
        val owningTree = tree ?: return
        if (this !== owningTree.debug.pressOwner) return
        val input = owningTree.input ?: return
        val node = nodeAt(input.pointerPosition) ?: return
        select(node)
        input.mouseClickConsumed = true
    }

    /** The node whose row contains [pointer] (screen pixels), or `null`. */
    private fun nodeAt(pointer: Vec2): Node? {
        val current = layout ?: return null
        val body = bodyOrigin
        val left = body.x
        if (pointer.x < left || pointer.x > left + current.size.x) return null
        var y = body.y + DebugTheme.padding
        for (row in current.rows) {
            if (row is Row.TreeNode && pointer.y >= y && pointer.y < y + row.height) return row.node
            y += row.height
        }
        return null
    }

    override fun bodySize(): Vec2 {
        val owningTree = tree
        val measurer = owningTree?.textMeasurer
        val surface = owningTree?.size
        if (owningTree == null || measurer == null || surface == null) {
            layout = null
            return Vec2.ZERO
        }
        val computed = computeLayout(owningTree.root, measurer, surface)
        layout = computed
        return computed.size
    }

    override fun drawDebug(renderer: Renderer) {
        val current = layout ?: return
        val body = bodyOrigin
        val x = body.x + DebugTheme.padding
        var y = body.y + DebugTheme.padding
        for (row in current.rows) {
            if (row is Row.TreeNode && row.selected) {
                renderer.drawRect(
                    Rect(Vec2(body.x, y - LINE_GAP / 2f), Vec2(current.size.x, row.height)),
                    SELECTED_ROW_COLOR,
                    filled = true,
                )
            }
            row.draw(renderer, x, y)
            y += row.height
        }
    }

    /**
     * Builds the visible row list and panel size for the hierarchy under [root].
     * Fits vertically: the tail that would spill past the screen collapses into
     * a single overflow row reporting how many rows were hidden.
     */
    private fun computeLayout(root: Node, measurer: TextMeasurer, surface: Vec2): TreeLayout {
        val rows = buildTreeRows(root)
        // Leave room for the title-bar header drawn above the body.
        val maxHeight = surface.y - DebugTheme.margin * 2f - DebugTheme.headerHeight
        val shown = mutableListOf<Row>()
        var contentHeight = DebugTheme.padding * 2f
        var hidden = 0
        for ((index, row) in rows.withIndex()) {
            if (contentHeight + row.height > maxHeight) {
                hidden = rows.size - index
                break
            }
            shown += row
            contentHeight += row.height
        }
        if (hidden > 0) {
            shown += Row.Section("… (+$hidden more)")
            contentHeight += SECTION_H
        }
        val panelWidth = shown.maxOf { it.width(measurer) } + DebugTheme.padding * 2f
        // The last row carries a trailing LINE_GAP it does not need; drop it so
        // the bottom inset equals the top.
        val panelHeight = contentHeight - LINE_GAP
        return TreeLayout(shown, Vec2(panelWidth, panelHeight))
    }

    /**
     * DFS pre-order rows from [root], indented by depth, **skipping** the
     * `"__debug"` node (the `DebugLayer`) and its descendants — that subtree is
     * engine plumbing, not game content (invariant #6).
     */
    private fun buildTreeRows(root: Node): List<Row.TreeNode> {
        val rows = mutableListOf<Row.TreeNode>()
        fun visit(node: Node, depth: Int) {
            if (node.name == DebugLayer.NODE_NAME) return
            rows += Row.TreeNode(node, depth, node === selected)
            for (child in node.children) visit(child, depth + 1)
        }
        visit(root, 0)
        return rows
    }

    private class TreeLayout(val rows: List<Row>, val size: Vec2)

    companion object {
        /** Screen-pixel radius within which a repeated click cycles instead of re-picking. */
        private const val CYCLE_EPSILON: Float = 4f
    }
}
