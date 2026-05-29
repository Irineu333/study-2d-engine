---@meta

-- Immediate-mode debug drawing facade, reached via `self.tree.debug.draw`.
-- Commands are single-frame: emit them in `_process` / `_physics_process` and
-- they draw once that frame, then clear. Verbs are no-ops until the
-- "Debug Draw" HUD row (or `self.tree.debug.draw.enabled = true`) enables them.

---@class DebugCanvas
local DebugCanvas = {}
---@param from Vec2
---@param to Vec2
---@param color Color
---@param thickness? number
function DebugCanvas:line(from, to, color, thickness) end
---@param rect Rect
---@param color Color
---@param filled? boolean
function DebugCanvas:rect(rect, color, filled) end
---@param center Vec2
---@param radius number
---@param color Color
---@param filled? boolean
---@param thickness? number
function DebugCanvas:circle(center, radius, color, filled, thickness) end
---@param points Vec2[]
---@param color Color
function DebugCanvas:polygon(points, color) end
---@param position Vec2
---@param text string
---@param color Color
---@param size? number
function DebugCanvas:text(position, text, color, size) end

---@class DebugDraw
---@field enabled boolean
---@field world DebugCanvas
---@field screen DebugCanvas
local DebugDraw = {}

---@class DebugRegistry
---@field draw DebugDraw
local DebugRegistry = {}

return DebugRegistry
