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

    private fun layout(width: Float, height: Float) {
        // Placeholder layout — step 7.2 replaces this with the responsive
        // calculation that reserves space for status text and centers the
        // board on the smaller axis.
        val available = (minOf(width, height) - STATUS_RESERVED).coerceAtLeast(60f)
        board.cellSize = available / 3f
        val boardSide = board.cellSize * 3f
        board.origin = Vec2((width - boardSide) / 2f, STATUS_RESERVED + (height - STATUS_RESERVED - boardSide) / 2f)
        status.transform = status.transform.copy(position = Vec2(width / 2f - 60f, 16f))
    }

    companion object {
        private const val STATUS_TEXT_SIZE: Float = 22f
        private const val STATUS_RESERVED: Float = 60f
    }
}

internal fun statusFor(board: Board): String = when {
    board.winner != null -> "${board.winner} venceu — clique para jogar de novo"
    board.isDraw -> "Empate — clique para jogar de novo"
    else -> "Vez de ${board.currentPlayer}"
}
