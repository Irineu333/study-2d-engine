# extends CharacterBody2D

import math
import random as _random

ballSize: float = 16.0
initialSpeed: float = 280.0
maxSpeed: float = 560.0
speedupPerHit: float = 1.05
fieldCenter: Vec2 = Vec2(400.0, 300.0)

# Emitted with the side string ("Left" or "Right") when the ball enters a
# goal Area2D. PongScene wires this signal up to the scoreboards.
scored: Signal = signal(str)


def _ready(self):
    if not getattr(self, '_initialized', False):
        _reset(self, 1.0 if _random.random() > 0.5 else -1.0)
        self._initialized = True


def _physics_process(self, dt):
    # Godot-style integration: the engine does NOT advance CharacterBody2D
    # by velocity automatically. The script integrates explicitly.
    v = self.velocity
    pos = self.position
    self.position = Vec2(pos.x + v.x * dt, pos.y + v.y * dt)


def _draw(self, renderer):
    # Drawn in local space; SceneTree.render pushes our world transform.
    center = Vec2(self.ballSize / 2.0, self.ballSize / 2.0)
    renderer.drawCircle(center, self.ballSize / 2.0, Color(1.0, 1.0, 1.0, 1.0), True, 1.0)


def _on_area_entered(self, area):
    # Goals are Area2D; entering one is a one-shot event per attempt, so the
    # `_scored_this_tick` flag of the old `_on_collide` era is gone.
    if area.name == "leftGoal":
        self.scored.emit("Right")
        _reset(self, 1.0)
    elif area.name == "rightGoal":
        self.scored.emit("Left")
        _reset(self, -1.0)


def _on_body_entered(self, body):
    if body.isInGroup("walls"):
        v = self.velocity
        self.velocity = Vec2(v.x, -v.y)
        return
    if body.isInGroup("paddles"):
        _bounce_off_paddle(self, body)


def _bounce_off_paddle(self, paddle):
    # `paddle` is the Kotlin StaticBody2D Node — `size` lives on the paddle
    # script's export, so reach it via `script_of` rather than the raw node.
    paddle_wrapper = script_of(paddle)
    paddle_pos = paddle.world().position
    paddle_size = paddle_wrapper.size
    paddle_center_y = paddle_pos.y + paddle_size.y / 2.0
    ball_center_y = self.position.y + self.ballSize / 2.0
    rel = (ball_center_y - paddle_center_y) / (paddle_size.y / 2.0)
    if rel < -1.0:
        rel = -1.0
    elif rel > 1.0:
        rel = 1.0
    v = self.velocity
    vlen = v.length
    new_speed = vlen * self.speedupPerHit
    if new_speed > self.maxSpeed:
        new_speed = self.maxSpeed
    h_sign = -1.0 if v.x > 0.0 else 1.0
    max_angle = math.pi / 3.0
    angle = rel * max_angle
    self.velocity = Vec2(
        h_sign * new_speed * math.cos(angle),
        new_speed * math.sin(angle),
    )
    # Shift the ball out of the paddle so it stops re-entering on the next
    # physics step (otherwise enter would fire repeatedly as we tunnel).
    ball_pos = self.position
    paddle_left = paddle_pos.x
    paddle_right = paddle_pos.x + paddle_size.x
    if h_sign < 0.0:
        shift = paddle_left - (ball_pos.x + self.ballSize) - 0.5
    else:
        shift = paddle_right - ball_pos.x + 0.5
    self.position = Vec2(ball_pos.x + shift, ball_pos.y)


def reset(self, serve_toward):
    _reset(self, serve_toward)


def _reset(self, serve_toward):
    self.position = Vec2(
        self.fieldCenter.x - self.ballSize / 2.0,
        self.fieldCenter.y - self.ballSize / 2.0,
    )
    angle = (_random.random() - 0.5) * 1.4
    sx = 1.0 if serve_toward >= 0.0 else -1.0
    self.velocity = Vec2(
        sx * self.initialSpeed * math.cos(angle),
        self.initialSpeed * math.sin(angle),
    )
