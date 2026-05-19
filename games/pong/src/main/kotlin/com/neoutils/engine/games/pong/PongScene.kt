package com.neoutils.engine.games.pong

import com.neoutils.engine.input.Key
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Scene
import com.neoutils.engine.serialization.NodeRef
import kotlinx.serialization.Serializable

@Serializable
class PongScene : Scene() {

    init {
        name = "PongScene"
        buildInitialTree()
    }

    private fun buildInitialTree() {
        if (children.isNotEmpty()) return

        val leftPaddle = Paddle().apply {
            name = "left"
            playFieldHeight = 600f
            upKey = Key.W
            downKey = Key.S
        }
        val rightPaddle = Paddle().apply {
            name = "right"
            playFieldHeight = 600f
            ai = true
            target = NodeRef(path = "../Ball")
        }
        val ball = Ball().apply { name = "Ball" }

        val topWall = Wall().apply { name = "topWall" }
        val bottomWall = Wall().apply { name = "bottomWall" }
        val leftGoal = Goal().apply { name = "leftGoal"; side = Goal.Side.Left }
        val rightGoal = Goal().apply { name = "rightGoal"; side = Goal.Side.Right }
        val centerLine = CenterLine().apply { name = "centerLine" }

        val leftScore = Score().apply { name = "leftScore" }
        val rightScore = Score().apply { name = "rightScore" }

        addChild(centerLine)
        addChild(leftPaddle)
        addChild(rightPaddle)
        addChild(ball)
        addChild(topWall)
        addChild(bottomWall)
        addChild(leftGoal)
        addChild(rightGoal)
        addChild(leftScore)
        addChild(rightScore)
    }

    override fun onEnter() {
        wireScoring()
    }

    private fun wireScoring() {
        val ball = findChild("Ball") as? Ball ?: return
        val leftScore = findChild("leftScore") as? Score
        val rightScore = findChild("rightScore") as? Score
        ball.onScore += { scorer ->
            when (scorer) {
                Goal.Side.Left -> leftScore?.increment()
                Goal.Side.Right -> rightScore?.increment()
            }
        }
    }

    override fun onResize(width: Float, height: Float) {
        layout(width, height)
    }

    private fun layout(width: Float, height: Float) {
        val topWall = findChild("topWall") as? Wall ?: return
        val bottomWall = findChild("bottomWall") as? Wall ?: return
        val leftGoal = findChild("leftGoal") as? Goal ?: return
        val rightGoal = findChild("rightGoal") as? Goal ?: return
        val leftPaddle = findChild("left") as? Paddle ?: return
        val rightPaddle = findChild("right") as? Paddle ?: return
        val ball = findChild("Ball") as? Ball ?: return
        val centerLine = findChild("centerLine") as? CenterLine ?: return

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
        centerLine.x = width / 2f
        centerLine.height = height

        val leftScore = findChild("leftScore") as? Score ?: return
        val rightScore = findChild("rightScore") as? Score ?: return
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
