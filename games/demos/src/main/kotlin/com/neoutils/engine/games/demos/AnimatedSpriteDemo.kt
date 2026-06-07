package com.neoutils.engine.games.demos

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.AnimatedSprite2D
import com.neoutils.engine.scene.Node2D
import kotlinx.serialization.Serializable

/**
 * Sprite-animation sentinel: a single `AnimatedSprite2D` cycling a 12-frame
 * horizontal sheet (384x32). The engine advances the frame in `onProcess`, so
 * the dot visibly orbits and the progress bar grows over time — frozen output
 * means a broken advance. Scaled up so nearest-neighbor stays crisp. Runs
 * identically on Skiko (default) and LWJGL (`runLwjgl`).
 */
@Serializable
class AnimatedSpriteDemo : Node2D() {

    init {
        name = "AnimatedSpriteDemo"
    }

    override fun onEnter() {
        val tree = tree ?: return
        addChild(
            AnimatedSprite2D().apply {
                texturePath = "demos/sprites/spin.png"
                frameCount = 12
                fps = 12f
                transform = Transform(
                    position = Vec2(tree.width / 2f, tree.height / 2f),
                    scale = Vec2(8f, 8f),
                )
            },
        )
    }

    override fun onDraw(renderer: Renderer) {
        renderer.drawText(
            "AnimatedSprite2D (12 frames @ 12fps, engine-driven) — same on Skiko + LWJGL",
            Vec2(8f, tree?.height?.minus(24f) ?: 576f),
            size = 14f,
            color = Color(1f, 1f, 1f, 0.85f),
        )
    }
}
