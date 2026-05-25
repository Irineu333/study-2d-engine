# extends Node

# Orchestration script for the Pong root. The world is fixed at 800×600 and
# `Camera2D` (declared in scene.json) projects it onto the surface, so layout
# now lives entirely in scene.json. The script only wires the ball's `scored`
# signal into the scoreboard nodes.


def _ready(self):
    _wire_scoring(self)


def _wire_scoring(self):
    ball_node = self._node.findChild("Ball")
    left_score_node = self._node.findChild("leftScore")
    right_score_node = self._node.findChild("rightScore")
    if ball_node is None:
        return
    ball = script_of(ball_node)
    left_score = script_of(left_score_node) if left_score_node is not None else None
    right_score = script_of(right_score_node) if right_score_node is not None else None
    if ball is None:
        return

    def _on_scored(side):
        if side == "Left" and left_score is not None:
            left_score.increment()
        elif side == "Right" and right_score is not None:
            right_score.increment()

    ball.scored.connect(_on_scored)
