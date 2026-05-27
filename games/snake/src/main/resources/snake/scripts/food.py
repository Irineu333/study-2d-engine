# extends ColorRect

import random as _random


def _ready(self):
    snake_node = self._node.parent.findChild("Snake")
    self._snake = script_of(snake_node)
    self._cell_size = self._snake.cellSize
    camera = self._node.parent.findChild("Camera2D")
    self._grid_w = int(camera.bounds.size.x / self._cell_size)
    self._grid_h = int(camera.bounds.size.y / self._cell_size)

    self._snake.foodEaten.connect(lambda _v: _reposition(self))
    self._snake.restart.connect(lambda _v: _reposition(self))

    # Re-randomize on _ready so the initial scene.json position doesn't sit
    # under the snake head; also exercises the same code path used during play.
    _reposition(self)


def _reposition(self):
    occupied = set()
    for (cx, cy) in self._snake.cells():
        occupied.add((cx, cy))
    empties = []
    for x in range(self._grid_w):
        for y in range(self._grid_h):
            cell = (float(x), float(y))
            if cell not in occupied:
                empties.append(cell)
    if not empties:
        return
    cx, cy = _random.choice(empties)
    self.position = Vec2(cx * self._cell_size, cy * self._cell_size)
