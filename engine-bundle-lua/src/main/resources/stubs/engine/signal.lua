---@meta

---@class Signal
local Signal = {}
---@param handler fun(value: any)
function Signal:connect(handler) end
---@param handler fun(value: any)
function Signal:disconnect(handler) end
---@param value? any
function Signal:emit(value) end

---@class SceneTree
---@field input Input
---@field size Vec2
---@field debug DebugRegistry
local SceneTree = {}
---@param screenPosition Vec2
---@return Vec2
function SceneTree:screenToWorld(screenPosition) end
---@param worldPosition Vec2
---@return Vec2
function SceneTree:worldToScreen(worldPosition) end

---@class Input
---@field pointerPosition Vec2
local Input = {}
---@param key any
---@return boolean
function Input:isKeyDown(key) end
---@param key any
---@return boolean
function Input:wasKeyPressed(key) end
---@param button any
---@return boolean
function Input:isMouseDown(button) end
---@param button any
---@return boolean
function Input:wasMouseClicked(button) end

---@class Renderer
local Renderer = {}
---@param color Color
function Renderer:clear(color) end
---@param rect Rect
---@param color Color
---@param filled? boolean
function Renderer:drawRect(rect, color, filled) end
---@param center Vec2
---@param radius number
---@param color Color
---@param filled? boolean
---@param thickness? number
function Renderer:drawCircle(center, radius, color, filled, thickness) end
---@param from Vec2
---@param to Vec2
---@param thickness number
---@param color Color
function Renderer:drawLine(from, to, thickness, color) end
---@param text string
---@param position Vec2
---@param size number
---@param color Color
function Renderer:drawText(text, position, size, color) end
---@param text string
---@param size number
---@return Vec2
function Renderer:measureText(text, size) end

return Signal
