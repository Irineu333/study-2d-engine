package com.neoutils.engine.games.pong

import com.neoutils.engine.runtime.GameConfig
import com.neoutils.engine.scene.Scene
import com.neoutils.engine.serialization.NodeRegistry
import com.neoutils.engine.serialization.SceneLoader
import com.neoutils.engine.skiko.SkikoHost
import com.neoutils.engine.scripting.KotlinScriptingHost
import com.neoutils.engine.scripting.ScriptHosts

private const val SCENE_RESOURCE = "pong.scene.json"

fun main() {
    registerPongTypes()
    val scriptingHost = KotlinScriptingHost(
        manifest = listOf(
            "scripts/goal.nengine.kts",
            "scripts/score.nengine.kts",
            "scripts/center-line.nengine.kts",
            "scripts/ball.nengine.kts",
            "scripts/paddle.nengine.kts",
            "scripts/pong-scene.nengine.kts"
        ),
        cacheDir = java.io.File("build/scripting-cache").absoluteFile
    )
    ScriptHosts.register(scriptingHost)
    val scene = loadScene()
    SkikoHost().run(scene, GameConfig(title = "Pong", width = 800, height = 600))
}

private fun loadScene(): Scene {
    val text = checkNotNull(
        Scene::class.java.classLoader.getResource(SCENE_RESOURCE)
    ) { "Resource not found on classpath: $SCENE_RESOURCE" }.readText()
    return SceneLoader.load(text)
}

internal fun registerPongTypes() {
    NodeRegistry.registerEngineTypes()
}
