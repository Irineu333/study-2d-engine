package com.neoutils.engine.skiko

import com.neoutils.engine.scene.Label
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SkikoTextMeasurerTest {

    @Test
    fun `measurer matches the renderer for the same text and size`() {
        val measurer = SkikoTextMeasurer()
        val renderer = SkikoRenderer()
        for (size in listOf(12f, 14f, 24f)) {
            val expected = renderer.measureText("Hello", size)
            assertEquals(expected, measurer.measureText("Hello", size), "mismatch at size $size")
        }
    }

    @Test
    fun `measurer reports non-zero dimensions off-frame`() {
        val measured = SkikoTextMeasurer().measureText("Hi", 12f)
        assertTrue(measured.x > 0f && measured.y > 0f, "expected non-zero, got $measured")
    }

    @Test
    fun `Label reports non-null local bounds once the measurer is wired`() {
        val tree = SceneTree(Node())
        tree.textMeasurer = SkikoTextMeasurer()
        tree.start()
        val label = Label().apply { text = "Hi"; size = 12f }
        tree.root.addChild(label)
        assertNotNull(label.localBounds())
    }
}
