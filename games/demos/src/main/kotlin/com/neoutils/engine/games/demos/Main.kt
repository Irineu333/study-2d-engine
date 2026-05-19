package com.neoutils.engine.games.demos

import com.neoutils.engine.runtime.GameConfig
import com.neoutils.engine.skiko.SkikoHost

fun main() {
    SkikoHost().run(
        scene = DemoSwitcherScene(),
        config = GameConfig(title = "engine-consistency demos", width = 800, height = 600),
    )
}
