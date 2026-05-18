package com.neoutils.engine.games.pong

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.BoxCollider
import com.neoutils.engine.physics.Collider
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node2D
import kotlin.math.sign
import kotlin.random.Random

class Ball(
    val size: Float = 16f,
    val initialSpeed: Float = 280f,
    val maxSpeed: Float = 560f,
    val speedupPerHit: Float = 1.05f,
    val fieldCenter: Vec2,
    private val random: Random = Random.Default,
    private val onScore: (Goal.Side) -> Unit = {},
) : Node2D() {

    var velocity: Vec2 = randomVelocity(initialSpeed, random)
        private set

    val collider: BoxCollider = object : BoxCollider(Vec2(size, size)) {
        override fun onCollide(other: Collider) {
            handleCollision(other)
        }
    }

    init {
        addChild(collider)
        reset(serveToward = if (random.nextBoolean()) 1f else -1f)
    }

    override fun onUpdate(dt: Float) {
        transform = transform.copy(position = transform.position + velocity * dt)
    }

    override fun onRender(renderer: Renderer) {
        renderer.drawRect(Rect(transform.position, Vec2(size, size)), Color.WHITE, filled = true)
    }

    private fun handleCollision(other: Collider) {
        when {
            other is Goal -> {
                val scorer = if (other.side == Goal.Side.Left) Goal.Side.Right else Goal.Side.Left
                onScore(scorer)
                reset(serveToward = if (other.side == Goal.Side.Left) 1f else -1f)
            }
            other is Wall -> velocity = velocity.copy(y = -velocity.y)
            other is PaddleCollider -> {
                val newX = -velocity.x
                val faster = (Vec2(newX, velocity.y) * speedupPerHit)
                velocity = clampSpeed(faster, maxSpeed)
                // Nudge ball out of paddle to prevent re-collision next tick.
                val paddleBounds = other.bounds()
                val ballPos = transform.position
                val ballRight = ballPos.x + size
                val ballLeft = ballPos.x
                val shift = if (newX < 0f) paddleBounds.left - ballRight - 0.5f
                else paddleBounds.right - ballLeft + 0.5f
                transform = transform.copy(position = ballPos.copy(x = ballPos.x + shift))
            }
        }
    }

    fun reset(serveToward: Float) {
        transform = transform.copy(position = fieldCenter - Vec2(size / 2f, size / 2f))
        val angle = (random.nextFloat() - 0.5f) * 0.6f // ~ ±0.3 rad
        val sx = if (serveToward >= 0f) 1f else -1f
        velocity = Vec2(
            sx * initialSpeed * kotlin.math.cos(angle),
            initialSpeed * kotlin.math.sin(angle),
        )
    }

    private fun clampSpeed(v: Vec2, max: Float): Vec2 {
        val l = v.length
        if (l <= max) return v
        val s = max / l
        return Vec2(v.x * s, v.y * s)
    }
}

private fun randomVelocity(speed: Float, random: Random): Vec2 {
    val angle = (random.nextFloat() - 0.5f) * 0.6f
    val sx = if (random.nextBoolean()) 1f else -1f
    return Vec2(sx * speed * kotlin.math.cos(angle), speed * kotlin.math.sin(angle))
        .let { Vec2(sign(it.x).let { s -> if (s == 0f) 1f else s } * kotlin.math.abs(it.x), it.y) }
}
