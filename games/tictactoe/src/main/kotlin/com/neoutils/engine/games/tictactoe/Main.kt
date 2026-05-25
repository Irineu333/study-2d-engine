package com.neoutils.engine.games.tictactoe

import com.neoutils.engine.compose.ComposeHost
import com.neoutils.engine.runtime.GameConfig
import com.neoutils.engine.tree.SceneTree

fun main() {
    ComposeHost().run(
        tree = SceneTree(root = TicTacToeRoot()),
        config = GameConfig(title = "Tic Tac Toe", width = 600, height = 600),
    )
}
