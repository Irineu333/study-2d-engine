package com.neoutils.engine.games.tictactoe

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.scene.Scene
import com.neoutils.engine.scene.Text

class TicTacToeScene(
    defaultWidth: Float = 800f,
    defaultHeight: Float = 600f,
) : Scene() {

    val board: Board = Board().apply { name = "board" }

    val status: Text = Text(
        text = statusFor(board),
        size = STATUS_TEXT_SIZE,
        color = Color.WHITE,
    ).apply { name = "status" }

    init {
        name = "TicTacToeScene"
        addChild(board)
        addChild(status)
        layout(defaultWidth, defaultHeight)
    }

    override fun onResize(width: Float, height: Float) {
        layout(width, height)
    }

    override fun onUpdate(dt: Float) {
        status.text = statusFor(board)
    }

    private fun layout(width: Float, height: Float) {
        val availableHeight = (height - STATUS_RESERVED).coerceAtLeast(0f)
        val side = minOf(width, availableHeight).coerceAtLeast(0f)
        board.cellSize = side / 3f
        val boardSide = board.cellSize * 3f
        val originX = (width - boardSide) / 2f
        val originY = STATUS_RESERVED + (availableHeight - boardSide) / 2f
        board.origin = Vec2(originX, originY)
        status.transform = status.transform.copy(
            position = Vec2(width / 2f - STATUS_TEXT_OFFSET_X, STATUS_BASELINE_Y)
        )
    }

    companion object {
        private const val STATUS_TEXT_SIZE: Float = 22f
        private const val STATUS_RESERVED: Float = 60f
        private const val STATUS_TEXT_OFFSET_X: Float = 180f
        private const val STATUS_BASELINE_Y: Float = 16f
    }
}

internal fun statusFor(board: Board): String = when {
    board.winner != null -> "${board.winner} venceu — clique para jogar de novo"
    board.isDraw -> "Empate — clique para jogar de novo"
    else -> "Vez de ${board.currentPlayer}"
}
