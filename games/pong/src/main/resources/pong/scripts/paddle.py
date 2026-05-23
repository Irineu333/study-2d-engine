# extends Node2D

from typing import Optional

size: Vec2 = Vec2(16.0, 96.0)
playFieldHeight: float = 600.0
upKey: Optional[Key] = None
downKey: Optional[Key] = None
ai: bool = False
speed: float = 360.0
aiMaxSpeed: float = 220.0
aiTolerance: float = 8.0
target: NodeRef = NodeRef("")


def on_enter(self):
    if not hasattr(self, '_collider') or self._collider is None:
        c = BoxCollider()
        c.size = self.size
        self._collider = c
        self.addChild(c)


def on_update(self, dt):
    if self._collider is not None:
        self._collider.size = self.size
    if self.ai:
        dy = _compute_ai(self, dt)
    else:
        dy = _compute_human(self, dt)
    if dy == 0.0:
        return
    pos = self.transform.position
    new_y = pos.y + dy
    max_y = self.playFieldHeight - self.size.y
    if new_y < 0.0:
        new_y = 0.0
    elif new_y > max_y:
        new_y = max_y
    self.transform = Transform(
        Vec2(pos.x, new_y),
        self.transform.scale,
        self.transform.rotation,
    )


def on_render(self, renderer):
    wp = self.worldPosition()
    renderer.drawRect(Rect(wp, self.size), Color(1.0, 1.0, 1.0, 1.0), True)


def _compute_human(self, dt):
    scene = self.rootScene()
    if scene is None:
        return 0.0
    input_ref = scene.input
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
    target_y = resolved.worldPosition().y
    center = self.transform.position.y + self.size.y / 2.0
    delta = target_y - center
    if delta > self.aiTolerance:
        direction = 1.0
    elif delta < -self.aiTolerance:
        direction = -1.0
    else:
        direction = 0.0
    return direction * self.aiMaxSpeed * dt
