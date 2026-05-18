package com.neoutils.engine.games.demos

import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.BoxCollider
import com.neoutils.engine.physics.Collider
import com.neoutils.engine.render.Color
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.scene.Shape
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pre-change this demo would crash with `ConcurrentModificationException`
 * (or silently corrupt the children list) because:
 *
 *  - `Spawner.onUpdate` calls `addChild` while the update traversal is
 *    iterating the scene's children;
 *  - `Trap.onCollide` calls `removeChild` while `PhysicsSystem.step` is
 *    iterating the collider list.
 *
 * After A4 both calls are buffered and drained between phases, so the demo
 * runs smoothly. F2 shows that the collider overlay is now drawn by
 * `GameSurface` (A2), not by `Scene` itself.
 */
class SpawnerDemo : Node2D() {

    private val rng = Random(System.nanoTime())

    private val trap = Trap()

    private val spawner = object : Node2D() {
        private var leftWasDown: Boolean = false
        private var autoCooldown: Float = 0f
        override fun onUpdate(dt: Float) {
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
                autoCooldown = 0.75f
            }
        }

        private fun spawn(at: Vec2) {
            val ball = Ball(velocity = randomVelocity()).apply {
                transform = Transform(position = at)
            }
            this@SpawnerDemo.addChild(ball)
        }

        private fun randomVelocity(): Vec2 {
            val angle = rng.nextFloat() * 2f * kotlin.math.PI.toFloat()
            val speed = 60f + rng.nextFloat() * 80f
            return Vec2(cos(angle) * speed, sin(angle) * speed)
        }
    }.apply { name = "Spawner" }

    init {
        name = "SpawnerDemo"
        addChild(trap)
        addChild(spawner)
    }

    override fun onEnter() {
        val scene = rootScene() ?: return
        trap.transform = Transform(
            position = Vec2(scene.width / 2f - Trap.SIZE / 2f, scene.height / 2f - Trap.SIZE / 2f),
        )
    }

    private class Trap : BoxCollider(Vec2(SIZE, SIZE)) {
        private val art = Shape(
            kind = Shape.Kind.Rect,
            size = Vec2(SIZE, SIZE),
            color = Color(1f, 0.2f, 0.2f, 0.6f),
            filled = false,
        )

        init {
            name = "Trap"
            addChild(art)
        }

        override fun onCollide(other: Collider) {
            val victim = other as? Ball ?: return
            // Mutation during physics traversal — gets enqueued and drained
            // by GameLoop before the next render. Pre-change this would have
            // thrown ConcurrentModificationException.
            val parent = victim.parent ?: return
            parent.removeChild(victim)
        }

        companion object {
            const val SIZE: Float = 80f
        }
    }

    private class Ball(private var velocity: Vec2) : BoxCollider(Vec2(SIZE, SIZE)) {
        private val art = Shape(
            kind = Shape.Kind.Circle,
            size = Vec2(SIZE, SIZE),
            color = Color(0.3f, 0.85f, 0.95f),
        )

        init {
            name = "Ball"
            addChild(art)
        }

        override fun onUpdate(dt: Float) {
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
}
