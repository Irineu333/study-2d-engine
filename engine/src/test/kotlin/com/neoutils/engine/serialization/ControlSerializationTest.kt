package com.neoutils.engine.serialization

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.scene.FocusMode
import com.neoutils.engine.scene.MouseFilter
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Panel
import com.neoutils.engine.tree.SceneTree
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Anchors/offsets/`visible`/`mouseFilter`/inert fields round-trip via scene.json. */
class ControlSerializationTest {

    @BeforeTest
    fun setUp() {
        NodeRegistry.clear()
        NodeRegistry.registerEngineTypes()
    }

    @AfterTest
    fun tearDown() {
        NodeRegistry.clear()
    }

    @Test
    fun `anchors offsets visible mouseFilter round-trip`() {
        val root = Node().apply {
            addChild(Panel().apply {
                name = "P"
                anchorLeft = 0f; anchorTop = 0f; anchorRight = 1f; anchorBottom = 0.5f
                offsetLeft = 5f; offsetTop = 6f; offsetRight = -7f; offsetBottom = 8f
                visible = false
                mouseFilter = MouseFilter.PASS
                focusMode = FocusMode.ALL
                sizeFlagsHorizontal = 3
            })
        }
        val reloaded = SceneLoader.load(SceneLoader.save(root))
        val p = reloaded.findChild("P") as Panel

        assertEquals(0f, p.anchorLeft)
        assertEquals(1f, p.anchorRight)
        assertEquals(0.5f, p.anchorBottom)
        assertEquals(5f, p.offsetLeft)
        assertEquals(6f, p.offsetTop)
        assertEquals(-7f, p.offsetRight)
        assertEquals(8f, p.offsetBottom)
        assertEquals(false, p.visible)
        assertEquals(MouseFilter.PASS, p.mouseFilter)
        assertEquals(FocusMode.ALL, p.focusMode)
        assertEquals(3, p.sizeFlagsHorizontal)
    }

    @Test
    fun `widget that only sets position and size keeps its rect after round-trip`() {
        val root = Node().apply {
            addChild(Panel().apply {
                name = "P"
                position = Vec2(40f, 60f)
                size = Vec2(120f, 30f)
            })
        }
        val reloaded = SceneLoader.load(SceneLoader.save(root))
        val tree = SceneTree(reloaded)
        tree.resize(800f, 600f)
        tree.start()
        tree.render(RecordingRenderer())

        val p = reloaded.findChild("P") as Panel
        assertEquals(Vec2(40f, 60f), p.position)
        assertEquals(Vec2(120f, 30f), p.size)
    }
}
