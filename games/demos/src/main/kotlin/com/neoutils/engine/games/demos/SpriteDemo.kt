package com.neoutils.engine.games.demos

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.scene.Sprite2D
import kotlinx.serialization.Serializable

/**
 * Texture-rendering sentinel: a single `Sprite2D` drawn centered and scaled up
 * so nearest-neighbor sampling is obvious (no blur). Runs identically on Skiko
 * (default) and LWJGL (`runLwjgl`), proving `Renderer.drawImage` + `tree.textures`
 * end-to-end before any animation/tilemap exists.
 */
@Serializable
class SpriteDemo : Node2D() {

    init {
        name = "SpriteDemo"
    }

    override fun onEnter() {
        val tree = tree ?: return
        // Centered on screen, scaled 8x so the 32x32 pixel-art stays crisp when
        // enlarged — the whole point of nearest-neighbor.
        addChild(
            Sprite2D().apply {
                texturePath = "demos/sprites/idle.png"
                transform = Transform(
                    position = Vec2(tree.width / 2f, tree.height / 2f),
                    scale = Vec2(8f, 8f),
                )
            },
        )
    }

    override fun onDraw(renderer: Renderer) {
        renderer.drawText(
            "Sprite2D (nearest-neighbor, 8x) — same on Skiko + LWJGL",
            Vec2(8f, tree?.height?.minus(24f) ?: 576f),
            size = 14f,
            color = Color(1f, 1f, 1f, 0.85f),
        )
    }
}
