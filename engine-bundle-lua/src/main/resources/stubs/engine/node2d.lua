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

---@class Sprite2D : Node2D
---@field texture_path string Classpath path of the image asset
---@field flip_h boolean Mirror horizontally (visual only)
local Sprite2D = {}

---@class AnimatedSprite2D : Node2D
---@field texture_path string Classpath path of the horizontal sheet asset
---@field frame_count integer Number of equally-sized frames in the sheet (>= 1)
---@field fps number Frame advance rate (frames per second)
---@field loop boolean Wrap to frame 0 after the last when true
---@field playing boolean Engine advances frames while true
---@field current_frame integer Index of the frame currently shown
---@field flip_h boolean Mirror horizontally (visual only)
local AnimatedSprite2D = {}
---Start (or resume) frame advance.
function AnimatedSprite2D:play() end
---Pause frame advance, holding the current frame.
function AnimatedSprite2D:pause() end

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
