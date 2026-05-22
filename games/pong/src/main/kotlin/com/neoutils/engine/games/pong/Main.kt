package com.neoutils.engine.games.pong

import com.neoutils.engine.bundle.BundleLoader
import com.neoutils.engine.runtime.GameConfig
import com.neoutils.engine.skiko.SkikoHost
import java.io.File

fun main(args: Array<String>) {
    val scene = when (val path = args.firstOrNull()) {
        null -> BundleLoader.fromResources("pong")
        else -> BundleLoader.fromPath(File(path))
    }
    SkikoHost().run(scene, GameConfig(title = "Pong", width = 800, height = 600))
}
