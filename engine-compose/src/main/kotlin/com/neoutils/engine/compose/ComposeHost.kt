package com.neoutils.engine.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.neoutils.engine.runtime.GameConfig
import com.neoutils.engine.runtime.GameHost
import com.neoutils.engine.scene.Scene

/**
 * `GameHost` implementation backed by Compose Multiplatform Desktop. Opens an
 * `application { Window { } }` and hosts `GameSurface` inside it. Returns from
 * `run(...)` only after the Window is closed, because Compose's `application`
 * itself is blocking.
 */
class ComposeHost : GameHost {

    override fun run(scene: Scene, config: GameConfig) {
        application {
            val state = rememberWindowState(
                width = config.width.dp,
                height = config.height.dp,
            )
            Window(
                onCloseRequest = ::exitApplication,
                title = config.title,
                state = state,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    GameSurface(
                        scene = scene,
                        modifier = Modifier.fillMaxSize(),
                        config = config,
                    )
                }
            }
        }
    }
}
