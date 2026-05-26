package com.neoutils.engine.games.demos

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.physics.RigidBody2D
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node2D
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Enxame de quadrados com velocidade linear **e** angular, colidindo
 * elasticamente contra paredes e entre si. Esta demo é uma vitrine do
 * `RigidBody2D` solver da engine: cada `TumblingSquare` é um `RigidBody2D`
 * com `restitution = 1f` e `friction = 0.4f`. O engine integra `linear-
 * Velocity` e `angularVelocity`, resolve cada contato (TOI swept + impulso
 * linear+angular + Coulomb tangencial), e re-aplica até `R = 4` iterações
 * por body por frame. A matemática de impulso (`jn`, `jt`, `r × n`, etc.)
 * que vivia inline aqui agora reside em `PhysicsSystem.resolveImpulse`.
 *
 * Veja `openspec/changes/add-rigid-body-2d/design.md` para a fórmula
 * completa e as decisões (`max(eA, eB)` para restitution, `sqrt(μA·μB)`
 * para fricção, leading-corner contact point).
 */
private const val SQUARE_COUNT = 16
private const val SQUARE_SIZE = 24f
private const val SQUARE_FRICTION = 0.4f

@Serializable
class TumblingSwarmDemo : Node2D() {

    @Transient
    private val rng = Random(0xDEADBEEF7L)

    @Transient
    private var instantFps: Float = 0f

    init {
        name = "TumblingSwarmDemo"
    }

    override fun onEnter() {
        val tree = tree ?: return
        val w = tree.width
        val h = tree.height
        // BoundaryWalls is the arena container: walls + squares share it as
        // parent so moveAndCollide's same-parent sweep can find them, and the
        // four walls keep tracking tree.size in real time during resize.
        val arena = BoundaryWalls().also { addChild(it) }
        val padding = SQUARE_SIZE
        repeat(SQUARE_COUNT) { i ->
            val px = padding + rng.nextFloat() * (w - 2f * padding)
            val py = padding + rng.nextFloat() * (h - 2f * padding)
            val speed = 90f + rng.nextFloat() * 90f
            val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
            val localRotation = rng.nextFloat() * 2f * Math.PI.toFloat()
            // Initial angular velocity in ±2 rad/s — high enough that
            // contacts visibly transfer spin between squares.
            val angularVel = (rng.nextFloat() - 0.5f) * 4f
            arena.addChild(
                TumblingSquare(
                    color = hue(i.toFloat() / SQUARE_COUNT),
                    initPos = Vec2(px, py),
                    initVx = cos(angle) * speed,
                    initVy = sin(angle) * speed,
                    initRotation = localRotation,
                    initAngularVel = angularVel,
                ).apply { name = "TumblingSquare$i" }
            )
        }
    }

    override fun onProcess(dt: Float) {
        if (dt > 0f) instantFps = 1f / dt
    }

    override fun onDraw(renderer: Renderer) {
        val text = "tumbling squares: $SQUARE_COUNT | fps: ${instantFps.roundToInt()}"
        val sceneW = tree?.width ?: 800f
        val textW = renderer.measureText(text, 14f).x
        renderer.drawText(text, Vec2(sceneW - textW - 8f, 18f), size = 14f, color = Color.WHITE)
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

class TumblingSquare(
    color: Color,
    initPos: Vec2,
    initVx: Float,
    initVy: Float,
    initRotation: Float,
    initAngularVel: Float,
) : RigidBody2D() {

    @Transient
    private val fillColor: Color = color

    init {
        transform = Transform(position = initPos, rotation = initRotation)
        restitution = 1f
        friction = SQUARE_FRICTION
        linearVelocity = Vec2(initVx, initVy)
        angularVelocity = initAngularVel
        addChild(
            CollisionShape2D().apply {
                // Center the rect on the body's position so the solver's
                // mass center (= body position) matches the geometric center.
                transform = Transform(position = Vec2(-SQUARE_SIZE / 2f, -SQUARE_SIZE / 2f))
                shape = RectangleShape2D().apply { size = Vec2(SQUARE_SIZE, SQUARE_SIZE) }
            }
        )
    }

    override fun onDraw(renderer: Renderer) {
        // Local-space polygon; SceneTree.render pushes our world transform
        // (position + rotation) so vertices rotate with the body.
        val h = SQUARE_SIZE / 2f
        renderer.drawPolygon(
            listOf(
                Vec2(-h, -h),
                Vec2(h, -h),
                Vec2(h, h),
                Vec2(-h, h),
            ),
            fillColor,
        )
    }
}
