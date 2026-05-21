import com.neoutils.engine.games.pong.GoalSide
import kotlinx.serialization.Serializable

class PongScene : Scene() {

    override fun onEnter() {
        wireScoring()
        try {
            val left = findChild("left") as? Paddle
            if (left != null) {
                val up = left.upKey
                val upClass = if (up != null) up::class.qualifiedName else "null"
                println("DEBUG: left paddle upKey=$up ($upClass), downKey=${left.downKey}")
                println("DEBUG: is upKey a Key? ${up is com.neoutils.engine.input.Key}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun wireScoring() {
        val ball = findChild("Ball") as? Ball ?: return
        val leftScore = findChild("leftScore") as? Score
        val rightScore = findChild("rightScore") as? Score
        ball.onScore += { scorer ->
            when (scorer) {
                GoalSide.Left -> incrementScore(leftScore)
                GoalSide.Right -> incrementScore(rightScore)
            }
        }
    }

    private fun incrementScore(scoreNode: Score?) {
        scoreNode?.increment()
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

        val paddleWidth = 16f
        val paddleHeight = 96f
        leftPaddle.playFieldHeight = height
        rightPaddle.playFieldHeight = height
        leftPaddle.transform = leftPaddle.transform.copy(
            position = Vec2(PADDLE_MARGIN, height / 2f - paddleHeight / 2f)
        )
        rightPaddle.transform = rightPaddle.transform.copy(
            position = Vec2(width - PADDLE_MARGIN - paddleWidth, height / 2f - paddleHeight / 2f)
        )

        ball.fieldCenter = Vec2(width / 2f, height / 2f)
        if (isLive) {
            val serveToward = if (ball.velocity.x >= 0f) 1f else -1f
            ball.reset(serveToward)
        }
        
        centerLine.x = width / 2f
        centerLine.height = height

        val leftScore = findChild("leftScore") as? Node2D ?: return
        val rightScore = findChild("rightScore") as? Node2D ?: return
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
