package com.neoutils.engine.games.demos

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.CharacterBody2D
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.physics.StaticBody2D
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
private const val WALL_THICKNESS = 12f
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
        // 4 walls in the wrapper's *local* frame. moveAndCollide sweeps in the
        // shared parent frame (the wrapper's local space), so walls + balls
        // stay axis-aligned in that frame regardless of the wrapper's rotation
        // in world space — that's the whole reason Demo 5 is shaped this way.
        wrapper.addChild(makeWall(Vec2(-HALF_BOX - WALL_THICKNESS, -HALF_BOX - WALL_THICKNESS), Vec2(BOX_SIZE + 2f * WALL_THICKNESS, WALL_THICKNESS)).apply { name = "topWall" })
        wrapper.addChild(makeWall(Vec2(-HALF_BOX - WALL_THICKNESS, HALF_BOX), Vec2(BOX_SIZE + 2f * WALL_THICKNESS, WALL_THICKNESS)).apply { name = "bottomWall" })
        wrapper.addChild(makeWall(Vec2(-HALF_BOX - WALL_THICKNESS, -HALF_BOX), Vec2(WALL_THICKNESS, BOX_SIZE)).apply { name = "leftWall" })
        wrapper.addChild(makeWall(Vec2(HALF_BOX, -HALF_BOX), Vec2(WALL_THICKNESS, BOX_SIZE)).apply { name = "rightWall" })
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

    private fun makeWall(position: Vec2, size: Vec2): StaticBody2D {
        val body = StaticBody2D().apply { transform = Transform(position = position) }
        body.addChild(
            CollisionShape2D().apply {
                shape = RectangleShape2D().apply { this.size = size }
            }
        )
        return body
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
) : CharacterBody2D() {

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
    // built-in `Circle2D` would place the circle at `world.position + (size/2)`
    // in world axes — a known limitation that visually shifts the ball when
    // the parent rotates, leaking through the box outline.
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

    override fun onPhysicsProcess(dt: Float) {
        // Motion lives in the RotatingBox's local frame (= moveAndCollide's
        // parent frame); the wrapper's world rotation is invisible to the
        // sweep because every shape in the sweep shares it. Walls + sibling
        // balls handle every reflect.
        val collision = moveAndCollide(Vec2(vx, vy) * dt) ?: return
        val reflected = Vec2(vx, vy).reflect(collision.normal)
        vx = reflected.x
        vy = reflected.y

        setArtColor(Color.WHITE)
        flashTimer = 0.15f
        val other = collision.collider
        if (other is BoxedBall) {
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
        currentColor = c
    }
}
