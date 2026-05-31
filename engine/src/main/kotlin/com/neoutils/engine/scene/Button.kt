package com.neoutils.engine.scene

import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.serialization.Inspect
import com.neoutils.engine.serialization.Signal
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Screen-space pushable widget. Renders as a filled rect (using `normalColor`
 * / `hoverColor` / `pressedColor` / `disabledColor` based on state) with
 * `text` drawn centered via `Renderer.measureText`. Hit-test is geometric
 * (rect contains pointer), not via `Area2D` / `PhysicsSystem`.
 *
 * Emission contract: [pressed] fires exactly once per click cycle when
 * mouse-up occurs inside the button AND the most recent mouse-down also
 * occurred inside the button. Drag-out cancels the press (no emission).
 * `disabled = true` suppresses both hit-test consumption and emission.
 *
 * Use under a `CanvasLayer` so the rect is in screen-pixel coordinates,
 * decoupled from any `Camera2D` view transform.
 */
@Serializable
open class Button : Node2D() {

    @Inspect
    var size: Vec2 = Vec2(120f, 36f)

    @Inspect
    var text: String = ""

    @Inspect
    var textSize: Float = 14f

    @Inspect
    var normalColor: Color = Color(0.30f, 0.30f, 0.32f, 1f)

    @Inspect
    var hoverColor: Color = Color(0.40f, 0.40f, 0.45f, 1f)

    @Inspect
    var pressedColor: Color = Color(0.18f, 0.18f, 0.22f, 1f)

    @Inspect
    var disabledColor: Color = Color(0.20f, 0.20f, 0.20f, 1f)

    @Inspect
    var textColor: Color = Color.WHITE

    @Inspect
    var disabled: Boolean = false

    /**
     * Built-in signal emitted once per completed click cycle (mouse-down +
     * mouse-up both inside the rect). Instantiated per-Button at construction;
     * not serialized.
     */
    @Transient
    val pressed: Signal<Unit> = Signal()

    @Transient
    private var hovered: Boolean = false

    /** True while a press cycle is in progress (mouse-down happened inside, no resolution yet). */
    @Transient
    private var armed: Boolean = false

    override fun localBounds(): Rect = Rect(Vec2.ZERO, size)

    /**
     * Screen-space axis-aligned rect, equivalent to [worldBounds]: the AABB
     * enclosing `world().apply(c)` for each corner of [localBounds]. Accounts
     * for the full world transform including **rotation** — superseding the
     * older scale-only computation that silently ignored it. Under a
     * `CanvasLayer` (a non-Node2D) composition stops at the layer boundary, so
     * coordinates are pure screen pixels.
     */
    fun screenRect(): Rect = worldBounds()!!

    /**
     * Tests whether [pointer] is inside this button's screen rect AND the
     * button is not disabled. Used by `SceneTree.hitTestUI` to decide whether
     * to consume the click.
     */
    fun hitTest(pointer: Vec2): Boolean {
        if (disabled) return false
        return screenRect().contains(pointer)
    }

    /**
     * Called by `SceneTree.hitTestUI` when this button has just absorbed a
     * mouse-down (the click is being consumed for the current tick). Arms
     * the press cycle; resolution happens in [onProcess] when the mouse
     * button is released.
     */
    internal fun armPress() {
        armed = true
    }

    override fun onProcess(dt: Float) {
        val input = tree?.input
        if (input == null) {
            super.onProcess(dt)
            return
        }
        if (disabled) {
            hovered = false
            armed = false
        } else {
            val rect = screenRect()
            hovered = rect.contains(input.pointerPosition)
            if (armed && !input.isMouseDown(MouseButton.Left)) {
                // Mouse just released: emit only if released inside.
                if (rect.contains(input.pointerPosition)) {
                    pressed.emit(Unit)
                }
                armed = false
            }
        }
        super.onProcess(dt)
    }

    override fun onDraw(renderer: Renderer) {
        val fill = when {
            disabled -> disabledColor
            armed -> pressedColor
            hovered -> hoverColor
            else -> normalColor
        }
        val rect = Rect(Vec2.ZERO, size)
        renderer.drawRect(rect, fill, filled = true)
        if (text.isNotEmpty()) {
            val measured = renderer.measureText(text, textSize)
            val pos = Vec2(
                (size.x - measured.x) / 2f,
                (size.y - measured.y) / 2f,
            )
            renderer.drawText(text, pos, textSize, textColor)
        }
        super.onDraw(renderer)
    }
}
