package com.neoutils.engine.scene

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.TextMeasurer
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LeafBoundsTest {

    @Test
    fun `Panel local bounds matches its drawn rect`() {
        val panel = Panel().apply { size = Vec2(100f, 50f) }
        assertEquals(Rect(Vec2.ZERO, Vec2(100f, 50f)), panel.localBounds())
    }

    @Test
    fun `ColorRect local bounds matches its drawn rect`() {
        val rect = ColorRect().apply { size = Vec2(30f, 12f) }
        assertEquals(Rect(Vec2.ZERO, Vec2(30f, 12f)), rect.localBounds())
    }

    @Test
    fun `Circle2D local bounds is centered`() {
        val circle = Circle2D().apply { radius = 8f }
        assertEquals(Rect(Vec2(-8f, -8f), Vec2(16f, 16f)), circle.localBounds())
    }

    @Test
    fun `Label uses the text measurer even when never drawn`() {
        val tree = SceneTree(Node())
        tree.textMeasurer = TextMeasurer { _, _ -> Vec2(14f, 12f) }
        tree.start()
        val label = Label().apply { text = "Hi"; fontSize = 12f }
        tree.root.addChild(label)
        assertEquals(Rect(Vec2.ZERO, Vec2(14f, 12f)), label.localBounds())
    }

    @Test
    fun `Label detached from any tree has null bounds`() {
        val label = Label().apply { text = "Hi" }
        assertNull(label.localBounds())
    }

    @Test
    fun `Label whose tree has no measurer has null bounds`() {
        val tree = SceneTree(Node())
        tree.start()
        val label = Label().apply { text = "Hi" }
        tree.root.addChild(label)
        assertNull(label.localBounds())
    }
}
