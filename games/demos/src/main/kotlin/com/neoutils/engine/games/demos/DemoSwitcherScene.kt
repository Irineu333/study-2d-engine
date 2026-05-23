package com.neoutils.engine.games.demos

import com.neoutils.engine.input.Key
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Scene

/**
 * Hosts the three engine-consistency demos and swaps between them at runtime
 * with addChild/removeChild — the switch itself doubles as a tiny stress test
 * of the lifecycle paths added by this change.
 */
class DemoSwitcherScene : Scene() {

    enum class Slot { Orbit, Scale, Spawner, Stress }

    private val factories: Map<Slot, () -> Node> = mapOf(
        Slot.Orbit to ::TransformOrbitDemo,
        Slot.Scale to ::ScaleHierarchyDemo,
        Slot.Spawner to ::SpawnerDemo,
        Slot.Stress to ::CollisionStressDemo,
    )

    private var active: Slot = Slot.Orbit
    private var activeNode: Node = factories.getValue(active)()
    private val hud = HudOverlay { active }

    init {
        name = "DemoSwitcher"
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

    override fun onUpdate(dt: Float) {
        val input = this.input ?: return
        when {
            input.wasKeyPressed(Key.DIGIT_1) -> select(Slot.Orbit)
            input.wasKeyPressed(Key.DIGIT_2) -> select(Slot.Scale)
            input.wasKeyPressed(Key.DIGIT_3) -> select(Slot.Spawner)
            input.wasKeyPressed(Key.DIGIT_4) -> select(Slot.Stress)
        }
    }
}

private class HudOverlay(private val slot: () -> DemoSwitcherScene.Slot) : Node() {

    override fun onRender(renderer: Renderer) {
        val name = when (slot()) {
            DemoSwitcherScene.Slot.Orbit -> "1. Transform orbit (rotation -> position)"
            DemoSwitcherScene.Slot.Scale -> "2. Scale hierarchy (parent scale -> child size)"
            DemoSwitcherScene.Slot.Spawner -> "3. Spawner (mutate during update/collide)"
            DemoSwitcherScene.Slot.Stress -> "4. Collision stress (world-transform cache)"
        }
        renderer.drawText(name, Vec2(8f, 18f), size = 16f, color = Color.WHITE)
        renderer.drawText(
            "keys: 1/2/3/4 switch | F1 fps | F2 colliders",
            Vec2(8f, 38f),
            size = 12f,
            color = Color(1f, 1f, 1f, 0.7f),
        )
    }
}
