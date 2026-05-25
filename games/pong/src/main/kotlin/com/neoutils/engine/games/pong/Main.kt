package com.neoutils.engine.games.pong

import com.neoutils.engine.bundle.BundleLoader
import com.neoutils.engine.bundle.python.PythonScriptHost
import com.neoutils.engine.runtime.GameConfig
import com.neoutils.engine.skiko.SkikoHost
import com.neoutils.engine.tree.SceneTree
import java.io.File

fun main(args: Array<String>) {
    val python = PythonScriptHost.create()
    val root = when (val path = args.firstOrNull()) {
        null -> BundleLoader.fromResources("pong", scripting = python)
        else -> BundleLoader.fromPath(File(path), scripting = python)
    }
    SkikoHost().run(SceneTree(root = root), GameConfig(title = "Pong", width = 800, height = 600))
}
