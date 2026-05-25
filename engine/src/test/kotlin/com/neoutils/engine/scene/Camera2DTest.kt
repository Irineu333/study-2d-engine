package com.neoutils.engine.scene

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Camera2DTest {

    private fun assertApprox(expected: Vec2, actual: Vec2, eps: Float = 0.001f) {
        assertTrue(
            abs(expected.x - actual.x) < eps && abs(expected.y - actual.y) < eps,
            "expected $expected, got $actual (eps=$eps)",
        )
    }

    @Test
    fun `defaults are bounds zero, current false, aspect FIT`() {
        val c = Camera2D()
        assertEquals(Rect(Vec2.ZERO, Vec2.ZERO), c.bounds)
        assertEquals(false, c.current)
        assertEquals(AspectMode.FIT, c.aspectMode)
    }

    @Test
    fun `degenerate bounds yield null view transform`() {
        val c = Camera2D().apply { bounds = Rect(Vec2.ZERO, Vec2.ZERO) }
        assertNull(c.computeViewTransform(Vec2(800f, 600f)))

        val c2 = Camera2D().apply { bounds = Rect(Vec2.ZERO, Vec2(-1f, 100f)) }
        assertNull(c2.computeViewTransform(Vec2(800f, 600f)))
    }

    @Test
    fun `FIT scales by min and centers letterbox`() {
        val c = Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))
            aspectMode = AspectMode.FIT
        }
        // 1280×900 surface, world 800×600 → scale = min(1.6, 1.5) = 1.5,
        // projected size = (1200, 900); horizontal offset = (1280-1200)/2 = 40.
        val (t, s) = c.computeViewTransform(Vec2(1280f, 900f))!!
        assertApprox(Vec2(40f, 0f), t)
        assertApprox(Vec2(1.5f, 1.5f), s)
    }

    @Test
    fun `FIT on portrait surface centers vertically`() {
        val c = Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))
            aspectMode = AspectMode.FIT
        }
        // 800×900 → scale = min(1.0, 1.5) = 1.0; projected = (800, 600);
        // vertical offset = (900-600)/2 = 150.
        val (t, s) = c.computeViewTransform(Vec2(800f, 900f))!!
        assertApprox(Vec2(0f, 150f), t)
        assertApprox(Vec2(1f, 1f), s)
    }

    @Test
    fun `FILL scales by max and crops`() {
        val c = Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))
            aspectMode = AspectMode.FILL
        }
        // 1280×900 → scale = max(1.6, 1.5) = 1.6; projected = (1280, 960);
        // vertical offset = (900-960)/2 = -30 (crops 30 px top & bottom).
        val (t, s) = c.computeViewTransform(Vec2(1280f, 900f))!!
        assertApprox(Vec2(0f, -30f), t)
        assertApprox(Vec2(1.6f, 1.6f), s)
    }

    @Test
    fun `STRETCH applies independent axis scales`() {
        val c = Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))
            aspectMode = AspectMode.STRETCH
        }
        val (t, s) = c.computeViewTransform(Vec2(1600f, 900f))!!
        assertApprox(Vec2(0f, 0f), t)
        assertApprox(Vec2(2f, 1.5f), s)
    }

    @Test
    fun `FIT square bounds on square surface is identity`() {
        val c = Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2(500f, 500f))
            aspectMode = AspectMode.FIT
        }
        val (t, s) = c.computeViewTransform(Vec2(500f, 500f))!!
        assertApprox(Vec2.ZERO, t)
        assertApprox(Vec2(1f, 1f), s)
    }

    @Test
    fun `worldToScreen and screenToWorld round-trip for FIT`() {
        val c = Camera2D().apply {
            bounds = Rect(Vec2(0f, 0f), Vec2(800f, 600f))
            aspectMode = AspectMode.FIT
        }
        val size = Vec2(1280f, 900f)
        val samples = listOf(Vec2(0f, 0f), Vec2(800f, 600f), Vec2(400f, 300f), Vec2(123.45f, 67.89f))
        for (p in samples) {
            val screen = c.worldToScreen(p, size)
            val back = c.screenToWorld(screen, size)
            assertApprox(p, back)
        }
    }

    @Test
    fun `round-trip for FILL`() {
        val c = Camera2D().apply {
            bounds = Rect(Vec2(0f, 0f), Vec2(800f, 600f))
            aspectMode = AspectMode.FILL
        }
        val size = Vec2(1280f, 900f)
        val samples = listOf(Vec2(0f, 0f), Vec2(800f, 600f), Vec2(400f, 300f))
        for (p in samples) {
            val back = c.screenToWorld(c.worldToScreen(p, size), size)
            assertApprox(p, back)
        }
    }

    @Test
    fun `round-trip for STRETCH`() {
        val c = Camera2D().apply {
            bounds = Rect(Vec2(0f, 0f), Vec2(800f, 600f))
            aspectMode = AspectMode.STRETCH
        }
        val size = Vec2(1600f, 1200f)
        val samples = listOf(Vec2(0f, 0f), Vec2(800f, 600f), Vec2(400f, 300f), Vec2(50f, 75f))
        for (p in samples) {
            val back = c.screenToWorld(c.worldToScreen(p, size), size)
            assertApprox(p, back)
        }
    }

    @Test
    fun `degenerate bounds yield identity helpers`() {
        val c = Camera2D().apply { bounds = Rect(Vec2.ZERO, Vec2.ZERO) }
        val p = Vec2(50f, 50f)
        assertEquals(p, c.screenToWorld(p, Vec2(800f, 600f)))
        assertEquals(p, c.worldToScreen(p, Vec2(800f, 600f)))
    }

    @Test
    fun `SceneTree screenToWorld delegates to current camera`() {
        val root = Node()
        val camera = Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))
            current = true
            aspectMode = AspectMode.FIT
        }
        root.addChild(camera)
        val tree = com.neoutils.engine.tree.SceneTree(root)
        tree.resize(1280f, 900f)
        tree.start()

        val screenCenter = Vec2(640f, 450f)
        assertApprox(Vec2(400f, 300f), tree.screenToWorld(screenCenter))
        assertApprox(screenCenter, tree.worldToScreen(Vec2(400f, 300f)))
    }

    @Test
    fun `SceneTree screenToWorld identity without camera`() {
        val root = Node()
        val tree = com.neoutils.engine.tree.SceneTree(root)
        tree.resize(800f, 600f)
        tree.start()

        val p = Vec2(123f, 456f)
        assertEquals(p, tree.screenToWorld(p))
        assertEquals(p, tree.worldToScreen(p))
    }

    @Test
    fun `non-zero origin shifts translation`() {
        val c = Camera2D().apply {
            bounds = Rect(Vec2(100f, 50f), Vec2(800f, 600f))
            aspectMode = AspectMode.FIT
        }
        // world point (100, 50) is the top-left of bounds; it should land at
        // the centered surface offset, NOT at the surface origin.
        val view = c.computeViewTransform(Vec2(1280f, 900f))
        assertNotNull(view)
        val screen = c.worldToScreen(Vec2(100f, 50f), Vec2(1280f, 900f))
        // With scale 1.5 the projected size is (1200, 900), centered → x off = 40, y off = 0.
        assertApprox(Vec2(40f, 0f), screen)
    }
}
