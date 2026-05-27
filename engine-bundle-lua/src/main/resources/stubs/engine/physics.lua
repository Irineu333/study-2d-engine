---@meta

---@class CollisionObject2D : Node2D
---@field area_entered Signal
---@field area_exited Signal
---@field body_entered Signal
---@field body_exited Signal
local CollisionObject2D = {}
---@return Area2D[]
function CollisionObject2D:getOverlappingAreas() end
---@return PhysicsBody2D[]
function CollisionObject2D:getOverlappingBodies() end

---@class Area2D : CollisionObject2D
local Area2D = {}

---@class PhysicsBody2D : CollisionObject2D
local PhysicsBody2D = {}

---@class StaticBody2D : PhysicsBody2D
local StaticBody2D = {}

---@class CharacterBody2D : PhysicsBody2D
local CharacterBody2D = {}
---@param motion Vec2
function CharacterBody2D:moveAndCollide(motion) end

---@class RigidBody2D : PhysicsBody2D
---@field mass number
---@field linearVelocity Vec2
---@field angularVelocity number
---@field restitution number
---@field friction number
local RigidBody2D = {}
---@param force Vec2
function RigidBody2D:applyForce(force) end
---@param impulse Vec2
function RigidBody2D:applyImpulse(impulse) end
---@param torque number
function RigidBody2D:applyTorque(torque) end

---@class CollisionShape2D : Node2D
---@field shape Shape2D
local CollisionShape2D = {}

---@class Shape2D
local Shape2D = {}

---@class RectangleShape2D : Shape2D
---@field size Vec2
local RectangleShape2D = {}

---@class CircleShape2D : Shape2D
---@field radius number
local CircleShape2D = {}

return CollisionObject2D
