package com.neoutils.engine.games.tictactoe

enum class Mark {
    X, O;

    fun other(): Mark = if (this == X) O else X
}
