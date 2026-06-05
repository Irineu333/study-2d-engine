package com.neoutils.engine.scene

import com.neoutils.engine.math.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pure resolution-fit math shared by `Camera2D` and the UI stretch pass.
 */
class FitTransformTest {

    @Test
    fun `FIT letterboxes horizontally and centers`() {
        val (t, s) = fitTransform(Vec2(800f, 600f), Vec2(1200f, 600f), AspectMode.FIT)!!
        assertEquals(Vec2(200f, 0f), t)
        assertEquals(Vec2(1f, 1f), s)
    }

    @Test
    fun `FIT scales content down on a smaller surface`() {
        val (t, s) = fitTransform(Vec2(800f, 600f), Vec2(400f, 300f), AspectMode.FIT)!!
        assertEquals(Vec2(0f, 0f), t)
        assertEquals(Vec2(0.5f, 0.5f), s)
    }

    @Test
    fun `FILL covers the surface and crops vertically`() {
        // max(1200/800, 600/600) = 1.5 → projected 1200x900, centered → y offset -150.
        val (t, s) = fitTransform(Vec2(800f, 600f), Vec2(1200f, 600f), AspectMode.FILL)!!
        assertEquals(Vec2(0f, -150f), t)
        assertEquals(Vec2(1.5f, 1.5f), s)
    }

    @Test
    fun `STRETCH scales per-axis with no centering`() {
        val (t, s) = fitTransform(Vec2(800f, 600f), Vec2(1200f, 600f), AspectMode.STRETCH)!!
        assertEquals(Vec2(0f, 0f), t)
        assertEquals(Vec2(1.5f, 1f), s)
    }

    @Test
    fun `identity surface yields null`() {
        assertNull(fitTransform(Vec2(800f, 600f), Vec2(800f, 600f), AspectMode.FIT))
        assertNull(fitTransform(Vec2(800f, 600f), Vec2(800f, 600f), AspectMode.STRETCH))
    }

    @Test
    fun `degenerate design yields null`() {
        assertNull(fitTransform(Vec2(0f, 600f), Vec2(800f, 600f), AspectMode.FIT))
        assertNull(fitTransform(Vec2(800f, 0f), Vec2(800f, 600f), AspectMode.FIT))
    }
}
