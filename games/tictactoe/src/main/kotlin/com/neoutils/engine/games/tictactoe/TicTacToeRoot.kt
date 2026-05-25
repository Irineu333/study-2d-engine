package com.neoutils.engine.games.tictactoe

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.scene.AspectMode
import com.neoutils.engine.scene.Camera2D
import com.neoutils.engine.scene.Node
import kotlinx.serialization.Serializable

@Serializable
class TicTacToeRoot : Node() {

    init {
        name = "TicTacToeRoot"
    }

    override fun onEnter() {
        super.onEnter()
        if (children.isNotEmpty()) return
        // FIT camera over a fixed 600×600 world; the whole composition
        // (board + status text) scales as one when the surface resizes,
        // and a single Camera2D in a Compose-backed game is the smoke
        // test that the view-transform stack works on both backends.
        addChild(
            Camera2D().apply {
                name = "MainCamera"
                bounds = Rect(Vec2.ZERO, Vec2(WORLD_WIDTH, WORLD_HEIGHT))
                current = true
                aspectMode = AspectMode.FIT
            },
        )
        addChild(
            Board().apply {
                name = "board"
                cellSize = BOARD_SIDE / 3f
                origin = Vec2((WORLD_WIDTH - BOARD_SIDE) / 2f, BOARD_ORIGIN_Y)
            },
        )
        addChild(
            StatusText().apply {
                name = "status"
                size = STATUS_TEXT_SIZE
                color = Color.WHITE
                baselineY = STATUS_BASELINE_Y
            },
        )
    }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        val board = findChild("board") as? Board ?: return
        val status = findChild("status") as? StatusText ?: return
        status.text = statusFor(board)
    }

    companion object {
        const val WORLD_WIDTH: Float = 600f
        const val WORLD_HEIGHT: Float = 600f
        const val STATUS_TEXT_SIZE: Float = 22f
        const val STATUS_RESERVED: Float = 60f
        const val STATUS_BASELINE_Y: Float = 16f
        const val BOARD_SIDE: Float = WORLD_HEIGHT - STATUS_RESERVED
        const val BOARD_ORIGIN_Y: Float = STATUS_RESERVED
    }
}

internal fun statusFor(board: Board): String = when {
    board.winner != null -> "${board.winner} venceu — clique para jogar de novo"
    board.isDraw -> "Empate — clique para jogar de novo"
    else -> "Vez de ${board.currentPlayer}"
}
