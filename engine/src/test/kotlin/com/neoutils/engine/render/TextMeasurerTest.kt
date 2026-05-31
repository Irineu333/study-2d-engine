package com.neoutils.engine.render

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TextMeasurerTest {

    @Test
    fun `SceneTree defaults textMeasurer to null`() {
        assertNull(SceneTree(Node()).textMeasurer)
    }

    @Test
    fun `a measurer assigned to the tree is reachable off-frame`() {
        val tree = SceneTree(Node())
        tree.textMeasurer = TextMeasurer { text, size -> Vec2(text.length * size, size) }
        assertEquals(Vec2(20f, 10f), tree.textMeasurer!!.measureText("ab", 10f))
    }
}
