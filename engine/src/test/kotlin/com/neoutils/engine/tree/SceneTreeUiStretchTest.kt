package com.neoutils.engine.tree

import com.neoutils.engine.debug.DebugLayer
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.scene.AspectMode
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.Camera2D
import com.neoutils.engine.scene.CanvasLayer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Panel
import com.neoutils.engine.scene.UiStretchMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * UI stretch on `SceneTree`: design resolution, the stretch transform, and how
 * render / hit-test consume it. The composed transform stack is reconstructed
 * from the recorded `push`/`pop` so assertions are on actual screen pixels.
 */
class SceneTreeUiStretchTest {

    // --- designSize default derivation (5.7) ---

    @Test
    fun `designSize defaults from the current camera bounds`() {
        val root = Node()
        root.addChild(Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2(800f, 600f)); current = true
        })
        val tree = SceneTree(root)
        tree.resize(1200f, 600f)
        tree.start()
        assertEquals(Vec2(800f, 600f), tree.designSize)
    }

    @Test
    fun `designSize defaults from the surface when no camera`() {
        val tree = SceneTree(Node())
        tree.resize(800f, 600f)
        tree.start()
        assertEquals(Vec2(800f, 600f), tree.designSize)
        // No camera, designSize == size → stretch identity (raw screen-space).
        assertEquals(null, tree.uiStretchTransform())
    }

    @Test
    fun `no-camera designSize tracks the surface across resizes`() {
        val tree = SceneTree(Node())
        tree.resize(800f, 600f)
        tree.start()
        assertEquals(Vec2(800f, 600f), tree.designSize)
        assertEquals(null, tree.uiStretchTransform(), "identity at start")

        // Resizing a camera-less tree must keep the stretch identity (raw
        // screen-space), not letterbox the UI against a frozen initial size.
        tree.resize(1024f, 768f)
        assertEquals(Vec2(1024f, 768f), tree.designSize, "designSize follows the surface")
        assertEquals(null, tree.uiStretchTransform(), "still identity after resize")
    }

    @Test
    fun `explicit designSize before start freezes against surface tracking`() {
        val tree = SceneTree(Node())
        tree.designSize = Vec2(400f, 300f)
        tree.resize(800f, 600f)
        tree.start()
        tree.resize(1024f, 768f)
        assertEquals(Vec2(400f, 300f), tree.designSize, "explicit value is frozen, not tracked")
    }

    @Test
    fun `explicit designSize before start suppresses derivation`() {
        val root = Node()
        root.addChild(Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2(800f, 600f)); current = true
        })
        val tree = SceneTree(root)
        tree.designSize = Vec2(320f, 240f)
        tree.resize(1200f, 600f)
        tree.start()
        assertEquals(Vec2(320f, 240f), tree.designSize)
    }

    // --- transform ignores camera pan (5.2) ---

    @Test
    fun `uiStretchTransform ignores camera pan`() {
        val root = Node()
        val cam = Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2(400f, 300f)); current = true
        }
        root.addChild(cam)
        val tree = SceneTree(root)
        tree.resize(800f, 600f)
        tree.start()
        val before = tree.uiStretchTransform()
        // Pan the gameplay camera (bounds.origin is its pan term).
        cam.bounds = Rect(Vec2(123f, 45f), Vec2(400f, 300f))
        cam.position = Vec2(99f, 99f)
        val after = tree.uiStretchTransform()
        assertEquals(before, after)
        assertEquals(Vec2(0f, 0f) to Vec2(2f, 2f), after)
    }

    @Test
    fun `DISABLED mode yields no stretch`() {
        val tree = SceneTree(Node())
        tree.resize(800f, 600f)
        tree.designSize = Vec2(400f, 300f)
        tree.uiStretchMode = UiStretchMode.DISABLED
        tree.start()
        assertEquals(null, tree.uiStretchTransform())
    }

    // --- render scaling (5.3) ---

    @Test
    fun `followStretch Panel scales 2x`() {
        val root = Node()
        root.addChild(Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2(400f, 300f)); current = true
        })
        val layer = CanvasLayer() // followStretch = true (default)
        layer.addChild(Panel().apply {
            transform = Transform(position = Vec2(50f, 50f)); size = Vec2(100f, 100f)
        })
        root.addChild(layer)
        val tree = SceneTree(root)
        tree.resize(800f, 600f) // FIT scale 2x, translation 0
        tree.start()

        val xf = transformAtFirstRect(tree)
        assertEquals(Vec2(100f, 100f), xf.apply(Vec2.ZERO), "design (50,50) → screen (100,100)")
        assertEquals(Vec2(200f, 200f), Vec2(xf.s.x * 100f, xf.s.y * 100f), "size scaled 2x")
    }

    @Test
    fun `raw Panel stays in screen pixels`() {
        val root = Node()
        root.addChild(Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2(400f, 300f)); current = true
        })
        val layer = CanvasLayer().apply { followStretch = false }
        layer.addChild(Panel().apply {
            transform = Transform(position = Vec2(50f, 50f)); size = Vec2(100f, 100f)
        })
        root.addChild(layer)
        val tree = SceneTree(root)
        tree.resize(800f, 600f)
        tree.start()

        val xf = transformAtFirstRect(tree)
        assertEquals(Vec2(50f, 50f), xf.apply(Vec2.ZERO), "raw pixels, no stretch")
        assertEquals(Vec2(1f, 1f), xf.s)
    }

    // --- DebugLayer immunity (5.6) ---

    @Test
    fun `debug screen canvas stays pixel-locked under a non-trivial stretch`() {
        val root = Node()
        root.addChild(Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2(400f, 300f)); current = true
        })
        val tree = SceneTree(root)
        tree.resize(800f, 600f) // FIT 2x
        tree.start()
        // Drop a marker Panel into the engine debug screen canvas.
        val debug = root.findChild(DebugLayer.NODE_NAME) as DebugLayer
        debug.screenContainer.addChild(Panel().apply {
            transform = Transform(position = Vec2(50f, 50f)); size = Vec2(10f, 10f)
        })

        val xf = transformAtFirstRect(tree)
        assertEquals(Vec2(50f, 50f), xf.apply(Vec2.ZERO), "debug stays raw despite 2x stretch")
        assertEquals(Vec2(1f, 1f), xf.s)
    }

    // --- hit-test maps through the stretch (5.5) ---

    @Test
    fun `click maps through the stretch onto the Button`() {
        val root = Node()
        root.addChild(Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2(400f, 300f)); current = true
        })
        val layer = CanvasLayer() // followStretch = true
        val button = Button().apply {
            transform = Transform(position = Vec2(50f, 50f)); size = Vec2(80f, 24f)
        }
        layer.addChild(button)
        root.addChild(layer)
        val tree = SceneTree(root)
        tree.resize(800f, 600f) // 2x; design rect (50,50)-(130,74) → screen (100,100)-(260,148)
        tree.start()

        // Click at screen (120,120) maps to design (60,60) — inside the button.
        val input = FakeInput(pointer = Vec2(120f, 120f), leftClicked = true)
        tree.input = input
        tree.hitTestUI(input)
        assertTrue(input.mouseClickConsumed)
    }

    @Test
    fun `raw layer click is unaffected by the stretch`() {
        val root = Node()
        root.addChild(Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2(400f, 300f)); current = true
        })
        val layer = CanvasLayer().apply { followStretch = false }
        val button = Button().apply {
            transform = Transform(position = Vec2(50f, 50f)); size = Vec2(80f, 24f)
        }
        layer.addChild(button)
        root.addChild(layer)
        val tree = SceneTree(root)
        tree.resize(800f, 600f)
        tree.start()

        // Raw layer: the screen pointer must be inside the raw rect (50,50)-(130,74).
        val input = FakeInput(pointer = Vec2(60f, 60f), leftClicked = true)
        tree.input = input
        tree.hitTestUI(input)
        assertTrue(input.mouseClickConsumed)
    }

    // --- helpers ---

    private class Xf(val t: Vec2, val s: Vec2) {
        fun apply(p: Vec2): Vec2 = Vec2(t.x + s.x * p.x, t.y + s.y * p.y)
    }

    /** Composes the push/pop stack and returns the transform in effect at the
     *  first `drawRect`, so a Panel's local rect can be projected to screen. */
    private fun transformAtFirstRect(tree: SceneTree): Xf {
        val recorder = RecordingRenderer()
        tree.render(recorder)
        val stack = ArrayDeque<Xf>()
        stack.addLast(Xf(Vec2.ZERO, Vec2.ONE))
        for (e in recorder.events) {
            when (e) {
                is RecordedEvent.Push -> {
                    val p = stack.last()
                    stack.addLast(
                        Xf(
                            Vec2(p.t.x + p.s.x * e.translation.x, p.t.y + p.s.y * e.translation.y),
                            Vec2(p.s.x * e.scale.x, p.s.y * e.scale.y),
                        ),
                    )
                }
                is RecordedEvent.Pop -> stack.removeLast()
                is RecordedEvent.Rect -> return stack.last()
                else -> {}
            }
        }
        error("no rect drawn")
    }
}
