# extends Node2D

# Tick-based grid snake. Mutates the scene graph each `MoveTimer.timeout`:
# prepends a head ColorRect, pops the tail unless food was eaten this tick.
# Direction input is edge-triggered and buffered into a 1-slot pending slot
# applied at the START of the next tick (not mid-tick), so multiple presses
# between ticks collapse to the last valid press.

cellSize: float = 20.0
startCell: Vec2 = Vec2(10.0, 10.0)

foodEaten: Signal = signal()
gameOver: Signal = signal()
restart: Signal = signal()


def _ready(self):
    camera = self._node.parent.findChild("Camera2D")
    self._grid_w = int(camera.bounds.size.x / self.cellSize)
    self._grid_h = int(camera.bounds.size.y / self.cellSize)

    self._move_timer = self._node.findChild("MoveTimer")
    script_of(self._move_timer).timeout.connect(lambda: _tick(self))

    self._direction = Vec2(1.0, 0.0)
    self._pending = None
    self._dead = False
    self._cells = []
    self._segments = []
    _spawn_initial(self)


def _process(self, dt):
    tree = self.tree
    if tree is None:
        return
    input_ref = tree.input
    if input_ref is None:
        return
    if self._dead:
        if input_ref.wasKeyPressed(Key.ENTER):
            reset(self)
        return
    d = self._direction
    if input_ref.wasKeyPressed(Key.ARROW_UP) and d.y != 1.0:
        self._pending = Vec2(0.0, -1.0)
    if input_ref.wasKeyPressed(Key.ARROW_DOWN) and d.y != -1.0:
        self._pending = Vec2(0.0, 1.0)
    if input_ref.wasKeyPressed(Key.ARROW_LEFT) and d.x != 1.0:
        self._pending = Vec2(-1.0, 0.0)
    if input_ref.wasKeyPressed(Key.ARROW_RIGHT) and d.x != -1.0:
        self._pending = Vec2(1.0, 0.0)


def reset(self):
    for seg in self._segments:
        self._node.removeChild(seg)
    self._segments = []
    self._cells = []
    self._direction = Vec2(1.0, 0.0)
    self._pending = None
    self._dead = False
    _spawn_initial(self)
    self.restart.emit(None)
    self._move_timer.start()


def cells(self):
    # Public read-only view for peer scripts (food, etc.).
    return list(self._cells)


def _spawn_initial(self):
    cs = self.cellSize
    sc = self.startCell
    # Head at startCell; trailing segments go behind on x axis so direction
    # right (+x) moves the head into open ground on the first tick.
    for i in range(3):
        cx = sc.x - i
        cy = sc.y
        self._cells.append((cx, cy))
        seg = _make_segment(cs, cx, cy)
        self._segments.append(seg)
        self._node.addChild(seg)


def _make_segment(cell_size, cx, cy):
    seg = ColorRect()
    seg.name = "segment"
    seg.size = Vec2(cell_size, cell_size)
    seg.color = Color(0.2, 0.9, 0.2, 1.0)
    seg.position = Vec2(cx * cell_size, cy * cell_size)
    return seg


def _tick(self):
    if self._dead:
        return
    if self._pending is not None:
        self._direction = self._pending
        self._pending = None

    head_cx, head_cy = self._cells[0]
    new_cx = (head_cx + self._direction.x) % self._grid_w
    new_cy = (head_cy + self._direction.y) % self._grid_h

    food_node = self._node.parent.findChild("Food")
    food_cx = food_node.position.x / self.cellSize
    food_cy = food_node.position.y / self.cellSize
    grew = (new_cx == food_cx and new_cy == food_cy)

    for (cx, cy) in self._cells[1:]:
        if cx == new_cx and cy == new_cy:
            self._dead = True
            self._move_timer.stop()
            self.gameOver.emit(None)
            return

    head = _make_segment(self.cellSize, new_cx, new_cy)
    self._cells.insert(0, (new_cx, new_cy))
    self._segments.insert(0, head)
    self._node.addChild(head)

    if not grew:
        tail = self._segments.pop()
        self._cells.pop()
        self._node.removeChild(tail)
    else:
        self.foodEaten.emit(None)
