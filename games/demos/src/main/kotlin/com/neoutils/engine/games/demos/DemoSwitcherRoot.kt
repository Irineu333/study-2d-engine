package com.neoutils.engine.games.demos

import com.neoutils.engine.input.Key
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node

/**
 * Hosts the engine-consistency demos and swaps between them at runtime with
 * addChild/removeChild — the switch itself doubles as a tiny stress test of
 * the lifecycle paths.
 */
class DemoSwitcherRoot : Node() {

    enum class Slot { SolarSystem, Scale, Spawner, Stress, RotatingBox, TumblingSwarm, UiPlayground }

    private val factories: Map<Slot, () -> Node> = mapOf(
        Slot.SolarSystem to ::SolarSystemDemo,
        Slot.Scale to ::ScaleHierarchyDemo,
        Slot.Spawner to ::SpawnerDemo,
        Slot.Stress to ::CollisionStressDemo,
        Slot.RotatingBox to ::RotatingBoxDemo,
        Slot.TumblingSwarm to ::TumblingSwarmDemo,
        Slot.UiPlayground to ::UiPlaygroundDemo,
    )

    private var active: Slot = Slot.SolarSystem
    private var activeNode: Node? = null
    private val hud = HudOverlay { active }

    init {
        name = "DemoSwitcher"
    }

    override fun onEnter() {
        super.onEnter()
        // Guard against re-attach (start/stop/start) — the active demo and the
        // HUD survive across reattachments. Checking the demo slot rather than
        // `children.isEmpty()` keeps us robust against engine-owned siblings
        // (e.g. the auto-inserted DebugLayer).
        if (activeNode != null) return
        // Demos run in raw surface pixels (no Camera2D) by design: they're
        // physics/collision exercises whose visuals follow the window, not a
        // fixed virtual world. Adding a camera would double-scale ball
        // bouncing bounds and HUD positions that read tree.size directly.
        val node = factories.getValue(active)()
        activeNode = node
        addChild(node)
        addChild(hud)
    }

    fun select(slot: Slot) {
        if (slot == active) return
        activeNode?.let { removeChild(it) }
        active = slot
        val node = factories.getValue(slot)()
        activeNode = node
        addChild(node)
    }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        val input = tree?.input ?: return
        when {
            input.wasKeyPressed(Key.DIGIT_1) -> select(Slot.SolarSystem)
            input.wasKeyPressed(Key.DIGIT_2) -> select(Slot.Scale)
            input.wasKeyPressed(Key.DIGIT_3) -> select(Slot.Spawner)
            input.wasKeyPressed(Key.DIGIT_4) -> select(Slot.Stress)
            input.wasKeyPressed(Key.DIGIT_5) -> select(Slot.RotatingBox)
            input.wasKeyPressed(Key.DIGIT_6) -> select(Slot.TumblingSwarm)
            input.wasKeyPressed(Key.DIGIT_7) -> select(Slot.UiPlayground)
        }
    }
}

private class HudOverlay(private val slot: () -> DemoSwitcherRoot.Slot) : Node() {

    override fun onDraw(renderer: Renderer) {
        val name = when (slot()) {
            DemoSwitcherRoot.Slot.SolarSystem -> "1. Solar system (nested transform composition)"
            DemoSwitcherRoot.Slot.Scale -> "2. Scale hierarchy (parent scale -> child size)"
            DemoSwitcherRoot.Slot.Spawner -> "3. Spawner (mutate during update/collide)"
            DemoSwitcherRoot.Slot.Stress -> "4. Collision stress (world-transform cache)"
            DemoSwitcherRoot.Slot.RotatingBox -> "5. Rotating box (ancestor rotation composes into children)"
            DemoSwitcherRoot.Slot.TumblingSwarm -> "6. Tumbling swarm (elastic impulse + angular transfer)"
            DemoSwitcherRoot.Slot.UiPlayground -> "7. UI playground (CanvasLayer + Panel + Button)"
        }
        renderer.drawText(name, Vec2(8f, 18f), size = 16f, color = Color.WHITE)
        renderer.drawText(
            "keys: 1/2/3/4/5/6/7 switch | F1 opens debug HUD (checkboxes)",
            Vec2(8f, 38f),
            size = 12f,
            color = Color(1f, 1f, 1f, 0.7f),
        )
    }
}
