package com.neoutils.engine.runtime

import com.neoutils.engine.input.Key

data class GameConfig(
    val title: String = "Game",
    val width: Int = 800,
    val height: Int = 600,
    val toggleFpsKey: Key = Key.F1,
    val toggleCollidersKey: Key = Key.F2,
)
