package com.neoutils.engine.games.demos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key as ComposeKey
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.neoutils.engine.compose.GameSurface
import com.neoutils.engine.dx.Debug

fun main() = application {
    val state = rememberWindowState(width = 800.dp, height = 600.dp)
    val scene = remember { DemoSwitcherScene() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "engine-consistency demos",
        state = state,
        onKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) return@Window false
            when (event.key) {
                ComposeKey.F1 -> { Debug.showFps = !Debug.showFps; true }
                ComposeKey.F2 -> { Debug.colliderVisualization = !Debug.colliderVisualization; true }
                else -> false
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            GameSurface(scene = scene, modifier = Modifier.fillMaxSize())
        }
    }
}
