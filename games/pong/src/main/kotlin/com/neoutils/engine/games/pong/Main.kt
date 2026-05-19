package com.neoutils.engine.games.pong

import com.neoutils.engine.runtime.GameConfig
import com.neoutils.engine.serialization.NodeRegistry
import com.neoutils.engine.skiko.SkikoHost

fun main() {
    registerPongTypes()
    SkikoHost().run(PongScene(), GameConfig(title = "Pong", width = 800, height = 600))
}

internal fun registerPongTypes() {
    NodeRegistry.registerEngineTypes()
    NodeRegistry.register(PongScene::class) { PongScene() }
    NodeRegistry.register(Paddle::class) { Paddle() }
    NodeRegistry.register(PaddleCollider::class) { PaddleCollider() }
    NodeRegistry.register(Ball::class) { Ball() }
    NodeRegistry.register(Wall::class) { Wall() }
    NodeRegistry.register(Goal::class) { Goal() }
    NodeRegistry.register(Score::class) { Score() }
    NodeRegistry.register(CenterLine::class) { CenterLine() }
}
