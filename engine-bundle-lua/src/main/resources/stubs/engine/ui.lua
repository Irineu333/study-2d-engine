---@meta

-- UI nodes shipped by the engine: CanvasLayer (screen-space scope), Control
-- (abstract anchored base) and its leaves Panel/Button (Label/ColorRect live
-- in node2d.lua). Controls own anchors/offsets, `visible` and `mouseFilter`;
-- `size` is resolved by the anchor layout pass. Accessible as
-- `nengine.Control`, `nengine.Panel`, `nengine.Button`, and the enums
-- `nengine.MouseFilter`, `nengine.LayoutPreset`.

---@class CanvasLayer : Node
---@field layer integer
CanvasLayer = {}

---@class MouseFilter
---@field STOP MouseFilter Opaque: consumes the click
---@field PASS MouseFilter Observed but not consumed
---@field IGNORE MouseFilter Transparent: never tested
MouseFilter = {}

---@class LayoutPreset
---@field TOP_LEFT LayoutPreset
---@field TOP_RIGHT LayoutPreset
---@field BOTTOM_LEFT LayoutPreset
---@field BOTTOM_RIGHT LayoutPreset
---@field CENTER_LEFT LayoutPreset
---@field CENTER_TOP LayoutPreset
---@field CENTER_RIGHT LayoutPreset
---@field CENTER_BOTTOM LayoutPreset
---@field CENTER LayoutPreset
---@field FULL_RECT LayoutPreset
LayoutPreset = {}

---@class Control : Node2D
---@field size Vec2 Resolved rect size (derived by the anchor layout pass)
---@field anchorLeft number
---@field anchorTop number
---@field anchorRight number
---@field anchorBottom number
---@field offsetLeft number
---@field offsetTop number
---@field offsetRight number
---@field offsetBottom number
---@field visible boolean Hides the control + subtree (render and hit-test) when false
---@field mouseFilter MouseFilter
---@field sizeFlagsHorizontal integer Reserved for `ui-layout` (inert)
---@field sizeFlagsVertical integer Reserved for `ui-layout` (inert)
Control = {}

---Sets the four anchors to the canonical fractions for `preset`.
---@param preset LayoutPreset
function Control:applyPreset(preset) end

---@class Border
---@field color Color
---@field width number
Border = {}

---@class Panel : Control
---@field color Color
---@field border Border|nil
Panel = {}

---@class Button : Control
---@field text string
---@field textSize number
---@field textColor Color
---@field normalColor Color
---@field hoverColor Color
---@field pressedColor Color
---@field disabledColor Color
---@field disabled boolean
---@field pressed Signal Built-in signal; emits exactly once per click cycle
Button = {}
