-- Pink Man platformer controller. The player is a CharacterBody2D (invariant
-- #3): the engine does NOT integrate velocity — this script owns it, applies
-- gravity, reads input, and moves via move_and_collide (CCD-correct sweep, no
-- tunneling on fast falls). Convention: y points DOWN, so gravity is +y and a
-- floor contact reports a normal pointing UP (n.y < 0).

local GRAVITY = 900.0      -- px/s^2, pulls the player down every frame
local MOVE_SPEED = 95.0    -- px/s, horizontal walk/run speed
local JUMP_SPEED = 330.0   -- px/s, upward impulse on jump (apex ~60px)
local MOVE_EPS = 1.0       -- below this |vx| the player counts as standing still

-- state -> (sheet path, frame count). Jump/Fall are single-frame (static pose);
-- AnimatedSprite2D treats frameCount == 1 as a frozen frame (no advance).
local ANIM = {
    idle = { path = "platformer/assets/characters/idle.png", frames = 11 },
    run  = { path = "platformer/assets/characters/run.png",  frames = 12 },
    jump = { path = "platformer/assets/characters/jump.png", frames = 1 },
    fall = { path = "platformer/assets/characters/fall.png", frames = 1 },
}

local function set_animation(self, state)
    if state == self._state then return end
    self._state = state
    local a = ANIM[state]
    local sprite = self._sprite
    -- Lua bindings resolve properties by their exact Kotlin (camelCase) name —
    -- there is no snake_case conversion (see LuaReflect), so these must match
    -- AnimatedSprite2D's `texturePath`/`frameCount`/`currentFrame`/`flipH`.
    sprite.texturePath = a.path
    sprite.frameCount = a.frames
    sprite.currentFrame = 0
    sprite.playing = true
end

return {
    extends = "CharacterBody2D",

    _ready = function(self)
        self._vx = 0.0
        self._vy = 0.0
        self._on_floor = false
        self._facing_left = false
        self._state = nil
        -- The animated sprite lives as a child named "Sprite"; cache the ref.
        self._sprite = nengine.NodeRef("Sprite"):resolve(self.node)
        set_animation(self, "idle")
    end,

    _physics_process = function(self, dt)
        local tree = self.tree
        if tree == nil then return end
        local input = tree.input

        -- Horizontal input: left/right (arrows or A/D). No input -> stop in x.
        local dir = 0.0
        if input ~= nil then
            if input:isKeyDown(nengine.Key.ARROW_LEFT) or input:isKeyDown(nengine.Key.A) then
                dir = dir - 1.0
            end
            if input:isKeyDown(nengine.Key.ARROW_RIGHT) or input:isKeyDown(nengine.Key.D) then
                dir = dir + 1.0
            end
        end
        self._vx = dir * MOVE_SPEED

        -- Gravity is always integrated; a floor contact zeroes vy below.
        self._vy = self._vy + GRAVITY * dt

        -- Jump only when grounded and the jump key edge-fires (no auto-repeat,
        -- no double jump in v1).
        local jump_pressed = input ~= nil and (
            input:wasKeyPressed(nengine.Key.SPACE)
            or input:wasKeyPressed(nengine.Key.ARROW_UP)
            or input:wasKeyPressed(nengine.Key.W)
        )
        if self._on_floor and jump_pressed then
            self._vy = -JUMP_SPEED
            self._on_floor = false
        end

        -- Axis-separated sweeps: move X (walls stop vx), then Y (floor/ceiling
        -- stop vy and the up-pointing normal re-arms the next jump).
        local hx = self:moveAndCollide(nengine.Vec2(self._vx * dt, 0.0))
        if hx ~= nil then
            self._vx = 0.0
        end

        self._on_floor = false
        local hy = self:moveAndCollide(nengine.Vec2(0.0, self._vy * dt))
        if hy ~= nil then
            if hy.normal.y < 0.0 then
                self._on_floor = true
            end
            self._vy = 0.0
        end

        -- Mirror the resolved velocity onto the body so the inspector shows it.
        self.velocity = nengine.Vec2(self._vx, self._vy)

        -- Facing: keep the last direction when idle so the character doesn't
        -- snap forward on stop.
        if self._vx < -MOVE_EPS then
            self._facing_left = true
        elseif self._vx > MOVE_EPS then
            self._facing_left = false
        end
        self._sprite.flipH = self._facing_left

        -- Animation state from motion: airborne -> jump (rising) / fall
        -- (descending); grounded -> run (moving) / idle (still).
        local state
        if not self._on_floor then
            if self._vy < 0.0 then state = "jump" else state = "fall" end
        elseif math.abs(self._vx) > MOVE_EPS then
            state = "run"
        else
            state = "idle"
        end
        set_animation(self, state)
    end,
}
