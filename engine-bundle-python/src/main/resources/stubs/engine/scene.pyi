"""Stubs for engine scene types: Node, Node2D, Camera2D and the visual primitives."""

from __future__ import annotations
from typing import List, Optional, TYPE_CHECKING

from engine.math import Rect, Vec2
from engine.render import Color, Renderer

if TYPE_CHECKING:
    from engine.scene import Scene


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
    scene: Optional["Scene"]
    groups: List[str]

    def add_child(self, child: "Node") -> None: ...
    def remove_child(self, child: "Node") -> None: ...
    def root_scene(self) -> Optional["Scene"]: ...
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


class Scene(Node2D):
    """Root of the scene graph. Obtain via node.root_scene()."""

    size: Vec2
    width: float
    height: float
    viewport: Rect

    def get_nodes_in_group(self, name: str) -> List[Node]: ...


class Camera2D(Node2D):
    """2D camera node. Marking ``current = True`` makes its ``bounds`` the
    scene's viewport."""

    bounds: Rect
    current: bool


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
