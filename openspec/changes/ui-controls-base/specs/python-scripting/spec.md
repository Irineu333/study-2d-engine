## ADDED Requirements

### Requirement: Python scripts can drive Control anchors, visibility, and mouse filter

Python scripts extending a `Control`-derived widget SHALL be able to read and
write the new `Control` API via the host Node accessors: the four anchors
(`anchorLeft`/`anchorTop`/`anchorRight`/
`anchorBottom`), the four offsets (`offsetLeft`/`offsetTop`/`offsetRight`/
`offsetBottom`), `size`, `visible`, `mouseFilter`, and the preset method
(`applyPreset(...)` with the `LayoutPreset` enum). The `MouseFilter` and
`LayoutPreset` enums SHALL be reachable in the same pre-bound namespace as the
other engine types (e.g. `Vec2`, `Color`). Writing these from a hook SHALL be
reflected by the next anchor layout pass — a script SHALL NOT need to recompute
position every frame to keep a widget anchored.

#### Scenario: Script anchors a Label to the screen center via preset

- **WHEN** a Python `_ready` hook calls `self.applyPreset(LayoutPreset.CENTER)` and sets symmetric offsets on a `Label`
- **THEN** the label SHALL resolve centered on the surface after the next anchor layout pass, with no `_draw`/`_process` repositioning code.

#### Scenario: Script toggles visibility instead of alpha

- **WHEN** a Python hook sets `self.visible = False` and later `self.visible = True` on a Control-derived widget
- **THEN** the widget SHALL hide and show at full color, replacing the `color.a = 0` hide pattern.

### Requirement: PyI stubs include Control, anchors, visible, mouse_filter and presets

The published `.pyi` stubs SHALL include the `Control` base surface — anchors,
offsets, `size`, `visible`, `mouseFilter`, `applyPreset`, and the `MouseFilter`
and `LayoutPreset` enums — and SHALL show `Panel`/`Button`/`Label` inheriting it,
so editor autocomplete reflects the new API.

#### Scenario: Stub exposes the anchor and visibility API

- **WHEN** the `.pyi` stubs are generated and inspected
- **THEN** they SHALL declare `Control` with the anchor/offset fields, `size`, `visible`, `mouseFilter`, and `applyPreset`, and SHALL declare `Panel`, `Button`, and `Label` as subclasses of `Control`.
