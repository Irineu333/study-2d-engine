---@meta

---@class Color
---@field r number
---@field g number
---@field b number
---@field a number
local Color = {}

---@class Rect
---@field origin Vec2
---@field size Vec2
local Rect = {}
---@param point Vec2
---@return boolean
function Rect:contains(point) end

---@class Transform
---@field position Vec2
---@field scale Vec2
---@field rotation number
local Transform = {}

---@class NodeRef
---@field path string
local NodeRef = {}
---@param from any
---@return any|nil
function NodeRef:resolve(from) end

---@class Key
local Key = {}

---@class MouseButton
---@field Left MouseButton
---@field Right MouseButton
---@field Middle MouseButton
local MouseButton = {}

return Color
