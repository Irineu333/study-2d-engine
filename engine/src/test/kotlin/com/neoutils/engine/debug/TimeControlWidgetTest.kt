package com.neoutils.engine.debug

import com.neoutils.engine.input.Input
import com.neoutils.engine.input.Key
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.loop.GameLoop
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.PhysicsSystem
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Panel
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class MouseInput(
    var pointer: Vec2 = Vec2.ZERO,
    var leftClicked: Boolean = false,
    var leftDown: Boolean = false,
) : Input {
    override val pointerPosition: Vec2 get() = pointer
    override var mouseClickConsumed: Boolean = false
    override fun isKeyDown(key: Key): Boolean = false
    override fun wasKeyPressed(key: Key): Boolean = false
    override fun isMouseDown(button: MouseButton): Boolean = button == MouseButton.Left && leftDown
    override fun wasMouseClickedRaw(button: MouseButton): Boolean = button == MouseButton.Left && leftClicked
}

private class NoopRenderer : Renderer {
    override fun clear(color: Color) {}
    override fun drawRect(rect: Rect, color: Color, filled: Boolean) {}
    override fun drawCircle(center: Vec2, radius: Float, color: Color, filled: Boolean, thickness: Float) {}
    override fun drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color) {}
    override fun drawText(text: String, position: Vec2, size: Float, color: Color) {}
    override fun measureText(text: String, size: Float): Vec2 = Vec2.ZERO
    override fun drawPolygon(points: List<Vec2>, color: Color) {}
    override fun pushTransform(translation: Vec2, rotation: Float, scale: Vec2) {}
    override fun popTransform() {}
    override fun pushClip(rect: Rect) {}
    override fun popClip() {}
}

private class StepProbe : Node() {
    var physicsSteps = 0
    override fun onPhysicsProcess(dt: Float) { physicsSteps++ }
}

class TimeControlWidgetTest {

    /** Builds the widget's panel and returns the named button. */
    private fun buttonNamed(tree: SceneTree, name: String): Button {
        tree.debug.timeControls.enabled = true
        tree.process(0.016f)
        tree.applyPending()
        val panel = tree.debug.timeControls.children.filterIsInstance<Panel>().single()
        return panel.children.filterIsInstance<Button>().single { it.name == name }
    }

    private fun click(tree: SceneTree, input: MouseInput, button: Button) {
        val rect = button.screenRect()
        input.pointer = Vec2(rect.origin.x + rect.size.x / 2f, rect.origin.y + rect.size.y / 2f)
        input.leftClicked = true
        input.leftDown = true
        tree.input = input
        tree.hitTestUI(input) // arms the button
        tree.process(0.016f)  // held, no emit
        tree.applyPending()
        input.leftClicked = false
        input.leftDown = false
        tree.process(0.016f)  // released inside → pressed emits
        tree.applyPending()
    }

    @Test
    fun `widget is a registered built-in and a togglable HUD row`() {
        val tree = SceneTree(Node()).also { it.start() }
        assertNotNull(tree.debug.timeControls)
        assertTrue(tree.debug.timeControls in tree.debug.widgets)

        tree.debug.hud.enabled = true
        tree.process(0.016f)
        tree.applyPending()
        val panel = tree.debug.hud.children.filterIsInstance<Panel>().single()
        val rowLabels = panel.children.filterIsInstance<Button>().map { it.text }
        assertTrue(rowLabels.any { it == "[ ] Time" }, "got $rowLabels")
    }

    @Test
    fun `resume control clears paused while paused`() {
        val tree = SceneTree(Node()).also { it.start() }
        tree.paused = true
        val pauseButton = buttonNamed(tree, "TimeControlPause")
        click(tree, MouseInput(), pauseButton)
        assertFalse(tree.paused, "activating the resume control unpauses the tree")
    }

    @Test
    fun `speed steppers raise and lower timeScale clamped at the ends`() {
        val tree = SceneTree(Node()).also { it.start() }
        val input = MouseInput()

        val plus = buttonNamed(tree, "TimeControlSpeedUp")
        click(tree, input, plus)
        assertEquals(2f, tree.timeScale, "+ steps 1.00x → 2.00x")
        click(tree, input, plus)
        click(tree, input, plus) // already at 4x, clamps
        assertEquals(4f, tree.timeScale, "+ clamps at the top preset")

        val minus = tree.debug.timeControls.children
            .filterIsInstance<Panel>().single()
            .children.filterIsInstance<Button>().single { it.name == "TimeControlSpeedDown" }
        repeat(10) { click(tree, input, minus) }
        assertEquals(0.25f, tree.timeScale, "- clamps at the bottom preset")
    }

    @Test
    fun `speed display button never consumes clicks`() {
        val tree = SceneTree(Node()).also { it.start() }
        val display = buttonNamed(tree, "TimeControlSpeedDisplay")
        assertTrue(display.disabled)
    }

    @Test
    fun `stepSpeed steps one preset and clamps at the ends`() {
        assertEquals(0.5f, TimeControlWidget.stepSpeed(0.25f, up = true))
        assertEquals(0.25f, TimeControlWidget.stepSpeed(0.25f, up = false), "clamps at the bottom")
        assertEquals(4f, TimeControlWidget.stepSpeed(4f, up = true), "clamps at the top")
        assertEquals(2f, TimeControlWidget.stepSpeed(4f, up = false))
    }

    @Test
    fun `step control requests a step and the next tick advances one`() {
        val root = Node()
        val probe = StepProbe()
        root.addChild(probe)
        val tree = SceneTree(root).also { it.start() }
        tree.paused = true

        val stepButton = buttonNamed(tree, "TimeControlStep")
        click(tree, MouseInput(), stepButton)
        assertTrue(tree.hasPendingStep, "step control calls requestStep()")

        val loop = GameLoop(tree, NoopRenderer(), MouseInput(), PhysicsSystem())
        loop.tick(1_000_000_000L / 60L)
        assertEquals(1, probe.physicsSteps, "the next tick advances exactly one physics step")
    }
}
