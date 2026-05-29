"""Stubs for ``engine.debug``: the immediate-mode debug drawing facade.

Reach it from a live script via ``self.tree.debug.draw``. Commands are
single-frame — emit them in ``_process`` / ``_physics_process`` and they are
drawn once that frame, then cleared. Verbs are no-ops until the ``"Debug
Draw"`` HUD row (or ``self.tree.debug.draw.enabled = True``) turns drawing on.
"""

from __future__ import annotations

from engine.math import Rect, Vec2
from engine.render import Color


class DebugCanvas:
    """One immediate-mode drawing surface — either ``world`` (under the active
    Camera2D view transform) or ``screen`` (screen pixels). Each verb enqueues
    a single command; a no-op while drawing is disabled."""

    def line(self, from_: Vec2, to: Vec2, color: Color, thickness: float = 1.0) -> None:
        """Kotlin: line(from, to, color, thickness)"""
        ...

    def rect(self, rect: Rect, color: Color, filled: bool = False) -> None:
        """Kotlin: rect(rect, color, filled)"""
        ...

    def circle(
        self,
        center: Vec2,
        radius: float,
        color: Color,
        filled: bool = False,
        thickness: float = 1.0,
    ) -> None:
        """Kotlin: circle(center, radius, color, filled, thickness)"""
        ...

    def polygon(self, points: list[Vec2], color: Color) -> None:
        """Kotlin: polygon(points, color)"""
        ...

    def text(self, position: Vec2, text: str, color: Color, size: float = 14.0) -> None:
        """Kotlin: text(position, text, color, size)"""
        ...


class DebugDraw:
    """Immediate-draw facade reached via ``tree.debug.draw``. Two symmetric
    canvases plus a single ``enabled`` flag gating both."""

    enabled: bool
    world: DebugCanvas
    screen: DebugCanvas


class DebugRegistry:
    """Per-SceneTree registry of debug widgets and the immediate-draw facade.
    Reach it via ``self.tree.debug``."""

    draw: DebugDraw
