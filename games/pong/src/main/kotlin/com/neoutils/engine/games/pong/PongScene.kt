package com.neoutils.engine.games.pong

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Scene
import com.neoutils.engine.physics.BoxCollider
import kotlinx.serialization.Serializable

@Serializable
class PongScene : Scene() {

    override fun onEnter() {
        wireScoring()
    }

    private fun wireScoring() {
        val ball = findChild("Ball") as? Ball ?: return
        val leftScore = findChild("leftScore")
        val rightScore = findChild("rightScore")
        ball.onScore += { scorer ->
            when (scorer) {
                GoalSide.Left -> incrementScore(leftScore)
                GoalSide.Right -> incrementScore(rightScore)
            }
        }
    }

    private fun incrementScore(scoreNode: Any?) {
        if (scoreNode == null) return
        try {
            scoreNode::class.java.getMethod("increment").invoke(scoreNode)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResize(width: Float, height: Float) {
        layout(width, height)
    }

    private fun layout(width: Float, height: Float) {
        val topWall = findChild("topWall") as? BoxCollider ?: return
        val bottomWall = findChild("bottomWall") as? BoxCollider ?: return
        val leftGoal = findChild("leftGoal") as? BoxCollider ?: return
        val rightGoal = findChild("rightGoal") as? BoxCollider ?: return
        val leftPaddle = findChild("left") as? Paddle ?: return
        val rightPaddle = findChild("right") as? Paddle ?: return
        val ball = findChild("Ball") as? Ball ?: return
        val centerLine = findChild("centerLine") ?: return

        topWall.size = Vec2(width, WALL_THICKNESS)
        topWall.transform = topWall.transform.copy(position = Vec2(0f, 0f))
        bottomWall.size = Vec2(width, WALL_THICKNESS)
        bottomWall.transform = bottomWall.transform.copy(
            position = Vec2(0f, height - WALL_THICKNESS)
        )

        leftGoal.size = Vec2(GOAL_THICKNESS, height)
        leftGoal.transform = leftGoal.transform.copy(position = Vec2(-GOAL_THICKNESS, 0f))
        rightGoal.size = Vec2(GOAL_THICKNESS, height)
        rightGoal.transform = rightGoal.transform.copy(position = Vec2(width, 0f))

        leftPaddle.playFieldHeight = height
        leftPaddle.transform = leftPaddle.transform.copy(
            position = Vec2(PADDLE_MARGIN, height / 2f - Paddle.HEIGHT / 2f)
        )
        rightPaddle.playFieldHeight = height
        rightPaddle.transform = rightPaddle.transform.copy(
            position = Vec2(width - PADDLE_MARGIN - Paddle.WIDTH, height / 2f - Paddle.HEIGHT / 2f)
        )

        ball.fieldCenter = Vec2(width / 2f, height / 2f)
        if (isLive) {
            ball.reset(serveToward = if (ball.velocity.x >= 0f) 1f else -1f)
        }
        val clClass = centerLine::class.java
        clClass.methods.firstOrNull { it.name == "setX" }?.invoke(centerLine, width / 2f)
        clClass.methods.firstOrNull { it.name == "setHeight" }?.invoke(centerLine, height)

        val leftScore = findChild("leftScore") as? com.neoutils.engine.scene.Node2D ?: return
        val rightScore = findChild("rightScore") as? com.neoutils.engine.scene.Node2D ?: return
        val scoreY = 24f
        val scoreOffset = 80f
        leftScore.transform = leftScore.transform.copy(
            position = Vec2(width / 2f - scoreOffset, scoreY)
        )
        rightScore.transform = rightScore.transform.copy(
            position = Vec2(width / 2f + scoreOffset / 2f, scoreY)
        )
    }

    companion object {
        const val WALL_THICKNESS: Float = 8f
        const val GOAL_THICKNESS: Float = 8f
        const val PADDLE_MARGIN: Float = 32f
    }
}
