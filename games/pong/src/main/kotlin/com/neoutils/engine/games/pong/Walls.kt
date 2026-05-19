package com.neoutils.engine.games.pong

import com.neoutils.engine.physics.BoxCollider
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

/** Horizontal boundary at the top or bottom of the field. */
@Serializable
class Wall : BoxCollider()

/** Vertical boundary that triggers a score for the opposite side. */
@Serializable
class Goal : BoxCollider() {

    @Inspect
    var side: Side = Side.Left

    enum class Side { Left, Right }
}

/** Tagged collider attached to a paddle so the ball can detect paddle hits. */
@Serializable
class PaddleCollider : BoxCollider()
