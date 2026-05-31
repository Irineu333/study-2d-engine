package com.neoutils.engine.debug

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.scene.ColorRect
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.FakeInput
import com.neoutils.engine.tree.SceneTree
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectionGizmoWidgetTest {

    private fun selectionLines(recorder: RecordingRenderer): List<RecordedEvent.Line> =
        recorder.events.filterIsInstance<RecordedEvent.Line>()
            .filter { it.color == DEBUG_SELECTION_COLOR }

    @Test
    fun `oriented box drawn around the selection matches world apply of corners`() {
        val target = ColorRect().apply {
            name = "Target"
            size = Vec2(40f, 30f)
            transform = Transform(position = Vec2(100f, 100f), rotation = (PI / 6.0).toFloat())
        }
        val tree = SceneTree(Node().apply { addChild(target) }).also { it.start() }
        // Enabling the picker is the single switch — the gizmo follows it.
        tree.debug.scenePicker.enabled = true
        // Click the rotated box center: local (20,15) → world.
        val center = target.world().apply(Vec2(20f, 15f))
        tree.hitTestPick(FakeInput(pointer = center, leftClicked = true))

        val recorder = RecordingRenderer()
        tree.render(recorder)

        val lines = selectionLines(recorder)
        assertEquals(4, lines.size, "an oriented box has four edges")
        val expected = target.localBounds()!!.corners().map { target.world().apply(it) }
        for (i in expected.indices) {
            val edge = lines[i]
            assertApprox(expected[i], edge.from)
            assertApprox(expected[(i + 1) % expected.size], edge.to)
        }
    }

    @Test
    fun `nothing drawn without a selection`() {
        val tree = SceneTree(Node()).also { it.start() }
        tree.debug.scenePicker.enabled = true
        // Picker on but no pick performed → no selection.
        val recorder = RecordingRenderer()
        tree.render(recorder)
        assertTrue(selectionLines(recorder).isEmpty())
    }

    @Test
    fun `nothing drawn when the picker is disabled even with a prior selection`() {
        val target = ColorRect().apply {
            name = "Target"
            size = Vec2(40f, 40f)
            transform = Transform(position = Vec2(100f, 100f))
        }
        val tree = SceneTree(Node().apply { addChild(target) }).also { it.start() }
        tree.debug.scenePicker.enabled = true
        tree.hitTestPick(FakeInput(pointer = Vec2(120f, 120f), leftClicked = true))
        // Turn the tool off — the gizmo must stop drawing.
        tree.debug.scenePicker.enabled = false
        val recorder = RecordingRenderer()
        tree.render(recorder)
        assertTrue(selectionLines(recorder).isEmpty())
    }

    private fun assertApprox(expected: Vec2, actual: Vec2, eps: Float = 1e-3f) {
        assertTrue(
            kotlin.math.abs(expected.x - actual.x) < eps &&
                kotlin.math.abs(expected.y - actual.y) < eps,
            "expected $expected, actual $actual",
        )
    }
}
