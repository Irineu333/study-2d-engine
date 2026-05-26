"""Stubs for engine input types: Key enum, MouseButton enum, and Input interface."""

from engine.math import Vec2


class MouseButton:
    """Mouse button constants. Each constant is an instance of MouseButton accessible as a class attribute."""

    Left: "MouseButton"
    Right: "MouseButton"
    Middle: "MouseButton"


class Key:
    """Keyboard key constants. Each constant is an instance of Key accessible as a class attribute."""

    # Letter keys
    A: "Key"; B: "Key"; C: "Key"; D: "Key"; E: "Key"; F: "Key"
    G: "Key"; H: "Key"; I: "Key"; J: "Key"; K: "Key"; L: "Key"
    M: "Key"; N: "Key"; O: "Key"; P: "Key"; Q: "Key"; R: "Key"
    S: "Key"; T: "Key"; U: "Key"; V: "Key"; W: "Key"; X: "Key"
    Y: "Key"; Z: "Key"

    # Digit keys
    DIGIT_0: "Key"; DIGIT_1: "Key"; DIGIT_2: "Key"
    DIGIT_3: "Key"; DIGIT_4: "Key"; DIGIT_5: "Key"
    DIGIT_6: "Key"; DIGIT_7: "Key"; DIGIT_8: "Key"; DIGIT_9: "Key"

    # Arrow keys
    ARROW_UP: "Key"; ARROW_DOWN: "Key"
    ARROW_LEFT: "Key"; ARROW_RIGHT: "Key"

    # Special keys
    SPACE: "Key"; ESCAPE: "Key"; ENTER: "Key"; TAB: "Key"; BACKSPACE: "Key"
    SHIFT_LEFT: "Key"; SHIFT_RIGHT: "Key"
    CTRL_LEFT: "Key"; CTRL_RIGHT: "Key"
    ALT_LEFT: "Key"; ALT_RIGHT: "Key"
    F1: "Key"; F2: "Key"


class Input:
    """Read-only snapshot of input state for the current frame.

    An ``Input`` instance is available inside ``_process`` / ``_physics_process`` via ``self.input``
    (the engine binds it to the node's scene input source).
    """

    @property
    def pointer_position(self) -> Vec2:
        """Current cursor / touch position in world space. Kotlin: pointerPosition"""
        ...

    def is_key_down(self, key: Key) -> bool:
        """True while the key is held. Kotlin: isKeyDown(key)"""
        ...

    def was_key_pressed(self, key: Key) -> bool:
        """True only on the first frame the key transitions to pressed. Kotlin: wasKeyPressed(key)"""
        ...

    def is_mouse_down(self, button: MouseButton) -> bool:
        """True while the mouse button is held. Kotlin: isMouseDown(button)"""
        ...

    def was_mouse_clicked(self, button: MouseButton) -> bool:
        """True only on the first frame the button transitions from up to down. Kotlin: wasMouseClicked(button)"""
        ...
