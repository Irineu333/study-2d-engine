class PongScene : Scene() {

    // Scoring wiring lived here before CenterLine/Score migrated to Python in
    // E4. With the migration, leftScore/rightScore are now Node2D-with-script
    // and `increment()` is a method of the Python instance, not callable from
    // a Kotlin script. Wiring is restored when PongScene itself moves to
    // Python in slice E7.
    override fun onEnter() {}

    override fun onResize(width: Float, height: Float) {
        layout(width, height)
    }

    private fun layout(width: Float, height: Float) {
        val topWall = findChild("topWall") as? BoxCollider ?: return
        val bottomWall = findChild("bottomWall") as? BoxCollider ?: return
        val leftGoal = findChild("leftGoal") as? BoxCollider ?: return
        val rightGoal = findChild("rightGoal") as? BoxCollider ?: return
        val leftPaddle = findChild("left") as? Node2D ?: return
        val rightPaddle = findChild("right") as? Node2D ?: return
        // Paddle moved to a Python script in E6 — `playFieldHeight` lives on
        // the script export, not the Node. The default (600) already matches
        // the 800x600 window; PongScene's migration in E7 will own the
        // height handoff via setExport.
        // Ball moved to a Python script in E5 — it's now a BoxCollider whose
        // behavior (movement, collisions, reset) lives in ball.py. Layout no
        // longer pokes fieldCenter / reset() from here; both are owned by
        // the Python ball and PongScene's migration in E7 will wire the
        // resize handoff (defaults from scene.json cover 800x600).

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
        leftPaddle.transform = leftPaddle.transform.copy(
            position = Vec2(PADDLE_MARGIN, height / 2f - paddleHeight / 2f)
        )
        rightPaddle.transform = rightPaddle.transform.copy(
            position = Vec2(width - PADDLE_MARGIN - paddleWidth, height / 2f - paddleHeight / 2f)
        )

        // centerLine is a Node (script-only) and exposes no Kotlin getters
        // we can drive from here — its x/height stay at scene.json defaults
        // (400, 600) which align with the 800x600 window for the E4 gate;
        // full handoff lands when PongScene moves to Python in slice E7.
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
