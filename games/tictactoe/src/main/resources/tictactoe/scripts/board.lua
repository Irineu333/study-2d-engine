-- Tic-tac-toe orchestrator. Owns the full game state (cells, current player,
-- winner, draw flag, winning line) and renders the marks + winning line +
-- hover ghost. Static decoration (grid lines, status Label) is declared in
-- scene.json and only touched here to write the status text on transitions.

local BOARD_ORIGIN_X = 60.0
local BOARD_ORIGIN_Y = 60.0
local CELL_SIZE = 160.0

local MARK_INSET = 0.18
local MARK_THICKNESS = 0.08
local WIN_THICKNESS = 0.12

local MARK_COLOR = nengine.Color(1.0, 1.0, 1.0, 1.0)
local GHOST_COLOR = nengine.Color(1.0, 1.0, 1.0, 0.3)
local WIN_COLOR = nengine.Color(1.0, 0.85, 0.15, 0.9)

-- Immediate-draw debug colors (only painted while the "Debug Draw" HUD row is on).
local DEBUG_HOVER_COLOR = nengine.Color(0.3, 0.9, 1.0, 0.9)
local DEBUG_INDEX_COLOR = nengine.Color(0.5, 1.0, 0.6, 0.55)
local DEBUG_SCREEN_COLOR = nengine.Color(0.3, 0.9, 1.0, 1.0)

local WINNING_LINES = {
    { 1, 2, 3 }, { 4, 5, 6 }, { 7, 8, 9 },
    { 1, 4, 7 }, { 2, 5, 8 }, { 3, 6, 9 },
    { 1, 5, 9 }, { 3, 5, 7 },
}

local function cell_rect(index)
    -- index is 1-based for Lua idiomatic table indexing.
    local row = math.floor((index - 1) / 3)
    local col = (index - 1) % 3
    return nengine.Rect(
        nengine.Vec2(BOARD_ORIGIN_X + col * CELL_SIZE, BOARD_ORIGIN_Y + row * CELL_SIZE),
        nengine.Vec2(CELL_SIZE, CELL_SIZE)
    )
end

local function cell_at(point)
    for i = 1, 9 do
        if cell_rect(i):contains(point) then return i end
    end
    return nil
end

local function game_over(self)
    return self._winner ~= nil or self._is_draw
end

local function check_winner(self)
    for _, line in ipairs(WINNING_LINES) do
        local a, b, c = line[1], line[2], line[3]
        local m = self._cells[a]
        if m ~= nil and self._cells[b] == m and self._cells[c] == m then
            return line
        end
    end
    return nil
end

local function reset(self)
    for i = 1, 9 do self._cells[i] = nil end
    self._current_player = "X"
    self._winner = nil
    self._is_draw = false
    self._winning_line = nil
end

local function place_move(self, index)
    self._cells[index] = self._current_player
    local line = check_winner(self)
    if line ~= nil then
        self._winner = self._current_player
        self._winning_line = line
        return
    end
    local all_filled = true
    for i = 1, 9 do
        if self._cells[i] == nil then all_filled = false; break end
    end
    if all_filled then
        self._is_draw = true
        return
    end
    if self._current_player == "X" then
        self._current_player = "O"
    else
        self._current_player = "X"
    end
end

local function update_status_text(self)
    if self._status == nil then return end
    if self._winner ~= nil then
        self._status.text = self._winner .. " venceu — clique para jogar de novo"
    elseif self._is_draw then
        self._status.text = "Empate — clique para jogar de novo"
    else
        self._status.text = "Vez de " .. self._current_player
    end
end

local function draw_mark(renderer, index, mark, color)
    -- Rounded scalars match the Python original — keeps stroke widths and
    -- circle radii on whole pixels, harmless at this scale.
    local rect = cell_rect(index)
    local inset = CELL_SIZE * MARK_INSET
    local thickness = math.max(math.floor(CELL_SIZE * MARK_THICKNESS), 1)
    local cx = rect.origin.x + CELL_SIZE / 2.0
    local cy = rect.origin.y + CELL_SIZE / 2.0
    if mark == "X" then
        local l = rect.origin.x + inset
        local r = rect.origin.x + CELL_SIZE - inset
        local t = rect.origin.y + inset
        local b = rect.origin.y + CELL_SIZE - inset
        renderer:drawLine(nengine.Vec2(l, t), nengine.Vec2(r, b), thickness, color)
        renderer:drawLine(nengine.Vec2(r, t), nengine.Vec2(l, b), thickness, color)
    else
        local radius = math.floor(CELL_SIZE / 2.0 - inset)
        renderer:drawCircle(nengine.Vec2(cx, cy), radius, color, false, thickness)
    end
end

local function draw_winning_line(renderer, line)
    local a_rect = cell_rect(line[1])
    local c_rect = cell_rect(line[3])
    local from_pt = nengine.Vec2(a_rect.origin.x + CELL_SIZE / 2.0, a_rect.origin.y + CELL_SIZE / 2.0)
    local to_pt = nengine.Vec2(c_rect.origin.x + CELL_SIZE / 2.0, c_rect.origin.y + CELL_SIZE / 2.0)
    local thickness = math.max(math.floor(CELL_SIZE * WIN_THICKNESS), 2)
    renderer:drawLine(from_pt, to_pt, thickness, WIN_COLOR)
end

local function draw_debug(self, tree, input_ref)
    -- Immediate-mode debug aid via `tree.debug.draw` (Lua side of the facade).
    -- Off by default: the verbs no-op until the "Debug Draw" row is ticked in
    -- the F1 HUD, so a normal match stays clean. World gizmos ride the
    -- MainCamera view transform exactly like the marks; the screen gizmo is in
    -- raw pixels. Emitted in _process (not _draw) per the facade contract.
    local draw = tree.debug.draw
    -- World: number every cell (visualizes the 1..9 indexing of WINNING_LINES)
    -- and outline the cell currently under the pointer.
    for i = 1, 9 do
        local rect = cell_rect(i)
        draw.world:text(
            nengine.Vec2(rect.origin.x + 8.0, rect.origin.y + 22.0),
            tostring(i),
            DEBUG_INDEX_COLOR,
            18.0
        )
    end
    if self._hovered ~= nil then
        draw.world:rect(cell_rect(self._hovered), DEBUG_HOVER_COLOR, false)
    end
    -- Screen: echo which cell the pointer maps to, next to the cursor.
    if input_ref ~= nil then
        local pointer = input_ref.pointerPosition
        local label = self._hovered ~= nil and ("cell " .. self._hovered) or "cell -"
        draw.screen:text(nengine.Vec2(pointer.x + 12.0, pointer.y - 12.0), label, DEBUG_SCREEN_COLOR, 13.0)
    end
end

return {
    extends = "Node",

    _ready = function(self)
        self._cells = {}
        for i = 1, 9 do self._cells[i] = nil end
        self._current_player = "X"
        self._winner = nil
        self._is_draw = false
        self._winning_line = nil
        self._hovered = nil
        -- Status label lives under the Hud CanvasLayer in screen-space.
        self._status = nengine.NodeRef("Hud/status"):resolve(self.node)
        update_status_text(self)
    end,

    _process = function(self, dt)
        local tree = self.tree
        if tree == nil then return end
        local input_ref = tree.input
        if input_ref ~= nil then
            local world_pos = tree:screenToWorld(input_ref.pointerPosition)
            self._hovered = cell_at(world_pos)
        end
        -- Debug gizmos are emitted every frame (no-ops unless "Debug Draw" is on),
        -- independent of input — so they show even before the first mouse move.
        draw_debug(self, tree, input_ref)
        if input_ref == nil then return end
        if not input_ref:wasMouseClicked(nengine.MouseButton.Left) then return end
        if game_over(self) then
            reset(self)
            update_status_text(self)
            return
        end
        local target = self._hovered
        if target == nil then return end
        if self._cells[target] ~= nil then return end
        place_move(self, target)
        update_status_text(self)
    end,

    _draw = function(self, renderer)
        for i = 1, 9 do
            local mark = self._cells[i]
            if mark ~= nil then
                draw_mark(renderer, i, mark, MARK_COLOR)
            end
        end
        if (not game_over(self))
                and self._hovered ~= nil
                and self._cells[self._hovered] == nil then
            draw_mark(renderer, self._hovered, self._current_player, GHOST_COLOR)
        end
        if self._winning_line ~= nil then
            draw_winning_line(renderer, self._winning_line)
        end
    end,
}
