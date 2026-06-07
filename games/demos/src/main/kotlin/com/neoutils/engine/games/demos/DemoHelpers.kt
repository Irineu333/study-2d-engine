package com.neoutils.engine.games.demos

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.CanvasLayer
import com.neoutils.engine.scene.Label
import com.neoutils.engine.scene.MouseFilter
import com.neoutils.engine.scene.Panel

/**
 * Maps a hue fraction `h ∈ [0, 1)` to a saturated RGB color, cycling through
 * the six primary/secondary hue sextants. Shared by every demo that colors
 * actors by index (`Spawn & Collide`, `Rotating Frame`, `Tumbling Swarm`) —
 * the single source of truth replacing the three private copies that used to
 * live inline in each demo.
 */
internal fun hue(h: Float): Color {
    val i = (h * 6f).toInt()
    val f = h * 6f - i
    return when (i % 6) {
        0 -> Color(1f, f, 0f)
        1 -> Color(1f - f, 1f, 0f)
        2 -> Color(0f, 1f, f)
        3 -> Color(0f, 1f - f, 1f)
        4 -> Color(f, 0f, 1f)
        else -> Color(1f, 0f, 1f - f)
    }
}

/**
 * Screen-space overlay shared by every demo: a title `Label`, a description
 * `Label` and a `Button` "← Menu" that returns to the navigation menu. Lives in
 * a `CanvasLayer`, so it stays in surface pixels even when a demo installs a
 * `Camera2D` (the `Transforms` demo) — title/description never zoom with the
 * world. The `onBack` callback is wired to the back `Button`'s `pressed` signal.
 */
internal class DemoOverlay(
    title: String,
    description: String,
    onBack: () -> Unit,
) : CanvasLayer() {

    init {
        name = "DemoOverlay"
        // Above any in-demo CanvasLayer; below the engine debug canvas
        // (Int.MAX_VALUE - 1), so F1's HUD still paints on top.
        layer = 100

        // Decorative top bar grounding the controls and guaranteeing title /
        // description legibility over any demo (bright sun, sprites, etc.).
        // IGNORE so it never consumes clicks — the demo behind still gets them
        // (spawning, camera drag); only the back Button consumes its own rect.
        addChild(
            Panel().apply {
                name = "HeaderBar"
                color = Color(0.07f, 0.07f, 0.10f, 0.78f)
                mouseFilter = MouseFilter.IGNORE
                anchorLeft = 0f
                anchorRight = 1f
                anchorTop = 0f
                anchorBottom = 0f
                offsetLeft = 0f
                offsetRight = 0f
                offsetTop = 0f
                offsetBottom = HEADER_HEIGHT
            }
        )

        // Just an arrow glyph: transparent background (a faint highlight only on
        // hover/press marks it interactive), vertically centered in the header
        // so it sits between the title and description lines.
        val backTop = (HEADER_HEIGHT - BACK_SIZE) / 2f
        addChild(
            Button().apply {
                name = "BackButton"
                text = "←"
                textSize = 22f
                normalColor = Color(1f, 1f, 1f, 0f)
                hoverColor = Color(1f, 1f, 1f, 0.12f)
                pressedColor = Color(1f, 1f, 1f, 0.22f)
                textColor = Color.WHITE
                anchorLeft = 0f
                anchorTop = 0f
                anchorRight = 0f
                anchorBottom = 0f
                offsetLeft = MARGIN
                offsetTop = backTop
                offsetRight = MARGIN + BACK_SIZE
                offsetBottom = backTop + BACK_SIZE
                pressed.connect { onBack() }
            }
        )

        // Title + description stacked, vertically centered as a block in the
        // header (so the arrow lands between the two lines).
        addChild(
            Label().apply {
                name = "Title"
                text = title
                fontSize = 16f
                color = Color.WHITE
                offsetLeft = MARGIN + BACK_SIZE + 12f
                offsetTop = TEXT_TOP
            }
        )

        addChild(
            Label().apply {
                name = "Description"
                text = description
                fontSize = 12f
                color = Color(1f, 1f, 1f, 0.7f)
                offsetLeft = MARGIN + BACK_SIZE + 12f
                offsetTop = TEXT_TOP + 18f
            }
        )
    }

    private companion object {
        const val MARGIN = 10f
        const val BACK_SIZE = 34f
        const val HEADER_HEIGHT = 50f
        // Title (16px) + gap + description (12px) ≈ 30px block, centered in the
        // 50px header → 10px top padding; the arrow centers on the same midline.
        const val TEXT_TOP = 10f
    }
}

/**
 * Centered, horizontally-stacked navigation menu button — a fixed-size button
 * whose anchors keep it centered on the surface as it resizes. [top] is the
 * pixel offset of its top edge from the menu's top.
 */
internal fun menuButton(label: String, top: Float, onPressed: () -> Unit): Button = Button().apply {
    name = label
    text = label
    textSize = 16f
    anchorLeft = 0.5f
    anchorRight = 0.5f
    offsetLeft = -MENU_BUTTON_WIDTH / 2f
    offsetRight = MENU_BUTTON_WIDTH / 2f
    offsetTop = top
    offsetBottom = top + MENU_BUTTON_HEIGHT
    pressed.connect { onPressed() }
}

internal const val MENU_BUTTON_WIDTH = 280f
internal const val MENU_BUTTON_HEIGHT = 44f
internal const val MENU_BUTTON_GAP = 14f
