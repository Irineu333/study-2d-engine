# extends CharacterBody2D

from typing import Optional

size: Vec2 = Vec2(16.0, 96.0)
upKey: Optional[Key] = None
downKey: Optional[Key] = None
ai: bool = False
speed: float = 360.0
aiMaxSpeed: float = 220.0
aiTolerance: float = 8.0
target: NodeRef = NodeRef("")

_AI_TARGET_COLOR: Color = Color(1.0, 0.4, 0.4, 0.8)
_AI_CENTER_COLOR: Color = Color(0.4, 1.0, 0.6, 0.9)
_AI_BAND_COLOR: Color = Color(0.4, 1.0, 0.6, 0.45)


def _process(self, dt):
    # Immediate-draw debug gizmo for the AI paddle: the target line it steers
    # toward plus its dead-zone band (±aiTolerance around the paddle center),
    # making `_compute_ai` visible. Off by default; no-op for human paddles.
    if not self.ai:
        return
    tree = self.tree
    if tree is None:
        return
    resolved = self.target.resolve(self._node)
    if resolved is None:
        return
    world = tree.debug.draw.world
    field_w = tree.viewport.size.x
    target_y = resolved.world().position.y
    world.line(Vec2(0.0, target_y), Vec2(field_w, target_y), _AI_TARGET_COLOR, 1.0)

    pos = self.position
    cx = pos.x + self.size.x / 2.0
    center_y = pos.y + self.size.y / 2.0
    band = self.aiTolerance
    world.line(Vec2(cx - 30.0, center_y), Vec2(cx + 30.0, center_y), _AI_CENTER_COLOR, 1.0)
    world.line(Vec2(cx - 20.0, center_y - band), Vec2(cx + 20.0, center_y - band), _AI_BAND_COLOR, 1.0)
    world.line(Vec2(cx - 20.0, center_y + band), Vec2(cx + 20.0, center_y + band), _AI_BAND_COLOR, 1.0)


def _physics_process(self, dt):
    if self.ai:
        dy = _compute_ai(self, dt)
    else:
        dy = _compute_human(self, dt)
    if dy == 0.0:
        return
    # CharacterBody2D moved by the script: sweep the vertical motion so the
    # paddle stops at the contact (against ball or walls) instead of teleporting
    # into it. The walls bound the travel via the sweep, so there's no manual
    # clamp. moveAndCollide may depenetrate diagonally on a transient ball
    # overlap, drifting us off the column — re-pin x to discard that horizontal
    # component while keeping the vertical stop-at-contact.
    target_x = self.position.x
    self.moveAndCollide(Vec2(0.0, dy))
    self.position = Vec2(target_x, self.position.y)


def _draw(self, renderer):
    # Drawn in local space; SceneTree.render pushes our world transform
    # around _draw so the rect lands at our world position.
    renderer.drawRect(Rect(Vec2(0.0, 0.0), self.size), Color(1.0, 1.0, 1.0, 1.0), True)


def _compute_human(self, dt):
    tree = self.tree
    if tree is None:
        return 0.0
    input_ref = tree.input
    if input_ref is None:
        return 0.0
    direction = 0.0
    if self.upKey is not None and input_ref.isKeyDown(self.upKey):
        direction -= 1.0
    if self.downKey is not None and input_ref.isKeyDown(self.downKey):
        direction += 1.0
    return direction * self.speed * dt


def _compute_ai(self, dt):
    resolved = self.target.resolve(self._node)
    if resolved is None:
        return 0.0
    target_y = resolved.world().position.y
    center = self.position.y + self.size.y / 2.0
    delta = target_y - center
    if delta > self.aiTolerance:
        direction = 1.0
    elif delta < -self.aiTolerance:
        direction = -1.0
    else:
        direction = 0.0
    return direction * self.aiMaxSpeed * dt
