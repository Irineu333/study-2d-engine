# extends Label

# `Label` doesn't expose a `visible` flag today, so visibility is toggled via
# `color.a` (0.0 hidden, 1.0 visible) — kept as a single channel so the rest
# of the color survives across show/hide.


def _ready(self):
    # Start hidden regardless of what scene.json declared, so a future edit to
    # the JSON color cannot accidentally leak the label at boot.
    c = self.color
    self.color = Color(c.r, c.g, c.b, 0.0)

    snake_node = self._node.parent.findChild("Snake")
    snake = script_of(snake_node)
    snake.gameOver.connect(lambda _v: _show(self))
    snake.restart.connect(lambda _v: _hide(self))


def _show(self):
    c = self.color
    self.color = Color(c.r, c.g, c.b, 1.0)


def _hide(self):
    c = self.color
    self.color = Color(c.r, c.g, c.b, 0.0)
