package com.neoutils.engine.games.demos

import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.Area2D
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.PhysicsBody2D
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.physics.RigidBody2D
import com.neoutils.engine.render.Color
import com.neoutils.engine.scene.Circle2D
import com.neoutils.engine.scene.Line2D
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val BALL_SIZE = 20f
private const val SEED_BALLS = 8
private const val MAX_BALLS = 42

/**
 * Funde os antigos `Spawner` + `Collision stress`: clique/auto-spawn adiciona
 * `RigidBody2D` bolinhas durante `onProcess`, um trap `Area2D` central as
 * remove durante `onBodyEntered`, e todas quicam elasticamente entre si e nas
 * paredes de uma `BoundaryWalls`. Exercita mutação segura durante traversal
 * (add no spawn, remove no trap, ambos bufferizados), o sensor `Area2D`, o
 * solver `RigidBody2D` e o cache de world-transform sob carga.
 *
 * Atores vivem como filhos da instância de `BoundaryWalls` (não siblings), para
 * que o solver e o sweep compartilhem o frame das paredes — ver KDoc de
 * [BoundaryWalls].
 */
@Serializable
class SpawnCollideDemo : Node2D() {

    @Transient
    private var lastSize: Vec2 = Vec2.ZERO

    @Transient
    private val rng = Random(0xC0FFEE)

    init {
        name = "SpawnCollideDemo"
    }

    override fun onEnter() {
        val tree = tree ?: return
        val arena = BoundaryWalls().also { addChild(it) }
        arena.addChild(Trap())
        arena.addChild(Spawner().apply { name = "Spawner" })
        repeat(SEED_BALLS) { i ->
            arena.addChild(
                randomBall(i, tree.width, tree.height).apply { name = "SeedBall$i" }
            )
        }
    }

    override fun onProcess(dt: Float) {
        val tree = tree ?: return
        if (tree.size == lastSize) return
        lastSize = tree.size
        val arena = findChild("BoundaryWalls") as? Node2D ?: return
        (arena.findChild("Trap") as? Node2D)?.transform =
            Transform(position = Vec2(tree.width / 2f, tree.height / 2f))
    }

    private fun randomBall(id: Int, w: Float, h: Float): Ball {
        val px = BALL_SIZE + rng.nextFloat() * (w - BALL_SIZE * 2f)
        val py = BALL_SIZE + rng.nextFloat() * (h - BALL_SIZE * 2f)
        val speed = 80f + rng.nextFloat() * 120f
        val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
        return Ball(
            id = id,
            color = hue(rng.nextFloat()),
            initPos = Vec2(px, py),
            initVx = cos(angle) * speed,
            initVy = sin(angle) * speed,
        )
    }
}

/**
 * Adds rigid balls into its parent arena: one per left-click (honoring UI
 * click-consumption via `wasMouseClicked`) plus a steady auto-spawn drip,
 * capped at [MAX_BALLS] live balls so the scene stays bounded even when the
 * trap misses.
 */
@Serializable
class Spawner : Node2D() {

    @Inspect
    var autoSpawnInterval: Float = 0.9f

    @Transient
    private val rng = Random(0xBADCAB)

    @Transient
    private var autoCooldown: Float = 0f

    @Transient
    private var spawnCount: Int = 0

    override fun onProcess(dt: Float) {
        val tree = tree ?: return
        val input = tree.input ?: return
        // wasMouseClicked (not raw edge-detection) honors UI/picker consumption:
        // clicking the "← Menu" button or a debug panel does not spawn a ball.
        if (input.wasMouseClicked(MouseButton.Left)) {
            spawn(input.pointerPosition)
        }
        autoCooldown -= dt
        if (autoCooldown <= 0f) {
            spawn(Vec2(rng.nextFloat() * tree.width, rng.nextFloat() * tree.height))
            autoCooldown = autoSpawnInterval
        }
    }

    private fun spawn(at: Vec2) {
        val parent = parent ?: return
        if (parent.children.count { it is Ball } >= MAX_BALLS) return
        val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
        val speed = 70f + rng.nextFloat() * 90f
        parent.addChild(
            Ball(
                id = spawnCount++,
                color = hue(rng.nextFloat()),
                initPos = at,
                initVx = cos(angle) * speed,
                initVy = sin(angle) * speed,
            )
        )
    }
}

/**
 * Central sensor that removes any `RigidBody2D` ball entering it. Removal during
 * `onBodyEntered` (mid-`PhysicsSystem.step`) is safe because the engine buffers
 * tree mutations and drains them between phases.
 */
@Serializable
class Trap : Area2D() {

    init {
        name = "Trap"
        if (children.none { it is CollisionShape2D }) {
            addChild(
                CollisionShape2D().apply {
                    shape = RectangleShape2D().apply { size = Vec2(SIZE, SIZE) }
                }
            )
        }
        if (children.none { it is Line2D }) {
            val h = SIZE / 2f
            addChild(
                Line2D().apply {
                    name = "art"
                    points = listOf(
                        Vec2(-h, -h),
                        Vec2(h, -h),
                        Vec2(h, h),
                        Vec2(-h, h),
                        Vec2(-h, -h),
                    )
                    thickness = 1f
                    color = Color(1f, 0.2f, 0.2f, 0.6f)
                }
            )
        }
    }

    override fun onBodyEntered(body: PhysicsBody2D) {
        if (body !is Ball) return
        body.parent?.removeChild(body)
    }

    companion object {
        const val SIZE: Float = 90f
    }
}

/**
 * Elastic, frictionless `RigidBody2D` ball. The engine solver handles
 * ball-vs-ball momentum transfer and wall bounces; a short white flash marks
 * each contact.
 */
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
        restitution = 1f
        friction = 0f
        linearVelocity = Vec2(initVx, initVy)
        addChild(
            CollisionShape2D().apply {
                // Centered rect sits on the body position, matching the visual
                // and the solver's center of mass — no offset needed.
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
