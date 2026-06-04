# extends Label

# Centered game-over banner. As a `Control`, the Label is anchored full-rect
# (`LayoutPreset.FULL_RECT`) so the anchor layout pass centers its measured
# text on the surface — no frame-2 `_draw` measuring hack. Visibility is a real
# flag (`visible`), not a `color.a = 0` trick.


def _ready(self):
    # Fill the surface; the min-size text then centers within it. Offsets are
    # zeroed so the anchor rect is exactly the surface rect.
    self.applyPreset(LayoutPreset.FULL_RECT)
    self.offsetLeft = 0.0
    self.offsetTop = 0.0
    self.offsetRight = 0.0
    self.offsetBottom = 0.0
    self.visible = False

    # GameOverLabel lives under the Hud CanvasLayer; reach the Snake as a
    # sibling by walking up two levels (Hud → root).
    snake_node = self._node.parent.parent.findChild("Snake")
    snake = script_of(snake_node)
    snake.gameOver.connect(lambda _v: _show(self))
    snake.restart.connect(lambda _v: _hide(self))


def _show(self):
    self.visible = True


def _hide(self):
    self.visible = False
