package com.neoutils.engine.debug

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.serialization.inspectProperties

/**
 * Screen-space owner of the scene-pick selection. `SceneTree.hitTestPick`
 * resolves clicks against world-space nodes and writes the result here via
 * [applyPick]; this widget holds the selection by **instance identity** (the
 * engine has no stable node IDs) plus the front-most cycling state, clears the
 * selection once the node is no longer live, and draws a read-only breadcrumb
 * (root→selected) and property panel in screen pixels.
 *
 * Lives under `ScreenDebugCanvas`. The companion `SelectionGizmoWidget` reads
 * [selected] to draw the world-space oriented box.
 */
class ScenePickerWidget : ScreenDebugWidget() {

    override val title: String = "Picker"

    init { name = "ScenePickerWidget" }

    /** Currently picked node, or `null`. Read by `SelectionGizmoWidget`. */
    var selected: Node? = null
        private set

    private var lastPickPoint: Vec2? = null
    private var cycleIndex: Int = 0

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

    override fun drawDebug(renderer: Renderer) {
        val node = selected ?: return
        val owningTree = tree ?: return
        val maxY = owningTree.size.y - PAD

        val lines = buildLines(node)
        var y = TOP
        for ((index, line) in lines.withIndex()) {
            if (y + LINE_HEIGHT > maxY) {
                renderer.drawText("… (+${lines.size - index} more)", Vec2(PAD, y), TEXT_SIZE, OVERFLOW_COLOR)
                return
            }
            renderer.drawText(line, Vec2(PAD, y), TEXT_SIZE, TEXT_COLOR)
            y += LINE_HEIGHT
        }
    }

    private fun buildLines(node: Node): List<String> {
        val lines = mutableListOf<String>()
        lines += breadcrumb(node)
        lines += "type: ${node::class.simpleName}"
        lines += "name: ${node.name}"
        if (node is Node2D) {
            val world = node.world()
            lines += "world.pos: ${world.position}"
            lines += "world.rot: ${world.rotation}"
            lines += "world.scale: ${world.scale}"
        }
        for (entry in inspectProperties(node)) {
            lines += "${entry.displayName} = ${entry.value}"
        }
        return lines
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

    companion object {
        /** Screen-pixel radius within which a repeated click cycles instead of re-picking. */
        private const val CYCLE_EPSILON: Float = 4f
        private const val PAD: Float = 8f
        private const val TOP: Float = 48f
        private const val LINE_HEIGHT: Float = 16f
        private const val TEXT_SIZE: Float = 12f
        private val TEXT_COLOR: Color = Color(0.95f, 0.95f, 0.6f, 1f)
        private val OVERFLOW_COLOR: Color = Color(0.7f, 0.7f, 0.7f, 1f)
    }
}
