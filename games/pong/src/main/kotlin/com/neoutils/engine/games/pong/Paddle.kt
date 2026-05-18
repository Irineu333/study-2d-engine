package com.neoutils.engine.games.pong

import com.neoutils.engine.input.Key
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node2D

class Paddle(
    val size: Vec2 = Vec2(WIDTH, HEIGHT),
    var playFieldHeight: Float,
    var upKey: Key? = null,
    var downKey: Key? = null,
    var ai: Boolean = false,
    val speed: Float = 360f,
    val aiMaxSpeed: Float = 220f,
    val aiTolerance: Float = 8f,
    var aiTargetY: (() -> Float)? = null,
) : Node2D() {

    val collider: PaddleCollider = PaddleCollider(size)

    init {
        addChild(collider)
    }

    override fun onUpdate(dt: Float) {
        val dy = if (ai) computeAi(dt) else computeHuman(dt)
        if (dy == 0f) return
        val newY = (transform.position.y + dy).coerceIn(0f, playFieldHeight - size.y)
        transform = transform.copy(position = transform.position.copy(y = newY))
    }

    private fun computeHuman(dt: Float): Float {
        val input = rootScene()?.input ?: return 0f
        var direction = 0f
        upKey?.let { if (input.isKeyDown(it)) direction -= 1f }
        downKey?.let { if (input.isKeyDown(it)) direction += 1f }
        return direction * speed * dt
    }

    private fun computeAi(dt: Float): Float {
        val targetY = aiTargetY?.invoke() ?: return 0f
        val center = transform.position.y + size.y / 2f
        val delta = targetY - center
        val direction = when {
            delta > aiTolerance -> 1f
            delta < -aiTolerance -> -1f
            else -> 0f
        }
        return direction * aiMaxSpeed * dt
    }

    override fun onRender(renderer: Renderer) {
        renderer.drawRect(Rect(worldPosition(), size), Color.WHITE, filled = true)
    }

    companion object {
        const val WIDTH = 16f
        const val HEIGHT = 96f
    }
}
