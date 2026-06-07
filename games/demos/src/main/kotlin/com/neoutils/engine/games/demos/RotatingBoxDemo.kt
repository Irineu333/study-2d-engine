package com.neoutils.engine.games.demos

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.CharacterBody2D
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node2D
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val BOX_SIZE = 280f
private const val HALF_BOX = BOX_SIZE / 2f
private const val WALL_THICKNESS = 12f
private const val ANGULAR_VELOCITY = 0.4f
private const val BALL_COUNT = 12
private const val BALL_SIZE = 18f
private const val VELOCITY_GIZMO_SCALE = 0.12f
private const val ARROW_HEAD = 6f

@Serializable
class RotatingBoxDemo : Node2D() {

    @Transient
    private val rng = Random(0xBADB0F)

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
        // Colliders are centered on their origin: pass each wall band's center
        // (the box edge offset outward by half the wall thickness).
        wrapper.addChild(makeStaticWall(Vec2(0f, -HALF_BOX - WALL_THICKNESS / 2f), Vec2(BOX_SIZE + 2f * WALL_THICKNESS, WALL_THICKNESS)).apply { name = "topWall" })
        wrapper.addChild(makeStaticWall(Vec2(0f, HALF_BOX + WALL_THICKNESS / 2f), Vec2(BOX_SIZE + 2f * WALL_THICKNESS, WALL_THICKNESS)).apply { name = "bottomWall" })
        wrapper.addChild(makeStaticWall(Vec2(-HALF_BOX - WALL_THICKNESS / 2f, 0f), Vec2(WALL_THICKNESS, BOX_SIZE)).apply { name = "leftWall" })
        wrapper.addChild(makeStaticWall(Vec2(HALF_BOX + WALL_THICKNESS / 2f, 0f), Vec2(WALL_THICKNESS, BOX_SIZE)).apply { name = "rightWall" })
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
        // Local-space outline; SceneTree.render pushes our world transform
        // (position + rotation) so the square rotates with the wrapper.
        val corners = arrayOf(
            Vec2(-HALF_BOX, -HALF_BOX),
            Vec2(HALF_BOX, -HALF_BOX),
            Vec2(HALF_BOX, HALF_BOX),
            Vec2(-HALF_BOX, HALF_BOX),
        )
        val outline = Color(1f, 1f, 1f, 0.7f)
        for (i in 0 until 4) {
            renderer.drawLine(
                from = corners[i],
                to = corners[(i + 1) % 4],
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
                // Visual is corner-anchored at local (0,0)..(BALL_SIZE); the
                // centered rect needs a +size/2 offset to cover that same span.
                transform = Transform(position = Vec2(BALL_SIZE / 2f, BALL_SIZE / 2f))
                shape = RectangleShape2D().apply { size = Vec2(BALL_SIZE, BALL_SIZE) }
            }
        )
    }

    // Local-space draw. Body's AABB lives at local (0,0)..(BALL_SIZE,BALL_SIZE),
    // so the visual center sits at (BALL_SIZE/2, BALL_SIZE/2); the engine push
    // composes the wrapper's rotation around it.
    override fun onDraw(renderer: Renderer) {
        renderer.drawCircle(
            center = Vec2(BALL_SIZE / 2f, BALL_SIZE / 2f),
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
        drawVelocityArrow()
    }

    // A CharacterBody2D guarda a velocidade nos campos de script vx/vy (não no
    // slot `velocity` que a engine lê) e se move via moveAndCollide, então o
    // VelocityGizmoWidget built-in não enxerga essas bolas. Desenhamos a seta
    // por conta própria via immediate-draw (`tree.debug.draw`): fica atrás do
    // toggle "Debug Draw" do HUD e, ao contrário do gizmo built-in, compõe a
    // velocidade do frame local para world via o world() deste corpo — a seta
    // aponta para onde a bola realmente anda mesmo enquanto a caixa gira.
    private fun drawVelocityArrow() {
        val draw = tree?.debug?.draw ?: return
        val center = Vec2(BALL_SIZE / 2f, BALL_SIZE / 2f)
        val base = world().compose(Transform(position = center)).position
        val tip = world()
            .compose(Transform(position = center + Vec2(vx, vy) * VELOCITY_GIZMO_SCALE))
            .position
        val color = Color(0.3f, 0.8f, 1f, 0.9f)
        draw.world.line(base, tip, color)
        val dir = (tip - base).normalized
        if (dir.x == 0f && dir.y == 0f) return
        val back = tip - dir * ARROW_HEAD
        val perp = Vec2(-dir.y, dir.x) * (ARROW_HEAD / 2f)
        draw.world.line(tip, back + perp, color)
        draw.world.line(tip, back - perp, color)
    }

    internal fun setArtColor(c: Color) {
        currentColor = c
    }
}
