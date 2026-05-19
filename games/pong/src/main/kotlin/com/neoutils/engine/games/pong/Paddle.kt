package com.neoutils.engine.games.pong

import com.neoutils.engine.input.Key
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.serialization.Inspect
import com.neoutils.engine.serialization.NodeRef
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class Paddle : Node2D() {

    @Inspect
    var size: Vec2 = Vec2(WIDTH, HEIGHT)

    @Inspect
    var playFieldHeight: Float = 600f

    @Inspect
    var upKey: Key? = null

    @Inspect
    var downKey: Key? = null

    @Inspect
    var ai: Boolean = false

    @Inspect
    var speed: Float = 360f

    @Inspect
    var aiMaxSpeed: Float = 220f

    @Inspect
    var aiTolerance: Float = 8f

    @Inspect
    var target: NodeRef<Node2D> = NodeRef("")

    @Transient
    private var collider: PaddleCollider? = null

    override fun onEnter() {
        if (collider == null) {
            val c = PaddleCollider().apply { size = this@Paddle.size }
            collider = c
            addChild(c)
        }
    }

    override fun onUpdate(dt: Float) {
        collider?.size = size
        val dy = if (ai) computeAi(dt) else computeHuman(dt)
        if (dy == 0f) return
        val newY = (transform.position.y + dy).coerceIn(0f, playFieldHeight - size.y)
        transform = transform.copy(position = transform.position.copy(y = newY))
    }

    private fun computeHuman(dt: Float): Float {
        val input = rootScene()?.input ?: return 0f
        var direction = 0f
        upKey?.let { if (input.isKeyDown(it)) direction -= 1f }
        downKey?.let { if (input.isKeyDown(it)) direction += 1f }
        return direction * speed * dt
    }

    private fun computeAi(dt: Float): Float {
        val resolved = target.resolve(this) ?: return 0f
        val targetY = resolved.worldPosition().y
        val center = transform.position.y + size.y / 2f
        val delta = targetY - center
        val direction = when {
            delta > aiTolerance -> 1f
            delta < -aiTolerance -> -1f
            else -> 0f
        }
        return direction * aiMaxSpeed * dt
    }

    override fun onRender(renderer: Renderer) {
        renderer.drawRect(Rect(worldPosition(), size), Color.WHITE, filled = true)
    }

    companion object {
        const val WIDTH = 16f
        const val HEIGHT = 96f
    }
}
