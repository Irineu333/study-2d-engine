---@meta

---@class Node
---@field name string
---@field parent Node|nil
---@field tree SceneTree|nil
---@field children Node[]
---@field node Node
local Node = {}
---@param name string
---@return Node|nil
function Node:findChild(name) end

return Node
