package com.neoutils.engine.games.tictactoe

import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class Board : Node2D() {

    @Transient
    var hoveredCell: Int? = null
        private set

    @Transient
    val cells: Array<Mark?> = arrayOfNulls(9)

    @Transient
    var currentPlayer: Mark = Mark.X
        private set

    @Transient
    var winner: Mark? = null
        private set

    @Transient
    var isDraw: Boolean = false
        private set

    @Transient
    var winningLine: Triple<Int, Int, Int>? = null
        private set

    @Inspect
    var origin: Vec2 = Vec2.ZERO

    @Inspect
    var cellSize: Float = 100f

    val gameOver: Boolean get() = winner != null || isDraw

    fun cellRect(index: Int): Rect {
        val row = index / 3
        val col = index % 3
        return Rect(
            origin = Vec2(origin.x + col * cellSize, origin.y + row * cellSize),
            size = Vec2(cellSize, cellSize),
        )
    }

    fun cellAt(point: Vec2): Int? {
        for (i in 0 until 9) {
            if (cellRect(i).contains(point)) return i
        }
        return null
    }

    fun checkWinner(): Triple<Int, Int, Int>? {
        for (line in WINNING_LINES) {
            val (a, b, c) = line
            val mark = cells[a] ?: continue
            if (cells[b] == mark && cells[c] == mark) return line
        }
        return null
    }

    fun reset() {
        for (i in 0 until 9) cells[i] = null
        winner = null
        isDraw = false
        winningLine = null
        currentPlayer = Mark.X
    }

    override fun onProcess(dt: Float) {
        val scene = rootScene() ?: return
        val input = scene.input ?: return
        // pointerPosition arrives in surface pixels; the board lives in world
        // coordinates under the scene's Camera2D, so project before hit-testing.
        hoveredCell = cellAt(scene.screenToWorld(input.pointerPosition))

        if (!input.wasMouseClicked(MouseButton.Left)) return
        if (gameOver) {
            reset()
            return
        }
        val target = hoveredCell ?: return
        if (cells[target] != null) return
        placeMove(target)
    }

    override fun onDraw(renderer: Renderer) {
        drawGrid(renderer)
        for (i in 0 until 9) {
            val mark = cells[i] ?: continue
            drawMark(renderer, i, mark, MARK_COLOR)
        }
        val hovered = hoveredCell
        if (!gameOver && hovered != null && cells[hovered] == null) {
            drawMark(renderer, hovered, currentPlayer, GHOST_COLOR)
        }
        winningLine?.let { drawWinningLine(renderer, it) }
    }

    private fun drawWinningLine(renderer: Renderer, line: Triple<Int, Int, Int>) {
        val from = cellRect(line.first).let { Vec2(it.origin.x + it.size.x / 2f, it.origin.y + it.size.y / 2f) }
        val to = cellRect(line.third).let { Vec2(it.origin.x + it.size.x / 2f, it.origin.y + it.size.y / 2f) }
        val thickness = (cellSize * WIN_THICKNESS_RATIO).coerceAtLeast(2f)
        renderer.drawLine(from, to, thickness, WIN_COLOR)
    }

    private fun drawMark(renderer: Renderer, index: Int, mark: Mark, color: Color) {
        val rect = cellRect(index)
        val cx = rect.origin.x + rect.size.x / 2f
        val cy = rect.origin.y + rect.size.y / 2f
        val inset = cellSize * MARK_INSET_RATIO
        val thickness = (cellSize * MARK_THICKNESS_RATIO).coerceAtLeast(1f)
        when (mark) {
            Mark.X -> {
                val l = rect.origin.x + inset
                val r = rect.origin.x + rect.size.x - inset
                val t = rect.origin.y + inset
                val b = rect.origin.y + rect.size.y - inset
                renderer.drawLine(Vec2(l, t), Vec2(r, b), thickness, color)
                renderer.drawLine(Vec2(r, t), Vec2(l, b), thickness, color)
            }
            Mark.O -> {
                val radius = rect.size.x / 2f - inset
                renderer.drawCircle(Vec2(cx, cy), radius, color, filled = false, thickness = thickness)
            }
        }
    }

    private fun drawGrid(renderer: Renderer) {
        val thickness = (cellSize * GRID_THICKNESS_RATIO).coerceAtLeast(1f)
        val boardSize = cellSize * 3f
        val left = origin.x
        val top = origin.y
        val right = origin.x + boardSize
        val bottom = origin.y + boardSize
        for (i in 1..2) {
            val x = origin.x + cellSize * i
            renderer.drawLine(Vec2(x, top), Vec2(x, bottom), thickness, GRID_COLOR)
            val y = origin.y + cellSize * i
            renderer.drawLine(Vec2(left, y), Vec2(right, y), thickness, GRID_COLOR)
        }
    }

    internal fun placeMove(index: Int) {
        if (gameOver || cells[index] != null) return
        cells[index] = currentPlayer
        val line = checkWinner()
        if (line != null) {
            winner = currentPlayer
            winningLine = line
        } else if (cells.all { it != null }) {
            isDraw = true
        } else {
            currentPlayer = currentPlayer.other()
        }
    }

    companion object {
        private val WINNING_LINES: List<Triple<Int, Int, Int>> = listOf(
            Triple(0, 1, 2), Triple(3, 4, 5), Triple(6, 7, 8),
            Triple(0, 3, 6), Triple(1, 4, 7), Triple(2, 5, 8),
            Triple(0, 4, 8), Triple(2, 4, 6),
        )

        private const val GRID_THICKNESS_RATIO: Float = 0.04f
        private val GRID_COLOR: Color = Color(0.9f, 0.9f, 0.9f)
        private const val MARK_INSET_RATIO: Float = 0.18f
        private const val MARK_THICKNESS_RATIO: Float = 0.08f
        private val MARK_COLOR: Color = Color.WHITE
        private val GHOST_COLOR: Color = Color(1f, 1f, 1f, 0.3f)
        private const val WIN_THICKNESS_RATIO: Float = 0.12f
        private val WIN_COLOR: Color = Color(1f, 0.85f, 0.15f, 0.9f)
    }
}
