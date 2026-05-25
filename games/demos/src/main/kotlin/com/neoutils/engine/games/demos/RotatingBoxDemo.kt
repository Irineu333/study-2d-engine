package com.neoutils.engine.games.demos

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.Area2D
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node2D
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private const val BOX_SIZE = 280f
private const val HALF_BOX = BOX_SIZE / 2f
private const val ANGULAR_VELOCITY = 0.4f
private const val BALL_COUNT = 12
private const val BALL_SIZE = 18f

@Serializable
class RotatingBoxDemo : Node2D() {

    @Transient
    private val rng = Random(0xBADB0F)

    @Transient
    private var instantFps: Float = 0f

    init {
        name = "RotatingBoxDemo"
    }

    override fun onEnter() {
        val tree = tree ?: return
        val wrapper = RotatingBox().apply {
            transform = Transform(position = Vec2(tree.width / 2f, tree.height / 2f))
        }
        addChild(wrapper)
        repeat(BALL_COUNT) { i ->
            val px = -HALF_BOX + BALL_SIZE + rng.nextFloat() * (BOX_SIZE - 2f * BALL_SIZE)
            val py = -HALF_BOX + BALL_SIZE + rng.nextFloat() * (BOX_SIZE - 2f * BALL_SIZE)
            val speed = 80f + rng.nextFloat() * 80f
            val angle = rng.nextFloat() * 2f * kotlin.math.PI.toFloat()
            wrapper.addChild(
                BoxedBall(
                    id = i,
                    color = hue(i.toFloat() / BALL_COUNT),
                    initLocalPos = Vec2(px, py),
                    initVx = cos(angle) * speed,
                    initVy = sin(angle) * speed,
                ).apply { name = "BoxedBall$i" }
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

@Serializable
class RotatingBox : Node2D() {

    @Transient
    internal var vx: Float = 90f

    @Transient
    internal var vy: Float = 70f

    init {
        name = "RotatingBox"
    }

    // Single transform assignment per frame so the world-transform cache
    // invalidation cascade fires once (rotation + translation in one go).
    override fun onProcess(dt: Float) {
        val tree = tree ?: return
        val newRotation = transform.rotation + ANGULAR_VELOCITY * dt
        val c = cos(newRotation)
        val s = sin(newRotation)
        // AABB envelope of a rotated square: max half-extent on either axis
        // is `half * (|cos| + |sin|)`.
        val halfExtent = HALF_BOX * (abs(c) + abs(s))

        var nx = transform.position.x + vx * dt
        var ny = transform.position.y + vy * dt
        if (nx - halfExtent < 0f) { nx = halfExtent; vx = -vx }
        if (nx + halfExtent > tree.width) { nx = tree.width - halfExtent; vx = -vx }
        if (ny - halfExtent < 0f) { ny = halfExtent; vy = -vy }
        if (ny + halfExtent > tree.height) { ny = tree.height - halfExtent; vy = -vy }

        transform = Transform(position = Vec2(nx, ny), rotation = newRotation)
    }

    override fun onDraw(renderer: Renderer) {
        val world = world()
        val c = cos(world.rotation)
        val s = sin(world.rotation)
        val cx = world.position.x
        val cy = world.position.y
        val locals = arrayOf(
            Vec2(-HALF_BOX, -HALF_BOX),
            Vec2(HALF_BOX, -HALF_BOX),
            Vec2(HALF_BOX, HALF_BOX),
            Vec2(-HALF_BOX, HALF_BOX),
        )
        val worldCorners = Array(4) { i ->
            val v = locals[i]
            Vec2(v.x * c - v.y * s + cx, v.x * s + v.y * c + cy)
        }
        val outline = Color(1f, 1f, 1f, 0.7f)
        for (i in 0 until 4) {
            renderer.drawLine(
                from = worldCorners[i],
                to = worldCorners[(i + 1) % 4],
                thickness = 2f,
                color = outline,
            )
        }
        super.onDraw(renderer)
    }
}

class BoxedBall(
    val id: Int,
    color: Color,
    initLocalPos: Vec2,
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
    private var currentColor: Color = color

    @Transient
    internal var flashTimer: Float = 0f

    init {
        transform = Transform(position = initLocalPos)
        addChild(
            CollisionShape2D().apply {
                shape = RectangleShape2D().apply { size = Vec2(BALL_SIZE, BALL_SIZE) }
            }
        )
    }

    // Custom render so the circle center honors the wrapper's rotation. The
    // built-in `Shape` would place the circle at `world.position + (size/2)`
    // in world axes — a known limitation (see `Shape` KDoc) that visually
    // shifts the ball by up to `BALL_SIZE` from its logical local position
    // when the parent rotates, leaking through the box outline.
    override fun onDraw(renderer: Renderer) {
        val world = world()
        val c = cos(world.rotation)
        val s = sin(world.rotation)
        val ox = BALL_SIZE / 2f
        val oy = BALL_SIZE / 2f
        renderer.drawCircle(
            center = Vec2(
                world.position.x + (ox * c - oy * s),
                world.position.y + (ox * s + oy * c),
            ),
            radius = BALL_SIZE / 2f,
            color = currentColor,
            filled = true,
        )
        super.onDraw(renderer)
    }

    override fun onProcess(dt: Float) {
        val min = -HALF_BOX
        val max = HALF_BOX - BALL_SIZE
        var nx = transform.position.x + vx * dt
        var ny = transform.position.y + vy * dt
        if (nx < min) { nx = min; vx = -vx }
        if (nx > max) { nx = max; vx = -vx }
        if (ny < min) { ny = min; vy = -vy }
        if (ny > max) { ny = max; vy = -vy }
        transform = transform.copy(position = Vec2(nx, ny))

        if (flashTimer > 0f) {
            flashTimer -= dt
            if (flashTimer <= 0f) setArtColor(baseColor)
        }
    }

    // Broad phase produces false positives here because every ball shares the
    // wrapper's world rotation, so AABBs of rotated squares balloon and overlap
    // before the squares themselves touch. We re-check overlap in the local
    // frame — siblings under the same parent, so positions are directly
    // comparable — and bail out if it was a phantom contact.
    override fun onAreaEntered(area: Area2D) {
        if (area !is BoxedBall) return
        if (area.id <= id) return

        val posA = transform.position
        val posB = area.transform.position
        val dx = posA.x - posB.x
        val dy = posA.y - posB.y
        val overlapX = BALL_SIZE - abs(dx)
        val overlapY = BALL_SIZE - abs(dy)
        if (overlapX <= 0f || overlapY <= 0f) return

        val min = -HALF_BOX
        val max = HALF_BOX - BALL_SIZE
        if (overlapX < overlapY) {
            val push = overlapX * 0.5f * if (dx >= 0f) 1f else -1f
            transform = transform.copy(
                position = Vec2((posA.x + push).coerceIn(min, max), posA.y),
            )
            area.transform = area.transform.copy(
                position = Vec2((posB.x - push).coerceIn(min, max), posB.y),
            )
        } else {
            val push = overlapY * 0.5f * if (dy >= 0f) 1f else -1f
            transform = transform.copy(
                position = Vec2(posA.x, (posA.y + push).coerceIn(min, max)),
            )
            area.transform = area.transform.copy(
                position = Vec2(posB.x, (posB.y - push).coerceIn(min, max)),
            )
        }

        if (flashTimer > 0f || area.flashTimer > 0f) return

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

    internal fun setArtColor(c: Color) {
        currentColor = c
    }
}
