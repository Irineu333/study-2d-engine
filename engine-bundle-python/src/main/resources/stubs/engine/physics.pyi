"""Stubs for engine physics types: CollisionObject2D + subclasses and
CollisionShape2D + Shape2D resources."""

from typing import List, Optional

from engine.math import Rect, Transform, Vec2
from engine.scene import Node2D
from engine.serialization import Signal


class Shape2D:
    """Base class for shape resources held by :class:`CollisionShape2D`.

    Polymorphic via kotlinx.serialization sealed-class encoding — the JSON
    form carries a `type` discriminator naming the concrete subtype.
    """

    def bounds(self, world: Transform, local_offset: Vec2) -> Rect:
        """Axis-aligned bounding box of this shape in world space, given the
        owning :class:`CollisionShape2D`'s world transform and an extra local
        offset (typically ``Vec2.ZERO``)."""
        ...


class RectangleShape2D(Shape2D):
    """Axis-aligned rectangle. ``size`` is the full width × height in local
    units (top-left anchored, same convention as :class:`ColorRect`)."""

    size: Vec2

    def __init__(self) -> None: ...


class CircleShape2D(Shape2D):
    """Circle centered on the owning :class:`CollisionShape2D`'s local
    origin."""

    radius: float

    def __init__(self) -> None: ...


class CollisionShape2D(Node2D):
    """Node child of a :class:`CollisionObject2D` that carries a polymorphic
    :class:`Shape2D` resource. Inactive when ``shape`` is ``None`` or
    ``disabled`` is True; in that case the engine ignores it."""

    shape: Optional[Shape2D]
    disabled: bool

    def __init__(self) -> None: ...

    def world_bounds(self) -> Optional[Rect]:
        """World-space AABB of the held shape, or ``None`` when disabled or
        ``shape`` is ``None``."""
        ...


class CollisionObject2D(Node2D):
    """Base class for nodes that participate in collision.

    Concrete subtypes are :class:`Area2D` (trigger, does not block) and
    :class:`PhysicsBody2D` (solid). Each instance exposes four hooks (no-op
    by default) plus four built-in :class:`Signal` instances that fire on
    enter/exit transitions detected by ``PhysicsSystem``.
    """

    disabled: bool

    area_entered: Signal
    area_exited: Signal
    body_entered: Signal
    body_exited: Signal

    # Override these in your script as `_on_area_entered(self, area)` etc.
    def _on_area_entered(self, area: "Area2D") -> None: ...
    def _on_area_exited(self, area: "Area2D") -> None: ...
    def _on_body_entered(self, body: "PhysicsBody2D") -> None: ...
    def _on_body_exited(self, body: "PhysicsBody2D") -> None: ...


class Area2D(CollisionObject2D):
    """Trigger-only collision object: receives enter/exit events but does
    not block other bodies. Used for goals, pickups, sensors, hitboxes."""

    def __init__(self) -> None: ...


class PhysicsBody2D(CollisionObject2D):
    """Abstract base for solid bodies. Use :class:`StaticBody2D`,
    :class:`CharacterBody2D`, or :class:`RigidBody2D`.

    ``friction`` and ``restitution`` are exposed on every PhysicsBody2D so that
    a :class:`RigidBody2D`'s impulse solver can combine them with the body it
    collides with.
    """

    friction: float
    restitution: float


class StaticBody2D(PhysicsBody2D):
    """Solid body with no velocity slot. Position is altered by the script
    (or stays still). Models walls, platforms, Pong paddles."""

    def __init__(self) -> None: ...


class CharacterBody2D(PhysicsBody2D):
    """Solid body with a ``velocity`` slot. The engine does NOT integrate
    ``velocity`` automatically — a script must apply it in
    ``_physics_process`` (Godot-style)::

        # extends CharacterBody2D
        def _physics_process(self, dt):
            v = self.velocity
            pos = self.position
            self.position = Vec2(pos.x + v.x * dt, pos.y + v.y * dt)
    """

    velocity: Vec2

    def __init__(self) -> None: ...


class RigidBody2D(PhysicsBody2D):
    """Dynamic body integrated by the engine. The engine owns ``position``,
    ``linear_velocity``, and ``angular_velocity``; scripts influence motion
    via ``apply_force`` / ``apply_impulse`` / ``apply_torque`` or by direct
    read/write of the velocity properties::

        # extends RigidBody2D

        mass: float = 1.0
        restitution: float = 1.0
        friction: float = 0.0

        def _physics_process(self, dt):
            self.apply_central_impulse(Vec2(100.0, 0.0))

    Writing ``self.linear_velocity.x = X`` raises :class:`AttributeError`
    (Vec2.x is immutable); construct a new Vec2 instead.
    """

    mass: float
    inertia: float
    gravity_scale: float
    linear_damping: float
    angular_damping: float
    linear_velocity: Vec2
    angular_velocity: float

    def __init__(self) -> None: ...

    def apply_force(self, force: Vec2) -> None: ...
    def apply_impulse(self, impulse: Vec2) -> None: ...
    def apply_central_force(self, force: Vec2) -> None: ...
    def apply_central_impulse(self, impulse: Vec2) -> None: ...
    def apply_force_at(self, force: Vec2, world_point: Vec2) -> None: ...
    def apply_impulse_at(self, impulse: Vec2, world_point: Vec2) -> None: ...
    def apply_torque(self, torque: float) -> None: ...
