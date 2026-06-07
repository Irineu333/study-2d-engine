package com.neoutils.engine.games.demos

import com.neoutils.engine.input.Input
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.Area2D
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.PhysicsBody2D
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.physics.RigidBody2D
import com.neoutils.engine.physics.StaticBody2D
import com.neoutils.engine.render.Color
import com.neoutils.engine.scene.Circle2D
import com.neoutils.engine.scene.Line2D
import com.neoutils.engine.scene.Node2D
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val BALL_SIZE = 20f
private const val SEED_BALLS = 8
private const val MAX_BALLS = 42
private const val TRAP_SIZE = 54f

/** Trap behavior toggled at runtime from the [SpawnCollideWidget]. */
enum class TrapMode { DESPAWN, COLLIDE }

/**
 * Session state shared between [SpawnCollideDemo] (which reads it every frame to
 * drive the trap colliders and the auto-spawn drip) and [SpawnCollideWidget]
 * (which reads/writes it from its segmented controls). Plain runtime holder,
 * never serialized — exactly the role [com.neoutils.engine.debug.ColliderWidget]
 * plays for the `ColliderModePanel`.
 */
class SpawnCollideState {
    var trapMode: TrapMode = TrapMode.DESPAWN
    var autoSpawnEnabled: Boolean = true
    var trapPosition: Vec2 = Vec2.ZERO
    var trapInitialized: Boolean = false
}

/**
 * Funde os antigos `Spawner` + `Collision stress`: clique/auto-spawn adiciona
 * `RigidBody2D` bolinhas durante `onProcess` e todas quicam elasticamente entre
 * si e nas paredes de uma `BoundaryWalls`.
 *
 * O trap central é **interativo** e ilustra a dicotomia sensor-vs-sólido do
 * invariante #3 com dois colliders irmãos na arena: [TrapSensor] (`Area2D` que
 * remove a bolinha no `onBodyEntered`, modo `Despawn`) e [TrapWall]
 * (`StaticBody2D` sólido em que as bolinhas quicam, modo `Collide`). Nunca os
 * dois estão ativos ao mesmo tempo — a troca de modo alterna o flag `disabled`
 * de `CollisionObject2D`. O trap é arrastável pela tela (com clamp aos limites)
 * e o auto-spawn pode ser desligado, tudo via [SpawnCollideWidget], que o demo
 * registra no `onEnter` e des-registra no `onExit`.
 *
 * Atores vivem como filhos da instância de `BoundaryWalls` (não siblings), para
 * que o solver e o sweep compartilhem o frame das paredes — ver KDoc de
 * [BoundaryWalls]. Os dois colliders do trap também são filhos diretos da arena
 * porque o sweep do `RigidBody2D` só considera alvos cujo `parent` coincide com
 * o `parent` das bolinhas.
 */
@Serializable
class SpawnCollideDemo : Node2D() {

    @Transient
    internal val state = SpawnCollideState()

    @Transient
    private var lastSize: Vec2 = Vec2.ZERO

    @Transient
    private val rng = Random(0xC0FFEE)

    @Transient
    private var trapSensor: TrapSensor? = null

    @Transient
    private var trapWall: TrapWall? = null

    @Transient
    private var trapArt: Line2D? = null

    @Transient
    private var widget: SpawnCollideWidget? = null

    @Transient
    private var dragging: Boolean = false

    @Transient
    private var wasMouseDown: Boolean = false

    init {
        name = "SpawnCollideDemo"
    }

    override fun onEnter() {
        val tree = tree ?: return
        val arena = BoundaryWalls().also { addChild(it) }
        // Two sibling colliders + a visual, all direct children of the arena so
        // the RigidBody2D sweep can find the solid TrapWall (target.parent must
        // equal the balls' parent).
        trapSensor = TrapSensor().also { arena.addChild(it) }
        trapWall = TrapWall().also { arena.addChild(it) }
        trapArt = makeTrapArt().also { arena.addChild(it) }
        arena.addChild(Spawner(state).apply { name = "Spawner" })
        repeat(SEED_BALLS) { i ->
            arena.addChild(
                randomBall(i, tree.width, tree.height).apply { name = "SeedBall$i" }
            )
        }
        widget = SpawnCollideWidget(state).also { tree.debug.register(it) }
    }

    override fun onExit() {
        widget?.let { tree?.debug?.unregister(it) }
        widget = null
    }

    override fun onProcess(dt: Float) {
        val tree = tree ?: return
        val surface = tree.size
        // First valid surface: center the trap once. Later resizes only re-clamp,
        // preserving wherever the user dragged it.
        if (!state.trapInitialized && surface.x > 0f && surface.y > 0f) {
            state.trapPosition = Vec2(surface.x / 2f, surface.y / 2f)
            state.trapInitialized = true
        }
        if (surface != lastSize) {
            lastSize = surface
            clampTrap(surface)
        }
        tree.input?.let { updateTrapDrag(it, surface) }
        applyTrapMode()
        propagateTrapPosition()
    }

    // Grab-and-drag the trap with the left button (mirrors SolarSystemDemo.dragPan
    // but bounded to the trap rect). Honors mouseDragConsumed so dragging a debug
    // panel does not also drag the trap. While held, consumes the click so the
    // Spawner does not spawn a ball under the gesture.
    private fun updateTrapDrag(input: Input, surface: Vec2) {
        val down = input.isMouseDown(MouseButton.Left)
        val pressed = down && !wasMouseDown
        wasMouseDown = down
        if (!down) {
            dragging = false
            return
        }
        if (input.mouseDragConsumed) return
        if (pressed && trapRect().contains(input.pointerPosition)) dragging = true
        if (!dragging) return
        state.trapPosition = input.pointerPosition
        clampTrap(surface)
        input.mouseClickConsumed = true
    }

    private fun trapRect(): Rect {
        val half = TRAP_SIZE / 2f
        return Rect(state.trapPosition - Vec2(half, half), Vec2(TRAP_SIZE, TRAP_SIZE))
    }

    private fun clampTrap(surface: Vec2) {
        val half = TRAP_SIZE / 2f
        state.trapPosition = Vec2(
            state.trapPosition.x.coerceIn(half, (surface.x - half).coerceAtLeast(half)),
            state.trapPosition.y.coerceIn(half, (surface.y - half).coerceAtLeast(half)),
        )
    }

    // Exactly one collider is live per mode; the other is disabled so they never
    // interact (and the sweep/broad-phase skip it).
    private fun applyTrapMode() {
        trapSensor?.disabled = state.trapMode != TrapMode.DESPAWN
        trapWall?.disabled = state.trapMode != TrapMode.COLLIDE
    }

    private fun propagateTrapPosition() {
        val pos = state.trapPosition
        trapSensor?.position = pos
        trapWall?.position = pos
        trapArt?.position = pos
    }

    private fun makeTrapArt(): Line2D {
        val h = TRAP_SIZE / 2f
        return Line2D().apply {
            name = "TrapArt"
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
 * click-consumption via `wasMouseClicked`) plus a steady auto-spawn drip gated
 * by [SpawnCollideState.autoSpawnEnabled], capped at [MAX_BALLS] live balls so
 * the scene stays bounded even when the trap misses.
 */
class Spawner(private val state: SpawnCollideState) : Node2D() {

    private val autoSpawnInterval: Float = 0.9f

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
        // clicking the "← Menu" button, a debug panel, or dragging the trap
        // (which consumes the click) does not spawn a ball.
        if (input.wasMouseClicked(MouseButton.Left)) {
            spawn(input.pointerPosition)
        }
        if (!state.autoSpawnEnabled) return
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
 * Trap in `Despawn` mode: a pure sensor that removes any `RigidBody2D` ball
 * entering it. Removal during `onBodyEntered` (mid-`PhysicsSystem.step`) is safe
 * because the engine buffers tree mutations and drains them between phases.
 * Active only when [SpawnCollideState.trapMode] is `DESPAWN`; the demo toggles
 * `disabled` every frame.
 */
@Serializable
class TrapSensor : Area2D() {

    init {
        name = "TrapSensor"
        if (children.none { it is CollisionShape2D }) {
            addChild(
                CollisionShape2D().apply {
                    shape = RectangleShape2D().apply { size = Vec2(TRAP_SIZE, TRAP_SIZE) }
                }
            )
        }
    }

    override fun onBodyEntered(body: PhysicsBody2D) {
        if (body !is Ball) return
        body.parent?.removeChild(body)
    }
}

/**
 * Trap in `Collide` mode: a solid `StaticBody2D` the balls bounce off via the
 * solver. Active only when [SpawnCollideState.trapMode] is `COLLIDE`; the demo
 * toggles `disabled` every frame.
 */
@Serializable
class TrapWall : StaticBody2D() {

    init {
        name = "TrapWall"
        if (children.none { it is CollisionShape2D }) {
            addChild(
                CollisionShape2D().apply {
                    shape = RectangleShape2D().apply { size = Vec2(TRAP_SIZE, TRAP_SIZE) }
                }
            )
        }
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
