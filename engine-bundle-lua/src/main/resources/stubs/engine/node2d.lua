---@meta

---@class Node2D : Node
---@field transform Transform
---@field position Vec2
---@field rotation number
---@field scale Vec2
local Node2D = {}
---@return Transform
function Node2D:world() end

---@class Camera2D : Node2D
---@field bounds Rect
---@field current boolean
local Camera2D = {}

---@class Label : Control
---@field text string
---@field fontSize number Font height (renamed from `size`, which now means the Control rect)
---@field color Color
local Label = {}

---@class ColorRect : Control
---@field color Color
local ColorRect = {}

---@class Circle2D : Node2D
---@field radius number
---@field color Color
local Circle2D = {}

---@class Line2D : Node2D
---@field points Vec2[]
---@field thickness number
---@field color Color
local Line2D = {}

---@class Polygon2D : Node2D
---@field points Vec2[]
---@field color Color
local Polygon2D = {}

return Node2D
