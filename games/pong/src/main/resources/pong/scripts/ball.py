# extends CharacterBody2D

import math
import random as _random

ballSize: float = 16.0
initialSpeed: float = 280.0
maxSpeed: float = 560.0
speedupPerHit: float = 1.05
fieldCenter: Vec2 = Vec2(400.0, 300.0)

# Seconds of look-ahead for the immediate-draw velocity gizmo: the vector is
# drawn from the ball center to where it would be this many seconds from now.
_VEL_GIZMO_LOOKAHEAD: float = 0.12
_GIZMO_COLOR: Color = Color(1.0, 0.9, 0.2, 1.0)

# Emitted with the side string ("Left" or "Right") when the ball enters a
# goal Area2D. PongScene wires this signal up to the scoreboards.
scored: Signal = signal(str)


def _ready(self):
    if not getattr(self, '_initialized', False):
        _reset(self, 1.0 if _random.random() > 0.5 else -1.0)
        self._initialized = True
    # Pre-load SFX once (Godot-style preload): decode happens here, off the hot
    # path, so a rapid rebound never pays I/O. Null-safe: a headless run (no
    # audio backend) leaves the handles None and every play below is a no-op.
    audio = self.tree.audio
    self._hit_sfx = audio.load("pong/sfx/hit.wav") if audio is not None else None
    self._goal_sfx = audio.load("pong/sfx/goal.wav") if audio is not None else None


def _physics_process(self, dt):
    # Godot-style kinematic motion: the engine does NOT integrate velocity
    # automatically. We sweep this frame's motion with moveAndCollide so the
    # ball stops exactly at the contact (no tunneling at high speed) and we
    # bounce off the resolved normal here, instead of reacting one frame late
    # in _on_body_entered. Solid targets are picked up by the sweep — walls are
    # StaticBody2D, paddles are CharacterBody2D, both PhysicsBody2D — while the
    # goals are Area2D sensors and stay on the _on_area_entered path
    # (moveAndCollide ignores areas).
    v = self.velocity
    collision = self.moveAndCollide(Vec2(v.x * dt, v.y * dt))
    if collision is None:
        return
    body = collision.collider
    n = collision.normal
    # Classify the contact by the ball's position relative to the paddle's
    # *horizontal* span, NOT by the contact normal or the vertical span. A hit is
    # a "face" hit whenever the ball center sits beside the paddle (its center-x
    # is outside the paddle's left/right edges) — it is pressing on a vertical
    # face and must have its x reversed (the english bounce). Only when the ball
    # center-x is within the paddle's horizontal span is the contact a genuine
    # top/bottom edge, which reflects vertically.
    #
    # The earlier center-y-within-vertical-span test mis-classified the FRONT
    # corner: there the ball center-y is a hair above/below the span, so it fell
    # into the reflect branch — but the corner normal is diagonal and nearly
    # tangent to the incoming velocity, so reflecting barely touched the x
    # component that was driving the ball into the face. Keying off center-x fixes
    # that: a front-corner hit is a face hit and the x reverses decisively. The
    # engine then carries the ball out of the overlap (moveAndCollide's
    # starting-overlap recovery), so a chasing paddle re-pressing the corner can
    # no longer pin it — no script-side nudge needed.
    if body.isInGroup("paddles") and _ball_beside_paddle(self, body):
        _bounce_off_paddle(self, body)
        # Paddle face hit: the rebatida moment. Fire-and-forget; null-safe so a
        # headless run stays silent. Overlapping voices keep rapid rallies crisp.
        if self.tree.audio is not None and self._hit_sfx is not None:
            # Pass volume explicitly: a Kotlin default arg is not visible to the
            # GraalPy interop, so the single-arg form would be an arity error.
            self.tree.audio.play(self._hit_sfx, 1.0)
    else:
        # Walls and paddle top/bottom edges: reflect velocity across the contact
        # normal — v' = v - 2(v·n)n.
        dot = v.x * n.x + v.y * n.y
        self.velocity = Vec2(v.x - 2.0 * dot * n.x, v.y - 2.0 * dot * n.y)
    # No starting-overlap nudge: moveAndCollide now applies the ball's outward
    # (separating) motion on a starting overlap itself, so a paddle re-pressing a
    # marginal corner overlap can no longer pin the ball in place. The engine
    # carries it out — the script only sets the bounce velocity above.


def _process(self, dt):
    # Immediate-draw debug gizmo: the ball's velocity vector in world space.
    # Off by default (no-op until "Debug Draw" is on in the F1 HUD), emitted in
    # _process so it lands exactly once per frame regardless of physics substeps.
    tree = self.tree
    if tree is None:
        return
    world = tree.debug.draw.world
    center = Vec2(self.position.x + self.ballSize / 2.0, self.position.y + self.ballSize / 2.0)
    v = self.velocity
    tip = Vec2(center.x + v.x * _VEL_GIZMO_LOOKAHEAD, center.y + v.y * _VEL_GIZMO_LOOKAHEAD)
    world.line(center, tip, _GIZMO_COLOR, 2.0)
    world.circle(center, 3.0, _GIZMO_COLOR, True)


def _draw(self, renderer):
    # Drawn in local space; SceneTree.render pushes our world transform.
    center = Vec2(self.ballSize / 2.0, self.ballSize / 2.0)
    renderer.drawCircle(center, self.ballSize / 2.0, Color(1.0, 1.0, 1.0, 1.0), True, 1.0)


def _on_area_entered(self, area):
    # Goals are Area2D; entering one is a one-shot event per attempt, so the
    # `_scored_this_tick` flag of the old `_on_collide` era is gone.
    if area.name == "leftGoal":
        _play_goal(self)
        self.scored.emit("Right")
        _reset(self, 1.0)
    elif area.name == "rightGoal":
        _play_goal(self)
        self.scored.emit("Left")
        _reset(self, -1.0)


def _play_goal(self):
    # Point scored: play the goal SFX, null-safe (no-op without a backend).
    if self.tree.audio is not None and self._goal_sfx is not None:
        self.tree.audio.play(self._goal_sfx, 1.0)


def _ball_beside_paddle(self, paddle):
    # Face hit iff the ball center-x sits outside the paddle's horizontal span
    # (the ball is beside the paddle, pressing on a vertical face — including the
    # front corners). Same data `_bounce_off_paddle` uses (paddle world pos +
    # script `size`).
    paddle_wrapper = script_of(paddle)
    paddle_pos = paddle.world().position
    paddle_size = paddle_wrapper.size
    ball_center_x = self.position.x + self.ballSize / 2.0
    return ball_center_x < paddle_pos.x or ball_center_x > paddle_pos.x + paddle_size.x


def _bounce_off_paddle(self, paddle):
    # `paddle` is the Kotlin CharacterBody2D Node — `size` lives on the paddle
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
    # Outgoing horizontal direction is the side of the paddle the ball is on
    # (its center-x vs the paddle center-x), NOT the incoming v.x nor the contact
    # normal. Keying off the side is stable frame-to-frame: a sustained or
    # corner contact can flip the incoming v.x or wobble a diagonal normal, but
    # the side the ball sits on does not — so the ball is always sent away from
    # the paddle and never trapped pressing into it.
    paddle_center_x = paddle_pos.x + paddle_size.x / 2.0
    ball_center_x = self.position.x + self.ballSize / 2.0
    h_sign = 1.0 if ball_center_x >= paddle_center_x else -1.0
    max_angle = math.pi / 3.0
    angle = rel * max_angle
    self.velocity = Vec2(
        h_sign * new_speed * math.cos(angle),
        new_speed * math.sin(angle),
    )
    # No manual shift needed: moveAndCollide already stopped the ball at the
    # contact and applied the sweep's depenetration, so the reflected velocity
    # carries it away from the paddle next frame without re-entering.


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
