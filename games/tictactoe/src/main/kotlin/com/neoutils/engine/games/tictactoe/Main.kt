package com.neoutils.engine.games.tictactoe

import com.neoutils.engine.bundle.BundleLoader
import com.neoutils.engine.bundle.python.PythonScriptHost
import com.neoutils.engine.compose.ComposeHost
import com.neoutils.engine.runtime.GameConfig
import com.neoutils.engine.tree.SceneTree

fun main() {
    val python = PythonScriptHost.create()
    val root = BundleLoader.fromResources("tictactoe", scripting = python)
    ComposeHost().run(
        tree = SceneTree(root = root),
        config = GameConfig(title = "Tic Tac Toe", width = 600, height = 600),
    )
}
