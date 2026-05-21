import com.neoutils.engine.games.pong.GoalSide

class Goal : BoxCollider() {
    @Inspect
    var side: GoalSide = GoalSide.Left
}
