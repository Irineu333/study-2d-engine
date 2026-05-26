# extends Node2D

textSize: float = 48.0
color: Color = Color(1.0, 1.0, 1.0, 1.0)


def _ready(self):
    self._value = 0


def _draw(self, renderer):
    # Drawn in local space; SceneTree.render pushes our world transform
    # around _draw so the text lands at our world position.
    renderer.drawText(str(self._value), Vec2(0.0, 0.0), self.textSize, self.color)


def increment(self):
    self._value = self._value + 1
