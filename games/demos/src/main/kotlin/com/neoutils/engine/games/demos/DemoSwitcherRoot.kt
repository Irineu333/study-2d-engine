package com.neoutils.engine.games.demos

import com.neoutils.engine.render.Color
import com.neoutils.engine.scene.CanvasLayer
import com.neoutils.engine.scene.Label
import com.neoutils.engine.scene.LayoutPreset
import com.neoutils.engine.scene.MouseFilter
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Panel

/**
 * Hosts the engine-consistency demos and swaps between them at runtime with
 * addChild/removeChild — the switch itself doubles as a tiny stress test of
 * the lifecycle paths.
 *
 * Navigation is a real UI menu (no `1`–`0` key polling, no raw `drawText`
 * HUD): the root shows a `CanvasLayer` menu with one `Button` per demo; each
 * loaded demo carries a `DemoOverlay` whose "← Menu" `Button` returns here.
 * The menu + back-button absorb the old dedicated UI demo — `CanvasLayer`,
 * `Panel`, `Button`, `Label`, anchors, z-order and click-consumption are
 * exercised continuously on every screen.
 */
class DemoSwitcherRoot : Node() {

    enum class Slot { Transforms, SpawnCollide, RotatingFrame, TumblingSwarm, SpritesTiles }

    private class Entry(
        val label: String,
        val description: String,
        val factory: () -> Node,
    )

    private val catalog: Map<Slot, Entry> = mapOf(
        Slot.Transforms to Entry(
            label = "Transforms",
            description = "Nested orbits + Camera2D: click a body to lock-follow; arrows hop bodies; scroll/drag to roam",
            factory = ::SolarSystemDemo,
        ),
        Slot.SpawnCollide to Entry(
            label = "Spawn & Collide",
            description = "Click/auto-spawn rigid balls; central Area2D trap removes them",
            factory = ::SpawnCollideDemo,
        ),
        Slot.RotatingFrame to Entry(
            label = "Rotating Frame",
            description = "moveAndCollide sweeps inside a rotating, translating wrapper",
            factory = ::RotatingBoxDemo,
        ),
        Slot.TumblingSwarm to Entry(
            label = "Tumbling Swarm",
            description = "RigidBody2D solver: linear + angular impulse + Coulomb friction",
            factory = ::TumblingSwarmDemo,
        ),
        Slot.SpritesTiles to Entry(
            label = "Sprites & Tiles",
            description = "TileMap ground + AnimatedSprite2D; CharacterBody2D player",
            factory = ::SpritesTilesDemo,
        ),
    )

    private var menu: CanvasLayer? = null
    private var activeNode: Node? = null
    private var activeOverlay: CanvasLayer? = null

    init {
        name = "DemoSwitcher"
    }

    override fun onEnter() {
        super.onEnter()
        // Guard against re-attach (start/stop/start): if a demo or the menu is
        // already shown, don't rebuild. Checking our own navigation state (not
        // children.isEmpty()) keeps us robust against engine-owned siblings
        // (the auto-inserted DebugLayer).
        if (activeNode != null || menu != null) return
        showMenu()
    }

    private fun showMenu() {
        activeNode?.let { removeChild(it) }
        activeOverlay?.let { removeChild(it) }
        activeNode = null
        activeOverlay = null
        if (menu != null) return
        val built = buildMenu()
        menu = built
        addChild(built)
    }

    private fun select(slot: Slot) {
        menu?.let { removeChild(it) }
        menu = null
        val entry = catalog.getValue(slot)
        val node = entry.factory()
        val overlay = DemoOverlay(entry.label, entry.description) { showMenu() }
        activeNode = node
        activeOverlay = overlay
        addChild(node)
        addChild(overlay)
    }

    private fun buildMenu(): CanvasLayer = CanvasLayer().apply {
        name = "Menu"
        layer = 100

        // Full-surface backdrop so the menu reads as a cohesive screen instead
        // of a floating card clashing against the black clear color. Decorative
        // (IGNORE) — only the buttons participate in the hit-test.
        addChild(
            Panel().apply {
                name = "MenuBackdrop"
                color = Color(0.09f, 0.09f, 0.11f, 1f)
                mouseFilter = MouseFilter.IGNORE
                applyPreset(LayoutPreset.FULL_RECT)
                offsetLeft = 0f
                offsetTop = 0f
                offsetRight = 0f
                offsetBottom = 0f
            }
        )

        addChild(
            Label().apply {
                name = "MenuTitle"
                text = "demos"
                fontSize = 20f
                color = Color.WHITE
                // Full-width anchor + zero vertical slack → text centered
                // horizontally at a fixed top offset.
                applyPreset(LayoutPreset.FULL_RECT)
                anchorBottom = 0f
                offsetLeft = 0f
                offsetRight = 0f
                offsetTop = TITLE_TOP
                offsetBottom = TITLE_TOP
            }
        )

        Slot.entries.forEachIndexed { index, slot ->
            val top = BUTTONS_TOP + index * (MENU_BUTTON_HEIGHT + MENU_BUTTON_GAP)
            addChild(menuButton(catalog.getValue(slot).label, top) { select(slot) })
        }
    }

    private companion object {
        const val TITLE_TOP = 90f
        const val BUTTONS_TOP = 140f
    }
}
