package com.neoutils.engine.games.demos

import com.neoutils.engine.dx.Log
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.CanvasLayer
import com.neoutils.engine.scene.ColorRect
import com.neoutils.engine.scene.Label
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
 * Without anchors (those land in the future `ui-anchors` change), this demo
 * recomputes the HUD and menu positions in `onProcess` every frame, reading
 * `tree.size`. That keeps the HUD glued to the bottom-left and the menu
 * horizontally centered when the user resizes the window — at the cost of
 * the ad-hoc layout math that anchors will replace declaratively.
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
 *  - F1/F2/F3 toggle the auto-inserted DebugOverlayLayer as in scenes 1–6.
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
        hudBackdrop = Panel().apply {
            name = "HudBackdrop"
            size = Vec2(HUD_WIDTH, HUD_HEIGHT)
            color = Color(0f, 0f, 0f, 0.5f)
        }
        scoreLabel = Label().apply {
            name = "Score"
            text = "Score: 0"
            size = 16f
            color = Color.WHITE
        }
        livesLabel = Label().apply {
            name = "Lives"
            text = "Lives: 3"
            size = 16f
            color = Color.WHITE
        }
        addChild(hudBackdrop)
        addChild(scoreLabel)
        addChild(livesLabel)
    }

    private fun buildMenu(): CanvasLayer = CanvasLayer().apply {
        name = "Menu"
        layer = 10
        startButton = Button().apply {
            name = "Start"
            size = Vec2(BUTTON_WIDTH, BUTTON_HEIGHT)
            text = "Start"
            pressed.connect { Log.i(TAG, "start clicked") }
        }
        settingsButton = Button().apply {
            name = "Settings"
            size = Vec2(BUTTON_WIDTH, BUTTON_HEIGHT)
            text = "Settings (disabled)"
            disabled = true
            pressed.connect { Log.i(TAG, "settings clicked — should NOT print") }
        }
        quitButton = Button().apply {
            name = "Quit"
            size = Vec2(BUTTON_WIDTH, BUTTON_HEIGHT)
            text = "Quit"
            pressed.connect { Log.i(TAG, "quit clicked") }
        }
        addChild(startButton)
        addChild(settingsButton)
        addChild(quitButton)
    }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        // Re-layout every frame against the current surface. Manual
        // recomputation here stands in for the declarative anchors that
        // `ui-anchors` will deliver later.
        val surface = tree?.size ?: return
        val w = surface.x
        val h = surface.y

        // HUD pinned to the bottom-left: 10 px left margin, ~10 px above the
        // bottom edge for the backdrop; labels sit centered within it.
        val hudY = h - HUD_HEIGHT - HUD_MARGIN
        hudBackdrop.position = Vec2(HUD_MARGIN, hudY)
        scoreLabel.position = Vec2(HUD_MARGIN + 10f, hudY + 18f)
        livesLabel.position = Vec2(HUD_MARGIN + 100f, hudY + 18f)

        // Menu horizontally centered, vertically stacked starting at 1/3 of
        // the surface height.
        val menuX = (w - BUTTON_WIDTH) / 2f
        val firstY = h / 3f
        startButton.position = Vec2(menuX, firstY)
        settingsButton.position = Vec2(menuX, firstY + BUTTON_HEIGHT + BUTTON_GAP)
        quitButton.position = Vec2(menuX, firstY + (BUTTON_HEIGHT + BUTTON_GAP) * 2)
    }

    private companion object {
        const val TAG = "UiPlayground"

        const val HUD_WIDTH = 200f
        const val HUD_HEIGHT = 50f
        const val HUD_MARGIN = 10f

        const val BUTTON_WIDTH = 200f
        const val BUTTON_HEIGHT = 50f
        const val BUTTON_GAP = 20f
    }
}
