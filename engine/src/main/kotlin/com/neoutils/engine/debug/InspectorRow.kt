package com.neoutils.engine.debug

import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.render.TextMeasurer
import com.neoutils.engine.scene.Node

/**
 * Shared structured-line vocabulary for the Inspector's screen panels: the tree
 * view ([SceneTreeWidget]) and the detail panel ([NodeInspectorWidget]) draw
 * nodes with the same scheme — `type` in [TYPE_COLOR], `name` in [NAME_COLOR].
 * Each variant owns its own layout; callers stack rows top-to-bottom and size
 * the panel from `width`/`height`. The selected-row highlight (a filled band)
 * is painted by the tree widget, which knows the panel width, not by the row.
 * The lone exception is [Kv], whose value column is shared across the panel and
 * so resolved by the layout pass (it depends on sibling rows, not just itself).
 */
internal sealed interface Row {
    val height: Float
    fun width(measurer: TextMeasurer): Float
    fun draw(renderer: Renderer, x: Float, y: Float)

    /** `Type "name"` header — the detail panel's title line. */
    class Title(val type: String, val name: String) : Row {
        override val height: Float get() = TITLE_H
        override fun width(measurer: TextMeasurer): Float =
            measurer.measureText(type, TITLE_SIZE).x + GAP + measurer.measureText(name, TITLE_SIZE).x

        override fun draw(renderer: Renderer, x: Float, y: Float) {
            // `y` is the text's top edge (the backend shifts by the ascent).
            renderer.drawText(type, com.neoutils.engine.math.Vec2(x, y), TITLE_SIZE, TYPE_COLOR)
            val typeW = renderer.measureText(type, TITLE_SIZE).x
            renderer.drawText(name, com.neoutils.engine.math.Vec2(x + typeW + GAP, y), TITLE_SIZE, NAME_COLOR)
        }
    }

    /**
     * One node line of the tree, indented by [depth] (so the hierarchy reads as
     * nesting); the row draws `type "name"` with the shared scheme. [selected]
     * is carried for the tree widget to paint its highlight band behind the row.
     */
    class TreeNode(val node: Node, val depth: Int, val selected: Boolean) : Row {
        private val type: String get() = node::class.simpleName ?: "?"
        override val height: Float get() = TREE_H
        override fun width(measurer: TextMeasurer): Float =
            depth * TREE_INDENT +
                measurer.measureText(type, TEXT_SIZE).x + GAP + measurer.measureText(node.name, TEXT_SIZE).x

        override fun draw(renderer: Renderer, x: Float, y: Float) {
            val ox = x + depth * TREE_INDENT
            renderer.drawText(type, com.neoutils.engine.math.Vec2(ox, y), TEXT_SIZE, TYPE_COLOR)
            val typeW = renderer.measureText(type, TEXT_SIZE).x
            renderer.drawText(node.name, com.neoutils.engine.math.Vec2(ox + typeW + GAP, y), TEXT_SIZE, NAME_COLOR)
        }
    }

    /** Section header. */
    class Section(val title: String) : Row {
        override val height: Float get() = SECTION_H
        override fun width(measurer: TextMeasurer): Float = measurer.measureText(title, TEXT_SIZE).x
        override fun draw(renderer: Renderer, x: Float, y: Float) =
            renderer.drawText(title, com.neoutils.engine.math.Vec2(x, y), TEXT_SIZE, SECTION_COLOR)
    }

    /**
     * Indented `key   value` pair. The value starts at [valueCol], a column
     * shared by all `Kv` of a panel: the layout pass measures the widest key
     * and assigns the same column to every row, so values align and a long key
     * never overlaps its value. Defaults to [KEY_COL] (the column's floor).
     */
    class Kv(val key: String, val value: String) : Row {
        var valueCol: Float = KEY_COL
        override val height: Float get() = KV_H
        override fun width(measurer: TextMeasurer): Float = valueCol + measurer.measureText(value, TEXT_SIZE).x
        override fun draw(renderer: Renderer, x: Float, y: Float) {
            renderer.drawText(key, com.neoutils.engine.math.Vec2(x + INDENT, y), TEXT_SIZE, KEY_COLOR)
            renderer.drawText(value, com.neoutils.engine.math.Vec2(x + valueCol, y), TEXT_SIZE, VALUE_COLOR)
        }
    }
}

internal const val INDENT: Float = 8f
internal const val KEY_COL: Float = 64f
internal const val GAP: Float = 6f

/** Per-depth horizontal indent of a tree row. */
internal const val TREE_INDENT: Float = 12f

/** Vertical breathing room below each line; the last line drops it so the panel pads symmetrically. */
internal const val LINE_GAP: Float = 6f

internal const val TITLE_SIZE: Float = 14f
internal const val TEXT_SIZE: Float = 12f
internal const val TITLE_H: Float = TITLE_SIZE + LINE_GAP
internal const val SECTION_H: Float = TEXT_SIZE + LINE_GAP
internal const val KV_H: Float = TEXT_SIZE + LINE_GAP
internal const val TREE_H: Float = TEXT_SIZE + LINE_GAP

internal val TYPE_COLOR: Color = Color(0.55f, 0.8f, 1f, 1f)
internal val NAME_COLOR: Color = Color(1f, 1f, 1f, 1f)
internal val SECTION_COLOR: Color = Color(0.95f, 0.8f, 0.4f, 1f)
internal val KEY_COLOR: Color = Color(0.68f, 0.74f, 0.82f, 1f)
internal val VALUE_COLOR: Color = Color(0.9f, 0.95f, 0.7f, 1f)

/** Filled band behind the selected tree row. */
internal val SELECTED_ROW_COLOR: Color = Color(0.25f, 0.35f, 0.5f, 1f)
