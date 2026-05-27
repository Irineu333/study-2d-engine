---@meta

---@class Timer : Node
---@field waitTime number
---@field autostart boolean
---@field oneShot boolean
---@field timeLeft number
---@field isStopped boolean
---@field timeout Signal
local Timer = {}
function Timer:start() end
function Timer:stop() end

return Timer
