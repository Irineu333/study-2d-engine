# extends Node

# Orchestration script for the Pong root. The world is fixed at 800×600 and
# `Camera2D` (declared in scene.json) projects it onto the surface, so layout
# now lives entirely in scene.json. The script only wires the ball's `scored`
# signal into the scoreboard nodes.


def _ready(self):
    _wire_scoring(self)
    _play_start(self)


def _play_start(self):
    # One-shot game-start jingle, fired once when the root enters the tree.
    # Null-safe: a headless run (no audio backend) stays silent.
    audio = self.tree.audio
    if audio is not None:
        audio.play(audio.load("pong/sfx/start.wav"), 1.0)


def _wire_scoring(self):
    ball_node = self._node.findChild("Ball")
    # Scores live under the HUD CanvasLayer (screen-space). Walk through it to
    # reach the per-side score Node2Ds.
    hud = self._node.findChild("Hud")
    left_score_node = hud.findChild("leftScore") if hud is not None else None
    right_score_node = hud.findChild("rightScore") if hud is not None else None
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
