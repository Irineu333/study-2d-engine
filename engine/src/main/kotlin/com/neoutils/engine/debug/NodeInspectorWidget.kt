package com.neoutils.engine.debug

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.render.TextMeasurer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.serialization.inspectProperties
import java.util.Locale

/**
 * Slave arm of the debug **Inspector**: a screen-space, read-only detail of the
 * node selected in the [SceneTreeWidget] master — its type and `name`, its
 * world transform (when `Node2D`) and its `@Inspect` properties with current
 * values, drawn with the shared node-row vocabulary ([Row]).
 *
 * It does not own the selection (it reads `tree.debug.inspector.selected`) and
 * does not own its toggle: [enabled] derives from the master, like
 * [SelectionGizmoWidget] and [ColliderModePanel]. It carries no window controls
 * ([closable]/[collapsible] are `false`) and no breadcrumb — the tree view
 * conveys the lineage. When nothing is selected it reports empty size and draws
 * no body, so it occupies no dock space.
 *
 * Lives under `ScreenDebugCanvas`, docked at `BOTTOM_RIGHT`.
 */
class NodeInspectorWidget : ScreenDebugWidget() {

    override val title: String = "Inspector"

    override val defaultSlot: DockSlot = DockSlot.BOTTOM_RIGHT

    // Slave of the tree master: visibility and on/off follow `inspector`, and
    // it carries no window controls of its own.
    override var enabled: Boolean
        get() = tree?.debug?.inspector?.enabled ?: false
        set(_) {}

    override val closable: Boolean = false
    override val collapsible: Boolean = false

    init { name = "NodeInspectorWidget" }

    private val selected: Node? get() = tree?.debug?.inspector?.selected

    // Layout computed once per frame in bodySize (the dock measures there),
    // then drawn from in drawDebug so both agree on size and origin.
    private var layout: PanelLayout? = null

    override fun bodySize(): Vec2 {
        val node = selected
        val measurer = tree?.textMeasurer
        if (node == null || measurer == null) {
            layout = null
            return Vec2.ZERO
        }
        val computed = computeLayout(node, measurer)
        layout = computed
        return computed.size
    }

    override fun drawDebug(renderer: Renderer) {
        val current = layout ?: return
        val body = bodyOrigin
        val x = body.x + DebugTheme.padding
        var y = body.y + DebugTheme.padding
        for (row in current.rows) {
            row.draw(renderer, x, y)
            y += row.height
        }
    }

    /**
     * Builds the full row list and content extent for [node] — every row, no
     * truncation. The base bounds the viewport and scrolls when the extent
     * overflows, so this reports the intrinsic height.
     */
    private fun computeLayout(node: Node, measurer: TextMeasurer): PanelLayout {
        val rows = buildRows(node)
        var contentHeight = DebugTheme.padding * 2f
        for (row in rows) contentHeight += row.height
        val panelWidth = rows.maxOf { it.width(measurer) } + DebugTheme.padding * 2f
        // The last row carries a trailing LINE_GAP it does not need; drop it so
        // the bottom inset equals the top (and the sides).
        val panelHeight = contentHeight - LINE_GAP
        return PanelLayout(rows, Vec2(panelWidth, panelHeight))
    }

    private fun buildRows(node: Node): List<Row> {
        val rows = mutableListOf<Row>()
        rows += Row.Title(node::class.simpleName ?: "?", node.name)
        if (node is Node2D) {
            val world = node.world()
            rows += Row.Section("Transform (world)")
            rows += Row.Kv("pos", fmtVec(world.position))
            rows += Row.Kv("rot", fmtFloat(world.rotation))
            rows += Row.Kv("scale", fmtVec(world.scale))
        }
        // `name` is the title and `transform` is the Transform section, so drop
        // them from the property list to avoid showing each value twice.
        val props = inspectProperties(node)
            .filter { it.displayName != "name" && it.displayName != "transform" }
        if (props.isNotEmpty()) {
            rows += Row.Section("Properties")
            for (p in props) rows += Row.Kv(p.displayName, "${p.value}")
        }
        return rows
    }

    private class PanelLayout(val rows: List<Row>, val size: Vec2)

    companion object {
        private fun fmtFloat(v: Float): String = String.format(Locale.US, "%.2f", v)
        private fun fmtVec(v: Vec2): String =
            "(${String.format(Locale.US, "%.1f", v.x)}, ${String.format(Locale.US, "%.1f", v.y)})"
    }
}
