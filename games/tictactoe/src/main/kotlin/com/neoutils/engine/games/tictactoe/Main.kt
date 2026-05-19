package com.neoutils.engine.games.tictactoe

import com.neoutils.engine.compose.ComposeHost
import com.neoutils.engine.runtime.GameConfig

fun main() {
    ComposeHost().run(
        scene = TicTacToeScene(),
        config = GameConfig(title = "Tic Tac Toe", width = 600, height = 600),
    )
}
