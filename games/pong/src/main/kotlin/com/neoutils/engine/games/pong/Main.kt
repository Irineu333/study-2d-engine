package com.neoutils.engine.games.pong

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.neoutils.engine.compose.GameSurface
import com.neoutils.engine.dx.Debug

fun main() = application {
    val state: WindowState = rememberWindowState(width = 800.dp, height = 600.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "Pong",
        state = state,
        onKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown) {
                when (event.key) {
                    androidx.compose.ui.input.key.Key.F1 -> { Debug.showFps = !Debug.showFps; true }
                    androidx.compose.ui.input.key.Key.F2 -> { Debug.colliderVisualization = !Debug.colliderVisualization; true }
                    else -> false
                }
            } else false
        },
    ) {
        val scene = remember { PongScene() }
        LaunchedEffect(Unit) {
            // Default DX toggles off; F1/F2 to toggle at runtime.
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            GameSurface(scene = scene, modifier = Modifier.fillMaxSize())
        }
    }
}
