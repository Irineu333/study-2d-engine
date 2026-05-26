package com.neoutils.engine.games.demos

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.PhysicsBody2D
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.physics.RigidBody2D
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Circle2D
import com.neoutils.engine.scene.Node2D
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
        val tree = tree ?: return
        val w = tree.width
        val h = tree.height
        // BoundaryWalls is the arena container: walls + balls share it as
        // parent so moveAndCollide's same-parent sweep can find them, and the
        // four walls keep tracking tree.size in real time during resize.
        val arena = BoundaryWalls().also { addChild(it) }
        repeat(BALL_COUNT) { i ->
            val px = BALL_SIZE + rng.nextFloat() * (w - BALL_SIZE * 2)
            val py = BALL_SIZE + rng.nextFloat() * (h - BALL_SIZE * 2)
            val speed = 80f + rng.nextFloat() * 120f
            val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
            arena.addChild(
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

    override fun onProcess(dt: Float) {
        if (dt > 0f) instantFps = 1f / dt
    }

    override fun onDraw(renderer: Renderer) {
        val text = "balls: $BALL_COUNT | fps: ${instantFps.roundToInt()}"
        val textSize = 14f
        val sceneW = tree?.width ?: 800f
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
) : RigidBody2D() {

    @Transient
    private val baseColor: Color = color

    @Transient
    internal var flashTimer: Float = 0f

    init {
        transform = Transform(position = initPos)
        // Elastic, frictionless: visually equivalent to the previous
        // CharacterBody2D + reflect-on-normal behaviour, but the engine
        // solver now handles ball-vs-ball momentum transfer correctly.
        restitution = 1f
        friction = 0f
        linearVelocity = Vec2(initVx, initVy)
        addChild(
            CollisionShape2D().apply {
                // Center the rect on the body's position so the rigid-body
                // solver's center of mass matches the visual.
                transform = Transform(position = Vec2(-BALL_SIZE / 2f, -BALL_SIZE / 2f))
                shape = RectangleShape2D().apply { size = Vec2(BALL_SIZE, BALL_SIZE) }
            }
        )
        addChild(
            Circle2D().apply {
                name = "art"
                radius = BALL_SIZE / 2f
                this.color = color
            }
        )
        bodyEntered.connect { other -> onContactFlash(other) }
    }

    private fun onContactFlash(other: PhysicsBody2D) {
        setArtColor(Color.WHITE)
        flashTimer = 0.15f
        if (other is Ball) {
            other.setArtColor(Color.WHITE)
            other.flashTimer = 0.15f
        }
    }

    override fun onProcess(dt: Float) {
        if (flashTimer > 0f) {
            flashTimer -= dt
            if (flashTimer <= 0f) setArtColor(baseColor)
        }
    }

    internal fun setArtColor(c: Color) {
        (findChild("art") as? Circle2D)?.color = c
    }
}
