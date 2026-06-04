package com.neoutils.engine.scene

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Writing `position`/`size` directly mirrors back into the offsets under the
 * current (top-left default) anchors, so the next anchor layout pass reproduces
 * the written rect — preserving the imperative API and scene-file compat.
 */
class ControlWriteBackTest {

    @Test
    fun `writing position updates offsets under top-left anchors`() {
        val panel = Panel().apply { size = Vec2(80f, 24f) }
        panel.position = Vec2(50f, 60f)
        assertEquals(50f, panel.offsetLeft)
        assertEquals(60f, panel.offsetTop)
        assertEquals(130f, panel.offsetRight)
        assertEquals(84f, panel.offsetBottom)
    }

    @Test
    fun `writing size preserves position under top-left anchors`() {
        val panel = Panel().apply { position = Vec2(10f, 10f) }
        panel.size = Vec2(200f, 50f)

        val root = Node()
        val layer = CanvasLayer().apply { addChild(panel) }
        root.addChild(layer)
        val tree = SceneTree(root)
        tree.resize(800f, 600f)
        tree.start()
        tree.render(RecordingRenderer())

        assertEquals(Vec2(10f, 10f), panel.position)
        assertEquals(Vec2(200f, 50f), panel.size)
    }
}
