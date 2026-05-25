package com.neoutils.engine.games.helloworld

import com.neoutils.engine.render.Color
import com.neoutils.engine.runtime.GameConfig
import com.neoutils.engine.skiko.SkikoHost
import com.neoutils.engine.tree.SceneTree

fun main() {
    val label = CenteredLabel().apply {
        text = "Hello, world!"
        size = 32f
        color = Color.WHITE
    }

    SkikoHost().run(
        tree = SceneTree(root = label),
        config = GameConfig(
            title = "Hello, world!",
            width = 800,
            height = 600
        ),
    )
}
