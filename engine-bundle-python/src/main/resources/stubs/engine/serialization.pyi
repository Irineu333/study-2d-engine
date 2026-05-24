"""Stubs for engine serialization types: NodeRef and Signal."""

from __future__ import annotations
from typing import Any, Callable, Generic, Optional, TypeVar, TYPE_CHECKING

if TYPE_CHECKING:
    from engine.scene import Node

_T = TypeVar("_T", bound="Node")


class NodeRef(Generic[_T]):
    """Typed, path-based reference to another node in the scene graph.

    The path is relative to the resolving caller:
    - ``""``  →  the caller itself
    - ``".."`` →  the parent node
    - ``"../ball"`` →  sibling named ``ball``
    - ``"paddle/collider"`` →  a grandchild

    Declare as a top-level annotated assignment to expose it as an export::

        # extends Node2D
        target: NodeRef = NodeRef("")
    """

    path: str

    def __init__(self, path: str = "") -> None: ...

    def resolve(self, from_: "Node") -> Optional[_T]: ...
    def invalidate(self) -> None: ...


_S = TypeVar("_S")


class Disposable:
    """Handle returned by ``Signal.connect`` so the caller can later
    unsubscribe via ``dispose()``."""

    def dispose(self) -> None: ...


class Signal(Generic[_S]):
    """Per-node event hub. Declare at module level::

        # extends Node2D
        scored: Signal = signal(str)

    Then ``connect`` handlers and ``emit`` values from either side of the
    Python/Kotlin boundary::

        ball.scored.connect(self._on_scored)
        self.scored.emit("Left")
    """

    def connect(self, handler: Callable[[_S], None]) -> Disposable: ...
    def disconnect(self, handler: Callable[[_S], None]) -> None: ...
    def emit(self, value: _S) -> None: ...


def signal(t: Optional[type] = None) -> Signal[Any]:
    """Factory for top-level ``name: Signal = signal(<type>)`` declarations.

    The ``t`` argument is documentation only — Python has no parametric
    Signal type at runtime. The AST inspector picks up declarations
    syntactically and instantiates a runtime ``Signal`` per attach.
    """
    ...
