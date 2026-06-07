package com.neoutils.engine.games.demos

import com.neoutils.engine.runtime.GameConfig
import com.neoutils.engine.skiko.SkikoHost
import com.neoutils.engine.tree.SceneTree

fun main() {
    val tree = SceneTree(root = DemoSwitcherRoot())
    tree.start()
    SkikoHost().run(
        tree = tree,
        config = GameConfig(title = "demos", width = 800, height = 600),
    )
}
