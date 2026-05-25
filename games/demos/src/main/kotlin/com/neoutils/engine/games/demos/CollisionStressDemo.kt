package com.neoutils.engine.games.demos

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.Area2D
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.RectangleShape2D
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
) : Area2D() {

    @Transient
    internal var vx: Float = initVx

    @Transient
    internal var vy: Float = initVy

    @Transient
    private val baseColor: Color = color

    @Transient
    internal var flashTimer: Float = 0f

    init {
        transform = Transform(position = initPos)
        addChild(
            CollisionShape2D().apply {
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
    }

    override fun onProcess(dt: Float) {
        val tree = tree ?: return
        var nx = transform.position.x + vx * dt
        var ny = transform.position.y + vy * dt
        if (nx < 0f) { nx = 0f; vx = -vx }
        if (nx + BALL_SIZE > tree.width) { nx = tree.width - BALL_SIZE; vx = -vx }
        if (ny < 0f) { ny = 0f; vy = -vy }
        if (ny + BALL_SIZE > tree.height) { ny = tree.height - BALL_SIZE; vy = -vy }
        transform = transform.copy(position = Vec2(nx, ny))

        if (flashTimer > 0f) {
            flashTimer -= dt
            if (flashTimer <= 0f) setArtColor(baseColor)
        }
    }

    // Enter-only dispatch (single fire per begin-of-overlap) replaces the
    // continuous `onCollide` of pre-overhaul. Position correction in
    // `separate` is enough — after the swap the balls separate, the pair
    // exits, and the next overlap fires `_entered` again.
    override fun onAreaEntered(area: Area2D) {
        if (area !is Ball) return
        if (area.id <= id) return  // each pair handled once

        separate(area)

        if (flashTimer > 0f || area.flashTimer > 0f) return

        val posA = transform.position
        val posB = area.transform.position
        val overlapX = BALL_SIZE - kotlin.math.abs(posA.x - posB.x)
        val overlapY = BALL_SIZE - kotlin.math.abs(posA.y - posB.y)
        if (overlapX < overlapY) {
            val tmp = vx; vx = area.vx; area.vx = tmp
        } else {
            val tmp = vy; vy = area.vy; area.vy = tmp
        }

        setArtColor(Color.WHITE)
        area.setArtColor(Color.WHITE)
        flashTimer = 0.15f
        area.flashTimer = 0.15f
    }

    private fun separate(other: Ball) {
        val posA = transform.position
        val posB = other.transform.position
        val dx = posA.x - posB.x
        val dy = posA.y - posB.y
        val overlapX = BALL_SIZE - kotlin.math.abs(dx)
        val overlapY = BALL_SIZE - kotlin.math.abs(dy)
        if (overlapX <= 0f || overlapY <= 0f) return
        if (overlapX < overlapY) {
            val push = overlapX * 0.5f * if (dx >= 0f) 1f else -1f
            transform = transform.copy(position = Vec2(posA.x + push, posA.y))
            other.transform = other.transform.copy(position = Vec2(posB.x - push, posB.y))
        } else {
            val push = overlapY * 0.5f * if (dy >= 0f) 1f else -1f
            transform = transform.copy(position = Vec2(posA.x, posA.y + push))
            other.transform = other.transform.copy(position = Vec2(posB.x, posB.y - push))
        }
    }

    internal fun setArtColor(c: Color) {
        (findChild("art") as? Circle2D)?.color = c
    }
}
