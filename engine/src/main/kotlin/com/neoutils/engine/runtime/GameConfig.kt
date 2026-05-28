package com.neoutils.engine.runtime

import com.neoutils.engine.input.Key

data class GameConfig(
    val title: String = "Game",
    val width: Int = 800,
    val height: Int = 600,
    val debugHudKey: Key = Key.F1,
    val physicsHz: Int = 60,
)
