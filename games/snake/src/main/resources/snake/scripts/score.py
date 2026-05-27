# extends Label


def _ready(self):
    self._value = 0
    self.text = "Score: 0"
    snake_node = self._node.parent.findChild("Snake")
    snake = script_of(snake_node)
    snake.foodEaten.connect(lambda _v: _on_food(self))
    snake.restart.connect(lambda _v: _on_restart(self))


def _on_food(self):
    self._value = self._value + 1
    self.text = "Score: " + str(self._value)


def _on_restart(self):
    self._value = 0
    self.text = "Score: 0"
