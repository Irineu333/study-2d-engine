"""
Public surface of the nengine scripting API.

These stubs describe the Java/Kotlin objects that the GraalPy Context exposes
to Python scripts.  They are NOT executable Python — they exist only so that
Pyright/Pylance can type-check your game scripts.

Configure your IDE:
  pyrightconfig.json  →  "extraPaths": ["engine-bundle-python/src/main/resources/stubs"]
  VS Code settings    →  "python.analysis.extraPaths": ["engine-bundle-python/src/main/resources/stubs"]
"""

from engine.math import Vec2 as Vec2, Rect as Rect, Transform as Transform
from engine.render import Color as Color, Renderer as Renderer
from engine.input import Key as Key, Input as Input
from engine.scene import (
    Node as Node,
    Node2D as Node2D,
    Camera2D as Camera2D,
    Label as Label,
    ColorRect as ColorRect,
    Circle2D as Circle2D,
    Line2D as Line2D,
    Polygon2D as Polygon2D,
    CanvasLayer as CanvasLayer,
    Panel as Panel,
    Border as Border,
    Button as Button,
    Timer as Timer,
    TimerMode as TimerMode,
)
from engine.physics import (
    Shape2D as Shape2D,
    RectangleShape2D as RectangleShape2D,
    CircleShape2D as CircleShape2D,
    CollisionShape2D as CollisionShape2D,
    CollisionObject2D as CollisionObject2D,
    Area2D as Area2D,
    PhysicsBody2D as PhysicsBody2D,
    StaticBody2D as StaticBody2D,
    CharacterBody2D as CharacterBody2D,
    RigidBody2D as RigidBody2D,
)
from engine.serialization import (
    NodeRef as NodeRef,
    Signal as Signal,
    Disposable as Disposable,
    signal as signal,
)
from engine.tree import SceneTree as SceneTree
from engine.debug import (
    DebugRegistry as DebugRegistry,
    DebugDraw as DebugDraw,
    DebugCanvas as DebugCanvas,
)

__all__ = [
    "Vec2",
    "Rect",
    "Transform",
    "Color",
    "Renderer",
    "Key",
    "Input",
    "Node",
    "Node2D",
    "Camera2D",
    "Label",
    "ColorRect",
    "Circle2D",
    "Line2D",
    "Polygon2D",
    "CanvasLayer",
    "Panel",
    "Border",
    "Button",
    "Timer",
    "TimerMode",
    "Shape2D",
    "RectangleShape2D",
    "CircleShape2D",
    "CollisionShape2D",
    "CollisionObject2D",
    "Area2D",
    "PhysicsBody2D",
    "StaticBody2D",
    "CharacterBody2D",
    "RigidBody2D",
    "NodeRef",
    "Signal",
    "Disposable",
    "signal",
    "SceneTree",
    "DebugRegistry",
    "DebugDraw",
    "DebugCanvas",
]
