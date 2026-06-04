"""Stubs for engine scene types: Node, Node2D, Camera2D and the visual primitives."""

from __future__ import annotations
from typing import List, Optional, TYPE_CHECKING

from engine.math import Rect, Transform, Vec2
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


class Node2D(Node):
    """Node with 2D transform (position, rotation, scale).

    Most gameplay scripts extend this type::

        # extends Node2D

    ``position``, ``rotation`` and ``scale`` are convenience properties that
    read from and write to ``transform``. Each setter rebuilds an immutable
    :class:`Transform` via ``transform.copy(...)``, so the world-transform
    cache invalidates exactly as it would for a direct ``self.transform = ...``
    assignment.

    Because ``Vec2`` is immutable, writing a single component fails::

        self.position.y = 5.0  # raises AttributeError at runtime

    The correct idiom is to assign a new ``Vec2``::

        self.position = Vec2(self.position.x, 5.0)
    """

    transform: Transform
    position: Vec2
    rotation: float
    scale: Vec2

    def world(self) -> Transform:
        """Return the composed world-space transform from the topmost
        ``Node2D`` ancestor down to ``self``. Result is cached per node;
        consecutive reads without intervening mutation are O(1)."""
        ...


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


class MouseFilter:
    """How a :class:`Control` takes part in the UI hit-test. Use the string
    values ``"STOP"`` / ``"PASS"`` / ``"IGNORE"`` in ``scene.json``; in scripts
    the enum is exposed as ``MouseFilter.STOP`` etc."""

    STOP: "MouseFilter"
    PASS: "MouseFilter"
    IGNORE: "MouseFilter"


class LayoutPreset:
    """Godot 4-style anchor presets for :meth:`Control.applyPreset`."""

    TOP_LEFT: "LayoutPreset"
    TOP_RIGHT: "LayoutPreset"
    BOTTOM_LEFT: "LayoutPreset"
    BOTTOM_RIGHT: "LayoutPreset"
    CENTER_LEFT: "LayoutPreset"
    CENTER_TOP: "LayoutPreset"
    CENTER_RIGHT: "LayoutPreset"
    CENTER_BOTTOM: "LayoutPreset"
    CENTER: "LayoutPreset"
    FULL_RECT: "LayoutPreset"


class Control(Node2D):
    """Abstract base for in-game UI widgets (``Panel``/``Button``/``Label``/
    ``ColorRect``). Anchors + offsets are the source of truth; the anchor layout
    pass resolves ``position``/``size`` against the parent rect each tick, so a
    widget stays anchored on resize **without** per-frame repositioning code.
    Writing ``position``/``size`` mirrors back into the offsets (Godot-style).

    ``visible = False`` hides the control and its subtree (render + hit-test),
    replacing the ``color.a = 0`` hack. ``mouseFilter`` controls hit-test
    participation (``STOP``/``PASS``/``IGNORE``)."""

    size: Vec2
    anchorLeft: float
    anchorTop: float
    anchorRight: float
    anchorBottom: float
    offsetLeft: float
    offsetTop: float
    offsetRight: float
    offsetBottom: float
    visible: bool
    mouseFilter: MouseFilter
    focusMode: "FocusMode"
    sizeFlagsHorizontal: int
    sizeFlagsVertical: int

    def applyPreset(self, preset: LayoutPreset) -> None:
        """Set the four anchors to the canonical fractions for ``preset``;
        offsets are left untouched."""
        ...


class FocusMode:
    """Reserved for the future ``ui-focus`` change. Inert today."""

    NONE: "FocusMode"
    CLICK: "FocusMode"
    ALL: "FocusMode"


class Label(Control):
    """Single-line text, a :class:`Control` **min-size** leaf: its rect is the
    measured text size. ``fontSize`` is the font height (renamed from the former
    ``size``, which now means the Control rect)."""

    text: str
    fontSize: float
    color: Color


class ColorRect(Control):
    """Filled rectangle anchored at the node's local origin. ``SceneTree.render``
    applies the world transform via ``Renderer.pushTransform`` around ``onDraw``.
    Non-interactive (``mouseFilter`` defaults to ``IGNORE``)."""

    color: Color


class Circle2D(Node2D):
    """Filled circle centered at the node's local origin. ``SceneTree.render``
    applies the world transform via ``Renderer.pushTransform`` around ``onDraw``."""

    radius: float
    color: Color


class Line2D(Node2D):
    """Polyline drawn by chaining consecutive ``points`` in **local space**.
    ``SceneTree.render`` applies the world transform via ``Renderer.pushTransform``
    around ``onDraw``."""

    points: List[Vec2]
    thickness: float
    color: Color


class Polygon2D(Node2D):
    """Filled polygon defined by ``points`` in **local space**. ``SceneTree.render``
    applies the world transform via ``Renderer.pushTransform`` around ``onDraw``."""

    points: List[Vec2]
    color: Color


class CanvasLayer(Node):
    """Screen-space scope inside the scene tree. Descendants render decoupled
    from any ``Camera2D`` view transform, useful for HUDs, menus and overlays.

    ``SceneTree.render`` walks the world subtree first (skipping every
    ``CanvasLayer``), then walks all CanvasLayers in ``(layer asc, dfs-order
    asc)`` order with identity transform at each layer boundary. Higher
    ``layer`` paints on top of lower ``layer``."""

    layer: int


class Panel(Control):
    """Filled rectangle in screen-space (when placed under ``CanvasLayer``) or
    world-space (when not). Optional ``border`` is drawn as an unfilled rect
    on top of the fill. ``size`` is inherited from :class:`Control`; default
    ``mouseFilter`` is ``STOP`` (opaque)."""

    color: Color
    border: Optional["Border"]


class Border:
    """Outline descriptor for :class:`Panel`. ``width`` is in renderer units
    (screen pixels under a CanvasLayer); not all backends honor it today."""

    color: Color
    width: float


class Button(Control):
    """Pushable widget. Hit-test is geometric (rect contains pointer), not
    physics-based. Place under a :class:`CanvasLayer` for screen-space input.
    ``size`` is inherited from :class:`Control`; default ``mouseFilter`` is
    ``STOP``.

    ``pressed`` emits exactly once per click cycle when mouse-up occurs inside
    the button rect AND the most recent mouse-down was also inside. Drag-out
    cancels. ``disabled = True`` (or ``visible = False``) suppresses both
    emission and hit-test consumption.

    Connect handlers in ``_ready``::

        # extends Button

        def _ready(self):
            self.pressed.connect(self._on_pressed)

        def _on_pressed(self, _):
            print("clicked")
    """

    text: str
    textSize: float
    textColor: Color
    normalColor: Color
    hoverColor: Color
    pressedColor: Color
    disabledColor: Color
    disabled: bool
    pressed: Signal


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
