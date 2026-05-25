"""
Public surface of the nengine scripting API.

These stubs describe the Java/Kotlin objects that the GraalPy Context exposes
to Python scripts.  They are NOT executable Python — they exist only so that
Pyright/Pylance can type-check your game scripts.

Configure your IDE:
  pyrightconfig.json  →  "extraPaths": ["engine-bundle-python/src/main/resources/stubs"]
  VS Code settings    →  "python.analysis.extraPaths": ["engine-bundle-python/src/main/resources/stubs"]
"""

from engine.math import Vec2 as Vec2, Rect as Rect
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
    Timer as Timer,
    TimerMode as TimerMode,
)
from engine.physics import BoxCollider as BoxCollider
from engine.serialization import (
    NodeRef as NodeRef,
    Signal as Signal,
    Disposable as Disposable,
    signal as signal,
)
from engine.tree import SceneTree as SceneTree

__all__ = [
    "Vec2",
    "Rect",
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
    "Timer",
    "TimerMode",
    "BoxCollider",
    "NodeRef",
    "Signal",
    "Disposable",
    "signal",
    "SceneTree",
]
