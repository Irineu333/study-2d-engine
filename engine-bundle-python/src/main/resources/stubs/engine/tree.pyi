"""Stubs for ``engine.tree``: the live owner of the scene graph."""

from __future__ import annotations
from typing import List, TYPE_CHECKING

from engine.math import Rect, Vec2

if TYPE_CHECKING:
    from engine.scene import Node
    from engine.debug import DebugRegistry


class SceneTree:
    """Live owner of a scene graph. Holds the driver/host/query concerns —
    ``input``, surface ``size``, computed ``viewport``, phase flags, traversal
    entry points, and tree-walk queries.

    Not a Node — has no ``parent``, no ``children``, no ``transform``, no
    lifecycle hooks. Obtain via ``node.tree`` from any live script."""

    root: "Node"
    size: Vec2
    width: float
    height: float
    viewport: Rect
    input: object
    debug: "DebugRegistry"

    def get_nodes_in_group(self, name: str) -> List["Node"]: ...
    def screen_to_world(self, screen_position: Vec2) -> Vec2: ...
    def world_to_screen(self, world_position: Vec2) -> Vec2: ...
