package com.neoutils.engine.games.pong

import com.neoutils.engine.bundle.BundleLoader
import com.neoutils.engine.bundle.python.PythonScriptHost
import com.neoutils.engine.lwjgl.LwjglHost
import com.neoutils.engine.runtime.GameConfig
import com.neoutils.engine.tree.SceneTree
import java.io.File

/**
 * LWJGL entry point for Pong — same bundle and scripting as the Skiko [main],
 * only the host differs. Proves render and the rest of the engine (physics,
 * Python scripting, audio) are orthogonal to the render backend: the exact same
 * `pong` bundle runs under GLFW + OpenGL. Launch via the `runLwjgl` Gradle task
 * (it injects `-XstartOnFirstThread` on macOS, required by GLFW/NSApp).
 */
fun main(args: Array<String>) {
    val python = PythonScriptHost.create()
    val root = when (val path = args.firstOrNull()) {
        null -> BundleLoader.fromResources("pong", scripting = python)
        else -> BundleLoader.fromPath(File(path), scripting = python)
    }
    LwjglHost().run(SceneTree(root = root), GameConfig(title = "Pong", width = 800, height = 600))
}
