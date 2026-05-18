package com.neoutils.engine.games.pong

import com.neoutils.engine.input.Key
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Scene

class PongScene(
    val width: Float = 800f,
    val height: Float = 600f,
) : Scene() {

    val leftScore: Score = Score(position = Vec2(width / 2f - 80f, 24f))
    val rightScore: Score = Score(position = Vec2(width / 2f + 48f, 24f))

    val leftPaddle: Paddle = Paddle(
        playFieldHeight = height,
        upKey = Key.W,
        downKey = Key.S,
    ).apply {
        name = "left"
        transform = transform.copy(position = Vec2(24f, height / 2f - Paddle.HEIGHT / 2f))
    }

    val rightPaddle: Paddle = Paddle(
        playFieldHeight = height,
        ai = true,
    ).apply {
        name = "right"
        transform = transform.copy(position = Vec2(width - 24f - Paddle.WIDTH, height / 2f - Paddle.HEIGHT / 2f))
    }

    val ball: Ball = Ball(fieldCenter = Vec2(width / 2f, height / 2f)) { scorer ->
        when (scorer) {
            Goal.Side.Left -> leftScore.increment()
            Goal.Side.Right -> rightScore.increment()
        }
    }

    private val topWall = Wall(Vec2(width, 8f)).apply {
        name = "topWall"
        transform = transform.copy(position = Vec2(0f, 0f))
    }

    private val bottomWall = Wall(Vec2(width, 8f)).apply {
        name = "bottomWall"
        transform = transform.copy(position = Vec2(0f, height - 8f))
    }

    private val leftGoal = Goal(Goal.Side.Left, Vec2(8f, height)).apply {
        name = "leftGoal"
        transform = transform.copy(position = Vec2(-8f, 0f))
    }

    private val rightGoal = Goal(Goal.Side.Right, Vec2(8f, height)).apply {
        name = "rightGoal"
        transform = transform.copy(position = Vec2(width, 0f))
    }

    private val centerLine = CenterLine(width / 2f, height)

    init {
        name = "PongScene"
        // Order matters for render: background decorations first, then play
        // objects so they paint on top.
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

        rightPaddle.aiTargetY = { ball.transform.position.y + ball.size / 2f }
    }
}

private class CenterLine(val x: Float, val height: Float) : Node() {
    override fun onRender(renderer: Renderer) {
        val dashHeight = 12f
        val gap = 8f
        val color = Color(1f, 1f, 1f, 0.3f)
        var y = 0f
        while (y < height) {
            renderer.drawRect(
                com.neoutils.engine.math.Rect(Vec2(x - 1f, y), Vec2(2f, dashHeight)),
                color,
                filled = true,
            )
            y += dashHeight + gap
        }
    }
}
