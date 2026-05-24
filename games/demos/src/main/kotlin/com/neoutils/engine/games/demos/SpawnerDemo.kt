package com.neoutils.engine.games.demos

import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.BoxCollider
import com.neoutils.engine.physics.Collider
import com.neoutils.engine.render.Color
import com.neoutils.engine.scene.Circle2D
import com.neoutils.engine.scene.ColorRect
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pre-change this demo would crash with `ConcurrentModificationException`
 * (or silently corrupt the children list) because:
 *
 *  - `Spawner.onProcess` calls `addChild` while the process traversal is
 *    iterating the scene's children;
 *  - `Trap.onCollide` calls `removeChild` while `PhysicsSystem.step` is
 *    iterating the collider list.
 *
 * After A4 both calls are buffered and drained between phases, so the demo
 * runs smoothly. F2 shows that the collider overlay is now drawn by
 * `GameSurface` (A2), not by `Scene` itself.
 */
@Serializable
class SpawnerDemo : Node2D() {

    init {
        name = "SpawnerDemo"
        if (children.isEmpty()) {
            addChild(Trap().apply { name = "Trap" })
            addChild(Spawner().apply { name = "Spawner" })
        }
    }

    override fun onEnter() {
        val scene = rootScene() ?: return
        val trap = findChild("Trap") as? Trap ?: return
        trap.transform = Transform(
            position = Vec2(scene.width / 2f - Trap.SIZE / 2f, scene.height / 2f - Trap.SIZE / 2f),
        )
    }
}

@Serializable
class Spawner : Node2D() {

    @Inspect
    var autoSpawnInterval: Float = 0.75f

    @Transient
    private val rng = Random(System.nanoTime())

    @Transient
    private var leftWasDown: Boolean = false

    @Transient
    private var autoCooldown: Float = 0f

    override fun onProcess(dt: Float) {
        val scene = rootScene() ?: return
        val input = scene.input ?: return
        val leftDown = input.isMouseDown(MouseButton.Left)
        if (leftDown && !leftWasDown) {
            spawn(at = input.pointerPosition)
        }
        leftWasDown = leftDown

        autoCooldown -= dt
        if (autoCooldown <= 0f) {
            spawn(at = Vec2(rng.nextFloat() * scene.width, rng.nextFloat() * scene.height))
            autoCooldown = autoSpawnInterval
        }
    }

    private fun spawn(at: Vec2) {
        val parent = parent ?: return
        val ball = SpawnerBall().apply {
            transform = Transform(position = at)
            setVelocity(randomVelocity())
        }
        parent.addChild(ball)
    }

    private fun randomVelocity(): Vec2 {
        val angle = rng.nextFloat() * 2f * kotlin.math.PI.toFloat()
        val speed = 60f + rng.nextFloat() * 80f
        return Vec2(cos(angle) * speed, sin(angle) * speed)
    }
}

@Serializable
class Trap : BoxCollider() {

    init {
        size = Vec2(SIZE, SIZE)
        if (children.isEmpty()) {
            // Translucent fill stands in for the legacy stroke-only outline:
            // ColorRect always fills, so a low-alpha colour reads as a hint
            // rather than a solid hit-marker.
            addChild(
                ColorRect().apply {
                    name = "art"
                    size = Vec2(SIZE, SIZE)
                    color = Color(1f, 0.2f, 0.2f, 0.6f)
                }
            )
        }
    }

    override fun onCollide(other: Collider) {
        val victim = other as? SpawnerBall ?: return
        val parent = victim.parent ?: return
        parent.removeChild(victim)
    }

    companion object {
        const val SIZE: Float = 80f
    }
}

@Serializable
class SpawnerBall : BoxCollider() {

    @Transient
    private var velocity: Vec2 = Vec2.ZERO

    init {
        size = Vec2(SIZE, SIZE)
        if (children.isEmpty()) {
            addChild(
                Circle2D().apply {
                    name = "art"
                    radius = SIZE / 2f
                    color = Color(0.3f, 0.85f, 0.95f)
                }
            )
        }
    }

    fun setVelocity(v: Vec2) {
        velocity = v
    }

    override fun onProcess(dt: Float) {
        val scene = rootScene() ?: return
        val maxX = scene.width
        val maxY = scene.height
        var vx = velocity.x
        var vy = velocity.y
        var nx = transform.position.x + vx * dt
        var ny = transform.position.y + vy * dt
        if (nx < 0f) { nx = 0f; vx = -vx }
        if (nx + SIZE > maxX) { nx = maxX - SIZE; vx = -vx }
        if (ny < 0f) { ny = 0f; vy = -vy }
        if (ny + SIZE > maxY) { ny = maxY - SIZE; vy = -vy }
        velocity = Vec2(vx, vy)
        transform = transform.copy(position = Vec2(nx, ny))
    }

    companion object {
        const val SIZE: Float = 18f
    }
}
