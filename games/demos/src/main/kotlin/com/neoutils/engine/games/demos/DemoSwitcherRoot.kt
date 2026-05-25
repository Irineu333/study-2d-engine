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

    enum class Slot { Orbit, Scale, Spawner, Stress, RotatingBox, RotatedSweep }

    private val factories: Map<Slot, () -> Node> = mapOf(
        Slot.Orbit to ::TransformOrbitDemo,
        Slot.Scale to ::ScaleHierarchyDemo,
        Slot.Spawner to ::SpawnerDemo,
        Slot.Stress to ::CollisionStressDemo,
        Slot.RotatingBox to ::RotatingBoxDemo,
        Slot.RotatedSweep to ::RotatedSweepDemo,
    )

    private var active: Slot = Slot.Orbit
    private lateinit var activeNode: Node
    private val hud = HudOverlay { active }

    init {
        name = "DemoSwitcher"
    }

    override fun onEnter() {
        super.onEnter()
        if (children.isNotEmpty()) return
        // Demos run in raw surface pixels (no Camera2D) by design: they're
        // physics/collision exercises whose visuals follow the window, not a
        // fixed virtual world. Adding a camera would double-scale ball
        // bouncing bounds and HUD positions that read tree.size directly.
        activeNode = factories.getValue(active)()
        addChild(activeNode)
        addChild(hud)
    }

    fun select(slot: Slot) {
        if (slot == active) return
        removeChild(activeNode)
        active = slot
        activeNode = factories.getValue(slot)()
        addChild(activeNode)
    }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        val input = tree?.input ?: return
        when {
            input.wasKeyPressed(Key.DIGIT_1) -> select(Slot.Orbit)
            input.wasKeyPressed(Key.DIGIT_2) -> select(Slot.Scale)
            input.wasKeyPressed(Key.DIGIT_3) -> select(Slot.Spawner)
            input.wasKeyPressed(Key.DIGIT_4) -> select(Slot.Stress)
            input.wasKeyPressed(Key.DIGIT_5) -> select(Slot.RotatingBox)
            input.wasKeyPressed(Key.DIGIT_6) -> select(Slot.RotatedSweep)
        }
    }
}

private class HudOverlay(private val slot: () -> DemoSwitcherRoot.Slot) : Node() {

    override fun onDraw(renderer: Renderer) {
        val name = when (slot()) {
            DemoSwitcherRoot.Slot.Orbit -> "1. Transform orbit (rotation -> position)"
            DemoSwitcherRoot.Slot.Scale -> "2. Scale hierarchy (parent scale -> child size)"
            DemoSwitcherRoot.Slot.Spawner -> "3. Spawner (mutate during update/collide)"
            DemoSwitcherRoot.Slot.Stress -> "4. Collision stress (world-transform cache)"
            DemoSwitcherRoot.Slot.RotatingBox -> "5. Rotating box (ancestor rotation composes into children)"
            DemoSwitcherRoot.Slot.RotatedSweep -> "6. Rotated sweep (CCD with non-axis-aligned bodies)"
        }
        renderer.drawText(name, Vec2(8f, 18f), size = 16f, color = Color.WHITE)
        renderer.drawText(
            "keys: 1/2/3/4/5/6 switch | F1 fps | F2 colliders",
            Vec2(8f, 38f),
            size = 12f,
            color = Color(1f, 1f, 1f, 0.7f),
        )
    }
}
