package com.neoutils.engine.games.demos

import com.neoutils.engine.dx.Log
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.CanvasLayer
import com.neoutils.engine.scene.ColorRect
import com.neoutils.engine.scene.Label
import com.neoutils.engine.scene.LayoutPreset
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Panel

/**
 * Scene 7 — `ui-foundation` validator.
 *
 * Two `CanvasLayer`s prove z-order: the HUD at `layer = 0` sits beneath the
 * menu at `layer = 10`. The menu's three buttons exercise normal / hover /
 * pressed / disabled visuals (Settings is disabled at startup). Background is
 * a dark `ColorRect` in world-space so it is visually obvious that the UI
 * lives on a different transform stack.
 *
 * Layout is declarative via `Control` anchors (`ui-controls-base`): the HUD
 * backdrop is `BOTTOM_LEFT`-anchored, its labels are nested children pinned to
 * the panel's top-left, and the three menu buttons center horizontally
 * (`anchorLeft = anchorRight = 0.5`). The anchor layout pass reflows everything
 * on resize with **no** `onProcess` code — the per-frame relayout this demo
 * used to carry is gone.
 *
 * Clicking a button prints a recognizable string via [Log.i]. Validation
 * checklist:
 *
 *  - Buttons stay horizontally centered when the window resizes.
 *  - HUD strip remains pinned to the bottom-left across resizes.
 *  - Settings (disabled) renders with the disabled color and never emits
 *    `pressed`; the click also is NOT consumed (passes through).
 *  - Start / Quit emit `pressed` exactly once per click cycle; the click is
 *    consumed (no gameplay node would see `wasMouseClicked` for that tick).
 *  - F1 opens the auto-inserted DebugLayer's HUD (checkboxes for fps,
 *    colliders, momentum, and custom widgets like Axes) as in scenes 1–6.
 */
class UiPlaygroundDemo : Node() {

    init { name = "UiPlayground" }

    // HUD widgets (bottom-left anchored).
    private lateinit var hudBackdrop: Panel
    private lateinit var scoreLabel: Label
    private lateinit var livesLabel: Label

    // Menu widgets (horizontally centered, vertically stacked).
    private lateinit var startButton: Button
    private lateinit var settingsButton: Button
    private lateinit var quitButton: Button

    override fun onEnter() {
        super.onEnter()
        if (children.isNotEmpty()) return

        // Dark background fills the entire surface in world-space. Oversized
        // on purpose: the renderer clips to the window, so a generous size
        // covers any resize without having to track tree.size for the bg.
        addChild(ColorRect().apply {
            name = "Background"
            transform = Transform(position = Vec2(0f, 0f))
            size = Vec2(4000f, 4000f)
            color = Color(0.07f, 0.07f, 0.10f, 1f)
        })

        // HUD layer (bottom-most UI).
        addChild(buildHud())
        // Menu layer (on top of HUD).
        addChild(buildMenu())
    }

    private fun buildHud(): CanvasLayer = CanvasLayer().apply {
        name = "Hud"
        layer = 0
        // Backdrop pinned to the surface bottom-left via a BOTTOM_LEFT preset
        // plus offsets: left/right grow from the left edge, top/bottom from the
        // bottom edge (negative offsets walk up from `anchor = 1`).
        hudBackdrop = Panel().apply {
            name = "HudBackdrop"
            color = Color(0f, 0f, 0f, 0.5f)
            applyPreset(LayoutPreset.BOTTOM_LEFT)
            offsetLeft = HUD_MARGIN
            offsetRight = HUD_MARGIN + HUD_WIDTH
            offsetTop = -(HUD_HEIGHT + HUD_MARGIN)
            offsetBottom = -HUD_MARGIN
        }
        // Labels nest inside the backdrop: top-left anchored, so they ride the
        // panel as it tracks the resizing surface (Control-in-Control).
        scoreLabel = Label().apply {
            name = "Score"
            text = "Score: 0"
            fontSize = 16f
            color = Color.WHITE
            offsetLeft = 10f
            offsetTop = 18f
        }
        livesLabel = Label().apply {
            name = "Lives"
            text = "Lives: 3"
            fontSize = 16f
            color = Color.WHITE
            offsetLeft = 100f
            offsetTop = 18f
        }
        hudBackdrop.addChild(scoreLabel)
        hudBackdrop.addChild(livesLabel)
        addChild(hudBackdrop)
    }

    private fun buildMenu(): CanvasLayer = CanvasLayer().apply {
        name = "Menu"
        layer = 10
        startButton = menuButton("Start", "Start", row = 0).apply {
            pressed.connect { Log.i(TAG, "start clicked") }
        }
        settingsButton = menuButton("Settings", "Settings (disabled)", row = 1).apply {
            disabled = true
            pressed.connect { Log.i(TAG, "settings clicked — should NOT print") }
        }
        quitButton = menuButton("Quit", "Quit", row = 2).apply {
            pressed.connect { Log.i(TAG, "quit clicked") }
        }
        addChild(startButton)
        addChild(settingsButton)
        addChild(quitButton)
    }

    /** Horizontally-centered button stacked at [row] from the menu top. */
    private fun menuButton(name: String, label: String, row: Int): Button = Button().apply {
        this.name = name
        text = label
        anchorLeft = 0.5f
        anchorRight = 0.5f
        offsetLeft = -BUTTON_WIDTH / 2f
        offsetRight = BUTTON_WIDTH / 2f
        val top = MENU_TOP + row * (BUTTON_HEIGHT + BUTTON_GAP)
        offsetTop = top
        offsetBottom = top + BUTTON_HEIGHT
    }

    private companion object {
        const val TAG = "UiPlayground"

        const val HUD_WIDTH = 200f
        const val HUD_HEIGHT = 50f
        const val HUD_MARGIN = 10f

        const val BUTTON_WIDTH = 200f
        const val BUTTON_HEIGHT = 50f
        const val BUTTON_GAP = 20f
        const val MENU_TOP = 160f
    }
}
