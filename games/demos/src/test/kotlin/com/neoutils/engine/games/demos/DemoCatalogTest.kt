package com.neoutils.engine.games.demos

import com.neoutils.engine.input.Input
import com.neoutils.engine.input.Key
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.loop.GameLoop
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.render.Texture
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.Camera2D
import com.neoutils.engine.scene.CanvasLayer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Scripted [Input] with a settable pointer, mouse-down and raw-click edge. */
private class ScriptedInput : Input {
    override var pointerPosition: Vec2 = Vec2.ZERO
    var down: Boolean = false
    var clickedRaw: Boolean = false

    override fun isKeyDown(key: Key) = false
    override fun wasKeyPressed(key: Key) = false
    override fun isMouseDown(button: MouseButton) = down && button == MouseButton.Left
    override fun wasMouseClickedRaw(button: MouseButton) = clickedRaw && button == MouseButton.Left
    override var mouseClickConsumed: Boolean = false
}

/** Headless renderer: the loop needs one, but the tests only observe topology. */
private object NoOpRenderer : Renderer {
    override fun clear(color: Color) {}
    override fun drawRect(rect: Rect, color: Color, filled: Boolean) {}
    override fun drawCircle(center: Vec2, radius: Float, color: Color, filled: Boolean, thickness: Float) {}
    override fun drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color) {}
    override fun drawText(text: String, position: Vec2, size: Float, color: Color) {}
    override fun measureText(text: String, size: Float): Vec2 = Vec2(text.length * size * 0.5f, size)
    override fun drawPolygon(points: List<Vec2>, color: Color) {}
    override fun drawImage(texture: Texture, src: Rect, dst: Rect, flipH: Boolean) {}
    override fun pushTransform(translation: Vec2, rotation: Float, scale: Vec2) {}
    override fun popTransform() {}
    override fun pushClip(rect: Rect) {}
    override fun popClip() {}
}

private const val FRAME_NANOS = 16_700_000L

class DemoCatalogTest {

    private fun newTree(): Pair<SceneTree, GameLoop> {
        val tree = SceneTree(root = DemoSwitcherRoot())
        tree.resize(800f, 600f)
        tree.textMeasurer = com.neoutils.engine.render.TextMeasurer { text, size ->
            Vec2(text.length * size * 0.5f, size)
        }
        tree.start()
        val loop = GameLoop(tree = tree, renderer = NoOpRenderer, input = ScriptedInput())
        return tree to loop
    }

    private fun collectButtons(node: Node, out: MutableList<Button>) {
        if (node is Button) out += node
        node.children.forEach { collectButtons(it, out) }
    }

    private fun menuButtons(tree: SceneTree): List<Button> {
        val menu = tree.root.findChild("Menu") as? CanvasLayer ?: return emptyList()
        val out = mutableListOf<Button>()
        collectButtons(menu, out)
        return out
    }

    /** Down-then-up click cycle at [pos]; emits the Button's `pressed` on release. */
    private fun click(loop: GameLoop, input: ScriptedInput, pos: Vec2) {
        input.pointerPosition = pos
        input.down = true
        input.clickedRaw = true
        loop.tick(FRAME_NANOS)
        input.clickedRaw = false
        input.down = false
        loop.tick(FRAME_NANOS)
    }

    @Test
    fun `menu shows one button per demo`() {
        val (tree, loop) = newTree()
        loop.tick(FRAME_NANOS) // resolve the anchor layout
        val buttons = menuButtons(tree).map { it.name }.toSet()
        assertEquals(
            setOf("Transforms", "Spawn & Collide", "Rotating Frame", "Tumbling Swarm", "Sprites & Tiles"),
            buttons,
            "menu must host exactly one button per demo",
        )
    }

    @Test
    fun `clicking a demo button loads it and the back button returns to the menu`() {
        val (tree, loop) = newTree()
        val input = loop.input as ScriptedInput
        loop.tick(FRAME_NANOS) // resolve the anchor layout

        val transformsButton = menuButtons(tree).first { it.name == "Transforms" }
        val rect = transformsButton.screenRect()!!
        click(loop, input, Vec2(rect.origin.x + rect.size.x / 2f, rect.origin.y + rect.size.y / 2f))

        assertNull(tree.root.findChild("Menu"), "menu must be removed once a demo loads")
        assertNotNull(tree.root.findChild("SolarSystemDemo"), "Transforms must load the solar system demo")
        assertNotNull(tree.root.findChild("DemoOverlay"), "loaded demo must carry an overlay")

        val back = (tree.root.findChild("DemoOverlay") as CanvasLayer).findChild("BackButton") as Button
        val backRect = back.screenRect()!!
        click(loop, input, Vec2(backRect.origin.x + backRect.size.x / 2f, backRect.origin.y + backRect.size.y / 2f))

        assertNull(tree.root.findChild("SolarSystemDemo"), "back button must remove the demo")
        assertNotNull(tree.root.findChild("Menu"), "back button must restore the menu")
    }

    @Test
    fun `dragging the mouse pans the Transforms camera`() {
        val tree = SceneTree(root = SolarSystemDemo())
        tree.resize(800f, 600f)
        tree.textMeasurer = com.neoutils.engine.render.TextMeasurer { text, size -> Vec2(text.length * size * 0.5f, size) }
        tree.start()
        val input = ScriptedInput()
        val loop = GameLoop(tree = tree, renderer = NoOpRenderer, input = input)
        loop.tick(FRAME_NANOS) // finalize camera bounds

        val cam = tree.root.findChild("Camera") as Camera2D
        val before = cam.bounds.origin

        // Grab and drag down-right: the camera follows the cursor, so bounds.origin
        // shifts up-left (the grabbed world point stays pinned under the cursor).
        input.down = true
        input.pointerPosition = Vec2(400f, 300f)
        loop.tick(FRAME_NANOS) // arm the drag (no motion yet)
        input.pointerPosition = Vec2(460f, 340f)
        loop.tick(FRAME_NANOS) // pan applied
        input.down = false

        val after = cam.bounds.origin
        assertTrue(after.x < before.x, "dragging right must shift bounds.origin left (got ${before.x} -> ${after.x})")
        assertTrue(after.y < before.y, "dragging down must shift bounds.origin up (got ${before.y} -> ${after.y})")
    }

    @Test
    fun `solar system keeps its celestial topology plus a current camera`() {
        val demo = SolarSystemDemo()
        val center = demo.findChild("Center") as Node2D
        val childNames = center.children.map { it.name }.toSet()
        val expectedOrbits = setOf(
            "MercuryOrbit", "VenusOrbit", "EarthOrbit", "MarsOrbit",
            "JupiterOrbit", "SaturnOrbit", "UranusOrbit", "NeptuneOrbit",
        )
        assertTrue("Sun" in childNames, "Center must contain the Sun")
        assertTrue(childNames.containsAll(expectedOrbits), "Center must contain all eight planet orbits")

        // The fold adds an interactive Camera2D without touching the
        // celestial-body counts under Center.
        val camera = demo.findChild("Camera") as? Camera2D
        assertNotNull(camera, "Transforms demo must install a Camera2D")
        assertTrue(camera.current, "the camera must be current")
    }
}
