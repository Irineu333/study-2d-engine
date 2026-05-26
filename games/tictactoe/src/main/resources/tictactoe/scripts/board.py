# extends Node

# Tic-tac-toe orchestrator. Owns the full game state (cells, current player,
# winner, draw flag, winning line) and renders the marks + winning line +
# hover ghost. Static decoration (grid lines, status Label) is declared in
# scene.json and only touched here to write the status text on transitions.

BOARD_ORIGIN_X = 60.0
BOARD_ORIGIN_Y = 60.0
CELL_SIZE = 160.0
WORLD_CENTER_X = 300.0
STATUS_Y = 24.0

MARK_INSET = 0.18
MARK_THICKNESS = 0.08
WIN_THICKNESS = 0.12

MARK_COLOR = Color(1.0, 1.0, 1.0, 1.0)
GHOST_COLOR = Color(1.0, 1.0, 1.0, 0.3)
WIN_COLOR = Color(1.0, 0.85, 0.15, 0.9)

WINNING_LINES = [
    (0, 1, 2), (3, 4, 5), (6, 7, 8),
    (0, 3, 6), (1, 4, 7), (2, 5, 8),
    (0, 4, 8), (2, 4, 6),
]


def _ready(self):
    self._cells = [None] * 9
    self._current_player = "X"
    self._winner = None
    self._is_draw = False
    self._winning_line = None
    self._hovered = None
    self._status = NodeRef("status").resolve(self._node)
    _update_status_text(self)


def _process(self, dt):
    tree = self.tree
    if tree is None:
        return
    input_ref = tree.input
    if input_ref is None:
        return
    world_pos = tree.screenToWorld(input_ref.pointerPosition)
    self._hovered = _cell_at(world_pos)
    if not input_ref.wasMouseClicked(MouseButton.Left):
        return
    if _game_over(self):
        _reset(self)
        _update_status_text(self)
        return
    target = self._hovered
    if target is None:
        return
    if self._cells[target] is not None:
        return
    _place_move(self, target)
    _update_status_text(self)


def _draw(self, renderer):
    for i in range(9):
        mark = self._cells[i]
        if mark is not None:
            _draw_mark(renderer, i, mark, MARK_COLOR)
    if (not _game_over(self)
            and self._hovered is not None
            and self._cells[self._hovered] is None):
        _draw_mark(renderer, self._hovered, self._current_player, GHOST_COLOR)
    if self._winning_line is not None:
        _draw_winning_line(renderer, self._winning_line)
    _recenter_status(self, renderer)


def _cell_rect(index):
    row = index // 3
    col = index % 3
    return Rect(
        Vec2(BOARD_ORIGIN_X + col * CELL_SIZE, BOARD_ORIGIN_Y + row * CELL_SIZE),
        Vec2(CELL_SIZE, CELL_SIZE),
    )


def _cell_at(point):
    for i in range(9):
        if _cell_rect(i).contains(point):
            return i
    return None


def _place_move(self, index):
    self._cells[index] = self._current_player
    line = _check_winner(self)
    if line is not None:
        self._winner = self._current_player
        self._winning_line = line
        return
    if all(c is not None for c in self._cells):
        self._is_draw = True
        return
    self._current_player = "O" if self._current_player == "X" else "X"


def _check_winner(self):
    for line in WINNING_LINES:
        a, b, c = line
        m = self._cells[a]
        if m is None:
            continue
        if self._cells[b] == m and self._cells[c] == m:
            return line
    return None


def _reset(self):
    for i in range(9):
        self._cells[i] = None
    self._current_player = "X"
    self._winner = None
    self._is_draw = False
    self._winning_line = None


def _game_over(self):
    return self._winner is not None or self._is_draw


def _draw_mark(renderer, index, mark, color):
    # Float scalars (thickness, radius) are forced through `int(...)` because
    # GraalPy refuses to coerce a Python double like `12.8` to Java `float`
    # ("Invalid or lossy primitive coercion"); rounding to whole pixels is
    # harmless for stroke widths and circle radii at this scale. Coordinates
    # passed into `Vec2(...)` keep their fractional precision because the
    # Vec2 factory does the explicit `double → float` cast internally.
    rect = _cell_rect(index)
    inset = CELL_SIZE * MARK_INSET
    thickness = max(int(CELL_SIZE * MARK_THICKNESS), 1)
    cx = rect.origin.x + CELL_SIZE / 2.0
    cy = rect.origin.y + CELL_SIZE / 2.0
    if mark == "X":
        l = rect.origin.x + inset
        r = rect.origin.x + CELL_SIZE - inset
        t = rect.origin.y + inset
        b = rect.origin.y + CELL_SIZE - inset
        renderer.drawLine(Vec2(l, t), Vec2(r, b), thickness, color)
        renderer.drawLine(Vec2(r, t), Vec2(l, b), thickness, color)
    else:
        radius = int(CELL_SIZE / 2.0 - inset)
        renderer.drawCircle(Vec2(cx, cy), radius, color, False, thickness)


def _draw_winning_line(renderer, line):
    a_rect = _cell_rect(line[0])
    c_rect = _cell_rect(line[2])
    from_pt = Vec2(a_rect.origin.x + CELL_SIZE / 2.0, a_rect.origin.y + CELL_SIZE / 2.0)
    to_pt = Vec2(c_rect.origin.x + CELL_SIZE / 2.0, c_rect.origin.y + CELL_SIZE / 2.0)
    thickness = max(int(CELL_SIZE * WIN_THICKNESS), 2)
    renderer.drawLine(from_pt, to_pt, thickness, WIN_COLOR)


def _update_status_text(self):
    if self._status is None:
        return
    if self._winner is not None:
        self._status.text = self._winner + " venceu — clique para jogar de novo"
    elif self._is_draw:
        self._status.text = "Empate — clique para jogar de novo"
    else:
        self._status.text = "Vez de " + self._current_player


def _recenter_status(self, renderer):
    # SceneTree.render draws root first then walks children, so updating the
    # Label's local transform here (before the Label's own onDraw runs) lands
    # the centered position in the same frame.
    if self._status is None:
        return
    measured = renderer.measureText(self._status.text, self._status.size)
    self._status.position = Vec2(WORLD_CENTER_X - measured.x / 2.0, STATUS_Y)
