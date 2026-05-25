# extends Node


def _ready(self):
    self._physics_label = self._node.findChild("PhysicsLabel")
    self._idle_label = self._node.findChild("IdleLabel")
    self._physics_state = True
    self._idle_state = True

    physics_timer = script_of(self._node.findChild("PhysicsTimer"))
    idle_timer = script_of(self._node.findChild("IdleTimer"))

    def on_physics_tick():
        self._physics_state = not self._physics_state
        self._physics_label.text = "TICK" if self._physics_state else "TOCK"

    def on_idle_tick():
        self._idle_state = not self._idle_state
        self._idle_label.text = "TICK" if self._idle_state else "TOCK"

    self._on_physics_tick = on_physics_tick
    self._on_idle_tick = on_idle_tick

    physics_timer.timeout.connect(on_physics_tick)
    idle_timer.timeout.connect(on_idle_tick)
