package com.neoutils.engine.games.demos

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.BoxCollider
import com.neoutils.engine.physics.Collider
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.scene.Shape
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.roundToInt
import kotlin.random.Random

private const val BALL_COUNT = 30
private const val BALL_SIZE = 20f

@Serializable
class CollisionStressDemo : Node2D() {

    @Transient
    private val rng = Random(0xC0FFEE)

    @Transient
    private var instantFps: Float = 0f

    init {
        name = "CollisionStressDemo"
    }

    override fun onEnter() {
        val scene = rootScene() ?: return
        val w = scene.width
        val h = scene.height
        repeat(BALL_COUNT) { i ->
            val px = BALL_SIZE + rng.nextFloat() * (w - BALL_SIZE * 2)
            val py = BALL_SIZE + rng.nextFloat() * (h - BALL_SIZE * 2)
            val speed = 80f + rng.nextFloat() * 120f
            val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
            addChild(
                Ball(
                    id = i,
                    color = hue(i.toFloat() / BALL_COUNT),
                    initPos = Vec2(px, py),
                    initVx = kotlin.math.cos(angle) * speed,
                    initVy = kotlin.math.sin(angle) * speed,
                ).apply { name = "Ball$i" }
            )
        }
    }

    override fun onUpdate(dt: Float) {
        if (dt > 0f) instantFps = 1f / dt
    }

    override fun onRender(renderer: Renderer) {
        val text = "balls: $BALL_COUNT | fps: ${instantFps.roundToInt()}"
        val textSize = 14f
        val sceneW = rootScene()?.width ?: 800f
        val textW = renderer.measureText(text, textSize).x
        renderer.drawText(
            text,
            Vec2(sceneW - textW - 8f, 18f),
            size = textSize,
            color = Color.WHITE,
        )
    }

    private fun hue(h: Float): Color {
        val i = (h * 6f).toInt()
        val f = h * 6f - i
        return when (i % 6) {
            0 -> Color(1f, f, 0f)
            1 -> Color(1f - f, 1f, 0f)
            2 -> Color(0f, 1f, f)
            3 -> Color(0f, 1f - f, 1f)
            4 -> Color(f, 0f, 1f)
            else -> Color(1f, 0f, 1f - f)
        }
    }
}

class Ball(
    val id: Int,
    color: Color,
    initPos: Vec2,
    initVx: Float,
    initVy: Float,
) : BoxCollider() {

    @Transient
    internal var vx: Float = initVx

    @Transient
    internal var vy: Float = initVy

    @Transient
    private val baseColor: Color = color

    @Transient
    internal var flashTimer: Float = 0f

    init {
        size = Vec2(BALL_SIZE, BALL_SIZE)
        transform = Transform(position = initPos)
        addChild(
            Shape().apply {
                name = "art"
                kind = Shape.Kind.Circle
                size = Vec2(BALL_SIZE, BALL_SIZE)
                this.color = color
            }
        )
    }

    override fun onUpdate(dt: Float) {
        val scene = rootScene() ?: return
        var nx = transform.position.x + vx * dt
        var ny = transform.position.y + vy * dt
        if (nx < 0f) { nx = 0f; vx = -vx }
        if (nx + BALL_SIZE > scene.width) { nx = scene.width - BALL_SIZE; vx = -vx }
        if (ny < 0f) { ny = 0f; vy = -vy }
        if (ny + BALL_SIZE > scene.height) { ny = scene.height - BALL_SIZE; vy = -vy }
        transform = transform.copy(position = Vec2(nx, ny))

        if (flashTimer > 0f) {
            flashTimer -= dt
            if (flashTimer <= 0f) setArtColor(baseColor)
        }
    }

    override fun onCollide(other: Collider) {
        if (other !is Ball) return
        // Process each pair only once: the ball with the higher id handles the swap.
        if (other.id <= id) return
        if (flashTimer > 0f || other.flashTimer > 0f) return

        // Elastic collision between equal-mass balls: swap velocities.
        val tvx = vx; val tvy = vy
        vx = other.vx; vy = other.vy
        other.vx = tvx; other.vy = tvy

        setArtColor(Color.WHITE)
        other.setArtColor(Color.WHITE)
        flashTimer = 0.15f
        other.flashTimer = 0.15f
    }

    internal fun setArtColor(c: Color) {
        (findChild("art") as? Shape)?.color = c
    }
}
