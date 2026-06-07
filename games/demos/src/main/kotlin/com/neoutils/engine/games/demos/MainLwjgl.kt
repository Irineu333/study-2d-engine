package com.neoutils.engine.games.demos

import com.neoutils.engine.lwjgl.LwjglHost
import com.neoutils.engine.runtime.GameConfig
import com.neoutils.engine.tree.SceneTree

fun main() {
    val tree = SceneTree(root = DemoSwitcherRoot())
    tree.start()
    LwjglHost().run(
        tree = tree,
        config = GameConfig(title = "engine-consistency demos", width = 800, height = 600),
    )
}
