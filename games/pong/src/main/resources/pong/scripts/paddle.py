# extends StaticBody2D

from typing import Optional

size: Vec2 = Vec2(16.0, 96.0)
upKey: Optional[Key] = None
downKey: Optional[Key] = None
ai: bool = False
speed: float = 360.0
aiMaxSpeed: float = 220.0
aiTolerance: float = 8.0
target: NodeRef = NodeRef("")


def _physics_process(self, dt):
    if self.ai:
        dy = _compute_ai(self, dt)
    else:
        dy = _compute_human(self, dt)
    if dy == 0.0:
        return
    pos = self.position
    new_y = pos.y + dy
    tree = self.tree
    play_field_height = tree.viewport.size.y if tree is not None else 0.0
    max_y = play_field_height - self.size.y
    if new_y < 0.0:
        new_y = 0.0
    elif new_y > max_y:
        new_y = max_y
    self.position = Vec2(pos.x, new_y)


def _draw(self, renderer):
    wp = self.world().position
    renderer.drawRect(Rect(wp, self.size), Color(1.0, 1.0, 1.0, 1.0), True)


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
