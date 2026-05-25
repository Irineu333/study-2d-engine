"""Stubs for engine scene types: Node, Node2D, Camera2D and the visual primitives."""

from __future__ import annotations
from typing import List, Optional, TYPE_CHECKING

from engine.math import Rect, Vec2
from engine.render import Color, Renderer
from engine.serialization import Signal

if TYPE_CHECKING:
    from engine.tree import SceneTree


class Node:
    """Base class for all scene graph nodes.

    In Python scripts you never instantiate Node directly — the engine creates
    nodes from ``scene.json`` and attaches your script via the slot pattern.
    ``self`` inside your script hooks IS the Kotlin Node instance.
    """

    name: str
    parent: Optional["Node"]
    children: List["Node"]
    is_live: bool
    tree: Optional["SceneTree"]
    groups: List[str]

    def add_child(self, child: "Node") -> None: ...
    def remove_child(self, child: "Node") -> None: ...
    def find_child(self, name: str) -> Optional["Node"]: ...
    def add_to_group(self, name: str) -> None: ...
    def remove_from_group(self, name: str) -> None: ...
    def is_in_group(self, name: str) -> bool: ...

    # Lifecycle hooks — override these in your script with the underscore
    # Godot-style names. The engine dispatches them via the Kotlin SPI.
    def _ready(self) -> None: ...
    def _process(self, dt: float) -> None: ...
    def _physics_process(self, dt: float) -> None: ...
    def _draw(self, renderer: Renderer) -> None: ...
    def _exit_tree(self) -> None: ...
    def _on_collide(self, other: "Node") -> None: ...


class Node2D(Node):
    """Node with 2D transform (position, rotation, scale).

    Most gameplay scripts extend this type::

        # extends Node2D
    """

    def world_transform(self) -> object: ...
    def world_position(self) -> Vec2: ...


class AspectMode:
    """How ``Camera2D.bounds`` is projected onto the surface when the aspect
    ratios differ. Use the string values "FIT", "FILL" or "STRETCH" in
    ``scene.json``; in Python scripts the enum is exposed as
    ``AspectMode.FIT`` / ``AspectMode.FILL`` / ``AspectMode.STRETCH``."""

    FIT: "AspectMode"
    FILL: "AspectMode"
    STRETCH: "AspectMode"


class Camera2D(Node2D):
    """2D camera node. Marking ``current = True`` makes its ``bounds`` the
    tree's viewport, and ``SceneTree.render`` pushes a view transform that
    maps ``bounds`` onto the surface under ``aspect_mode``."""

    bounds: Rect
    current: bool
    aspect_mode: AspectMode

    def screen_to_world(self, screen_position: Vec2, scene_size: Vec2) -> Vec2:
        """Convert a surface (pixel) coordinate to a world coordinate, honoring
        ``bounds`` and ``aspect_mode``. Returns ``screen_position`` unchanged
        when ``bounds.size`` has a zero or negative component (identity
        fallback)."""
        ...

    def world_to_screen(self, world_position: Vec2, scene_size: Vec2) -> Vec2:
        """Inverse of :meth:`screen_to_world`. Converts a world coordinate to a
        surface (pixel) coordinate."""
        ...


class Label(Node2D):
    """Single-line text drawn at the node's world position."""

    text: str
    size: float
    color: Color


class ColorRect(Node2D):
    """Axis-aligned filled rectangle anchored at the node's world position."""

    size: Vec2
    color: Color


class Circle2D(Node2D):
    """Filled circle whose center is ``world_position + (radius, radius)``."""

    radius: float
    color: Color


class Line2D(Node2D):
    """Polyline drawn by chaining consecutive ``points`` offset by ``world_position``."""

    points: List[Vec2]
    thickness: float
    color: Color


class Polygon2D(Node2D):
    """Filled polygon defined by ``points`` offset by ``world_position``."""

    points: List[Vec2]
    color: Color


class TimerMode:
    """Selects which engine tick advances a ``Timer``. ``PHYSICS`` (default)
    drains during ``onPhysicsProcess`` at the fixed step — deterministic. ``IDLE``
    drains during ``onProcess`` (variable ``dt``) — may drift across frames."""

    PHYSICS: "TimerMode"
    IDLE: "TimerMode"


class Timer(Node):
    """Logical node that emits ``timeout`` at fixed intervals.

    Extends :class:`Node` (not :class:`Node2D`) — has no transform, no draw,
    no spatial state. Connect a handler via ``timer.timeout.connect(my_fn)``::

        # extends Node
        def _ready(self):
            t = script_of(self._node.findChild("MoveTimer"))
            t.timeout.connect(self.on_tick)

        def on_tick(self):
            # called with no args; Signal[Unit] is bridged to a 0-arg call.
            ...

    Property names mirror Kotlin's camelCase (the Polyglot bridge does
    not translate to snake_case; using ``timer.wait_time`` would fail
    at runtime — use ``timer.waitTime``).
    """

    waitTime: float
    autostart: bool
    oneShot: bool
    processCallback: TimerMode
    timeLeft: float
    isStopped: bool
    timeout: Signal

    def start(self, override: Optional[float] = None) -> None:
        """Resets ``timeLeft`` to ``waitTime`` (no argument) or to ``override``
        (one-shot override applied only to the next emission). ``override``
        must be strictly positive — non-positive raises
        ``IllegalArgumentException`` naming the offending value."""
        ...

    def stop(self) -> None:
        """Zeroes ``timeLeft`` and marks the timer as stopped. Any pending
        emission that hasn't fired this tick is discarded."""
        ...
