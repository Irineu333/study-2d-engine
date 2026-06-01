package com.neoutils.engine.debug

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.render.TextMeasurer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.serialization.inspectProperties
import java.util.Locale

/**
 * Screen-space owner of the scene-pick selection. `SceneTree.hitTestPick`
 * resolves clicks against world-space nodes and writes the result here via
 * [applyPick]; this widget holds the selection by **instance identity** (the
 * engine has no stable node IDs) plus the front-most cycling state, clears the
 * selection once the node is no longer live, and draws a read-only breadcrumb
 * (root→selected) and property panel in screen pixels.
 *
 * Lives under `ScreenDebugCanvas`, docked at `BOTTOM_RIGHT` via the `DebugDock`.
 * The companion `SelectionGizmoWidget` reads [selected] to draw the world-space
 * oriented box.
 */
class ScenePickerWidget : ScreenDebugWidget() {

    override val title: String = "Picker"

    override val defaultSlot: DockSlot = DockSlot.BOTTOM_RIGHT

    init { name = "ScenePickerWidget" }

    /** Currently picked node, or `null`. Read by `SelectionGizmoWidget`. */
    var selected: Node? = null
        private set

    private var lastPickPoint: Vec2? = null
    private var cycleIndex: Int = 0

    // Layout computed once per frame in contentSize (the dock measures there),
    // then drawn from in drawDebug so both agree on size and origin.
    private var layout: PanelLayout? = null

    /**
     * Records a pick at [pickPoint] (screen pixels) over [frontToBack] — the
     * overlapping candidates ordered front-most first. A fresh point (beyond
     * [CYCLE_EPSILON] from the previous one) selects the front-most and resets
     * the cycle; a near-same point advances the cycle modulo the candidate
     * count, revealing the nodes stacked behind. An empty candidate list clears
     * the selection. Called only by `SceneTree.hitTestPick`.
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

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        val current = selected ?: return
        if (!current.isLive) {
            selected = null
            lastPickPoint = null
            cycleIndex = 0
        }
    }

    override fun bodySize(): Vec2 {
        val node = selected
        val measurer = tree?.textMeasurer
        val surface = tree?.size
        if (node == null || measurer == null || surface == null) {
            layout = null
            return Vec2.ZERO
        }
        val computed = computeLayout(node, measurer, surface)
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
     * Builds the visible row list and panel size for [node]. Fits vertically:
     * the tail that would spill past the screen collapses into a single
     * overflow row rather than overflowing.
     */
    private fun computeLayout(node: Node, measurer: TextMeasurer, surface: Vec2): PanelLayout {
        val rows = buildRows(node)
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
        // the bottom inset equals the top (and the sides).
        val panelHeight = contentHeight - LINE_GAP
        return PanelLayout(shown, Vec2(panelWidth, panelHeight))
    }

    private fun buildRows(node: Node): List<Row> {
        val rows = mutableListOf<Row>()
        rows += Row.Title(node::class.simpleName ?: "?", node.name)
        rows += Row.Crumb(breadcrumb(node))
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

    /** Ancestor chain from the root down to [node], joined by `/`. */
    private fun breadcrumb(node: Node): String {
        val chain = mutableListOf<String>()
        var current: Node? = node
        while (current != null) {
            chain += current.name
            current = current.parent
        }
        return chain.asReversed().joinToString(" / ")
    }

    private class PanelLayout(val rows: List<Row>, val size: Vec2)

    /** A single structured line of the panel; each variant owns its layout. */
    private sealed interface Row {
        val height: Float
        fun width(measurer: TextMeasurer): Float
        fun draw(renderer: Renderer, x: Float, y: Float)

        /** `Type "name"` header. */
        class Title(val type: String, val name: String) : Row {
            override val height: Float get() = TITLE_H
            override fun width(measurer: TextMeasurer): Float =
                measurer.measureText(type, TITLE_SIZE).x + GAP + measurer.measureText(name, TITLE_SIZE).x
            override fun draw(renderer: Renderer, x: Float, y: Float) {
                // `y` is the text's top edge (the backend shifts by the ascent).
                renderer.drawText(type, Vec2(x, y), TITLE_SIZE, TYPE_COLOR)
                val typeW = renderer.measureText(type, TITLE_SIZE).x
                renderer.drawText(name, Vec2(x + typeW + GAP, y), TITLE_SIZE, NAME_COLOR)
            }
        }

        /** root→selected path, dimmed. */
        class Crumb(val text: String) : Row {
            override val height: Float get() = CRUMB_H
            override fun width(measurer: TextMeasurer): Float = measurer.measureText(text, SMALL_SIZE).x
            override fun draw(renderer: Renderer, x: Float, y: Float) =
                renderer.drawText(text, Vec2(x, y), SMALL_SIZE, CRUMB_COLOR)
        }

        /** Section header. */
        class Section(val title: String) : Row {
            override val height: Float get() = SECTION_H
            override fun width(measurer: TextMeasurer): Float = measurer.measureText(title, TEXT_SIZE).x
            override fun draw(renderer: Renderer, x: Float, y: Float) =
                renderer.drawText(title, Vec2(x, y), TEXT_SIZE, SECTION_COLOR)
        }

        /** Indented `key   value` pair with the value in a fixed column. */
        class Kv(val key: String, val value: String) : Row {
            override val height: Float get() = KV_H
            override fun width(measurer: TextMeasurer): Float = KEY_COL + measurer.measureText(value, TEXT_SIZE).x
            override fun draw(renderer: Renderer, x: Float, y: Float) {
                renderer.drawText(key, Vec2(x + INDENT, y), TEXT_SIZE, KEY_COLOR)
                renderer.drawText(value, Vec2(x + KEY_COL, y), TEXT_SIZE, VALUE_COLOR)
            }
        }
    }

    companion object {
        /** Screen-pixel radius within which a repeated click cycles instead of re-picking. */
        private const val CYCLE_EPSILON: Float = 4f

        private const val INDENT: Float = 8f
        private const val KEY_COL: Float = 64f
        private const val GAP: Float = 6f

        /** Vertical breathing room below each line; the last line drops it so the panel pads symmetrically. */
        private const val LINE_GAP: Float = 6f

        private const val TITLE_SIZE: Float = 14f
        private const val TEXT_SIZE: Float = 12f
        private const val SMALL_SIZE: Float = 10f
        private const val TITLE_H: Float = TITLE_SIZE + LINE_GAP
        private const val CRUMB_H: Float = SMALL_SIZE + LINE_GAP
        private const val SECTION_H: Float = TEXT_SIZE + LINE_GAP
        private const val KV_H: Float = TEXT_SIZE + LINE_GAP

        private val TYPE_COLOR: Color = Color(0.55f, 0.8f, 1f, 1f)
        private val NAME_COLOR: Color = Color(1f, 1f, 1f, 1f)
        private val CRUMB_COLOR: Color = Color(0.6f, 0.6f, 0.66f, 1f)
        private val SECTION_COLOR: Color = Color(0.95f, 0.8f, 0.4f, 1f)
        private val KEY_COLOR: Color = Color(0.68f, 0.74f, 0.82f, 1f)
        private val VALUE_COLOR: Color = Color(0.9f, 0.95f, 0.7f, 1f)

        private fun fmtFloat(v: Float): String = String.format(Locale.US, "%.2f", v)
        private fun fmtVec(v: Vec2): String =
            "(${String.format(Locale.US, "%.1f", v.x)}, ${String.format(Locale.US, "%.1f", v.y)})"
    }
}
