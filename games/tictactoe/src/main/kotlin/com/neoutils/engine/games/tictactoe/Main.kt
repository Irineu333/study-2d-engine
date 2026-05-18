package com.neoutils.engine.games.tictactoe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.neoutils.engine.compose.GameSurface
import com.neoutils.engine.scene.Scene

fun main() = application {
    val state = rememberWindowState(width = 800.dp, height = 600.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "Velha",
        state = state,
    ) {
        val scene = remember { Scene() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            GameSurface(
                scene = scene,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
