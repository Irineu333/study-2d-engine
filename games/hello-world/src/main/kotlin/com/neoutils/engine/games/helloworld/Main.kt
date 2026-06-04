package com.neoutils.engine.games.helloworld

import com.neoutils.engine.render.Color
import com.neoutils.engine.runtime.GameConfig
import com.neoutils.engine.scene.CanvasLayer
import com.neoutils.engine.skiko.SkikoHost
import com.neoutils.engine.tree.SceneTree

fun main() {
    val label = CenteredLabel().apply {
        text = "Hello, world!"
        fontSize = 32f
        color = Color.WHITE
    }
    val hud = CanvasLayer().apply {
        name = "Hud"
        addChild(label)
    }

    SkikoHost().run(
        tree = SceneTree(root = hud),
        config = GameConfig(
            title = "Hello, world!",
            width = 800,
            height = 600
        ),
    )
}
