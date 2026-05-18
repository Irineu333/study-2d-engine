package com.neoutils.engine.games.pong

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.BoxCollider
import com.neoutils.engine.physics.Collider
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node2D
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Ball(
    val size: Float = 16f,
    val initialSpeed: Float = 280f,
    val maxSpeed: Float = 560f,
    val speedupPerHit: Float = 1.05f,
    var fieldCenter: Vec2,
    private val random: Random = Random.Default,
    private val onScore: (Goal.Side) -> Unit = {},
) : Node2D() {

    var velocity: Vec2 = Vec2.ZERO
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
        val center = worldPosition() + Vec2(size / 2f, size / 2f)
        renderer.drawCircle(center, radius = size / 2f, color = Color.WHITE, filled = true)
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
                val paddleBounds = other.bounds()
                val paddleCenterY = paddleBounds.top + paddleBounds.size.y / 2f
                val ballCenterY = transform.position.y + size / 2f
                val relative = ((ballCenterY - paddleCenterY) /
                    (paddleBounds.size.y / 2f)).coerceIn(-1f, 1f)

                val newSpeed = (velocity.length * speedupPerHit).coerceAtMost(maxSpeed)
                val horizontalSign = if (velocity.x > 0f) -1f else 1f
                val maxAngleRad = (PI / 3f).toFloat() // ±60°
                val angle = relative * maxAngleRad
                velocity = Vec2(
                    horizontalSign * newSpeed * cos(angle),
                    newSpeed * sin(angle),
                )

                // Nudge ball out of paddle to prevent re-collision next tick.
                val ballPos = transform.position
                val ballRight = ballPos.x + size
                val ballLeft = ballPos.x
                val shift = if (horizontalSign < 0f) paddleBounds.left - ballRight - 0.5f
                else paddleBounds.right - ballLeft + 0.5f
                transform = transform.copy(position = ballPos.copy(x = ballPos.x + shift))
            }
        }
    }

    fun reset(serveToward: Float) {
        transform = transform.copy(position = fieldCenter - Vec2(size / 2f, size / 2f))
        // Wider angle range so the AI paddle has visible vertical work to do
        // and the ball doesn't degenerate into a horizontal line.
        val angle = (random.nextFloat() - 0.5f) * 1.4f // ~ ±0.7 rad (~40deg)
        val sx = if (serveToward >= 0f) 1f else -1f
        velocity = Vec2(
            sx * initialSpeed * cos(angle),
            initialSpeed * sin(angle),
        )
    }
}
