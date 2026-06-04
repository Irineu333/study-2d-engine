## ADDED Requirements

### Requirement: Lua scripts can drive Control anchors, visibility, and mouse filter

Lua scripts extending a `Control`-derived widget SHALL be able to read and write
the new `Control` API through the host Node userdata: the four anchors
(`anchorLeft`/`anchorTop`/`anchorRight`/
`anchorBottom`), the four offsets (`offsetLeft`/`offsetTop`/`offsetRight`/
`offsetBottom`), `size`, `visible`, `mouseFilter`, and the preset method
(`applyPreset(...)`). The `MouseFilter` and `LayoutPreset` enums SHALL be exposed
under the `nengine` global namespace alongside the other engine types. Writing
these from a hook SHALL be reflected by the next anchor layout pass.

#### Scenario: Script anchors a widget via preset from Lua

- **WHEN** a Lua hook calls `self:applyPreset(nengine.LayoutPreset.FULL_RECT)` on a `Panel` with zero offsets
- **THEN** the panel SHALL fill its parent rect after the next anchor layout pass, with no per-frame layout code.

#### Scenario: Script toggles visibility from Lua

- **WHEN** a Lua hook sets `self.visible = false` and later `self.visible = true` on a Control-derived widget
- **THEN** the widget SHALL hide and show at full color.

### Requirement: LuaCATS stubs include Control, anchors, visible, mouse_filter and presets

The published LuaCATS stubs SHALL include the `Control` base surface — anchors,
offsets, `size`, `visible`, `mouseFilter`, `applyPreset`, and the `MouseFilter`
and `LayoutPreset` enums — and SHALL show `Panel`/`Button`/`Label` inheriting it.

#### Scenario: LuaCATS stub exposes the anchor and visibility API

- **WHEN** the LuaCATS stubs are generated and inspected
- **THEN** they SHALL declare `Control` with the anchor/offset fields, `size`, `visible`, `mouseFilter`, and `applyPreset`, and SHALL declare `Panel`, `Button`, and `Label` as extending `Control`.
