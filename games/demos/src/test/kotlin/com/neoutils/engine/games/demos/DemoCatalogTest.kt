package com.neoutils.engine.games.demos

import com.neoutils.engine.input.Input
import com.neoutils.engine.input.Key
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.loop.GameLoop
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.Area2D
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.physics.StaticBody2D
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.render.Texture
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.Camera2D
import com.neoutils.engine.scene.CanvasLayer
import com.neoutils.engine.scene.Circle2D
import com.neoutils.engine.scene.Label
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Scripted [Input] with a settable pointer, mouse-down, raw-click and Esc edge. */
private class ScriptedInput : Input {
    override var pointerPosition: Vec2 = Vec2.ZERO
    var down: Boolean = false
    var clickedRaw: Boolean = false
    var esc: Boolean = false

    override fun isKeyDown(key: Key) = false
    override fun wasKeyPressed(key: Key) = esc && key == Key.ESCAPE
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

    /** Loads the Spawn & Collide demo via its menu button and returns it. */
    private fun loadSpawnCollide(tree: SceneTree, loop: GameLoop, input: ScriptedInput): SpawnCollideDemo {
        loop.tick(FRAME_NANOS) // resolve the anchor layout
        val button = menuButtons(tree).first { it.name == "Spawn & Collide" }
        val rect = button.screenRect()!!
        click(loop, input, Vec2(rect.origin.x + rect.size.x / 2f, rect.origin.y + rect.size.y / 2f))
        return tree.root.findChild("SpawnCollideDemo") as SpawnCollideDemo
    }

    @Test
    fun `spawn collide trap exposes two sibling colliders in the arena`() {
        val (tree, loop) = newTree()
        val input = loop.input as ScriptedInput
        val demo = loadSpawnCollide(tree, loop, input)

        val arena = demo.findChild("BoundaryWalls") as Node2D
        val sensor = arena.findChild("TrapSensor")
        val wall = arena.findChild("TrapWall")
        assertNotNull(sensor, "trap sensor must be a direct child of the arena")
        assertNotNull(wall, "trap wall must be a direct child of the arena")
        assertTrue(sensor is Area2D, "TrapSensor must be an Area2D")
        assertTrue(wall is StaticBody2D, "TrapWall must be a StaticBody2D")
        // Both carry a CollisionShape2D + RectangleShape2D of the same size.
        for (collider in listOf(sensor, wall)) {
            val cs = collider.children.firstOrNull { it is CollisionShape2D } as? CollisionShape2D
            assertNotNull(cs, "each trap collider must carry a CollisionShape2D")
            assertTrue(cs.shape is RectangleShape2D, "trap collider shape must be a rectangle")
        }
    }

    @Test
    fun `trap mode toggles which collider is disabled`() {
        val (tree, loop) = newTree()
        val input = loop.input as ScriptedInput
        val demo = loadSpawnCollide(tree, loop, input)
        val arena = demo.findChild("BoundaryWalls") as Node2D
        val sensor = arena.findChild("TrapSensor") as Area2D
        val wall = arena.findChild("TrapWall") as StaticBody2D

        // Default Despawn: sensor live, wall dormant — never both at once.
        demo.state.trapMode = TrapMode.DESPAWN
        loop.tick(FRAME_NANOS)
        assertTrue(!sensor.disabled, "Despawn must keep the sensor live")
        assertTrue(wall.disabled, "Despawn must disable the wall")

        demo.state.trapMode = TrapMode.COLLIDE
        loop.tick(FRAME_NANOS)
        assertTrue(sensor.disabled, "Collide must disable the sensor")
        assertTrue(!wall.disabled, "Collide must keep the wall live")
    }

    @Test
    fun `dragging the trap clamps to the surface and suppresses the spawn`() {
        val (tree, loop) = newTree()
        val input = loop.input as ScriptedInput
        val demo = loadSpawnCollide(tree, loop, input)
        val arena = demo.findChild("BoundaryWalls") as Node2D

        // Quiesce spawning and despawning so the ball count is a clean witness.
        demo.state.autoSpawnEnabled = false
        demo.state.trapMode = TrapMode.COLLIDE
        loop.tick(FRAME_NANOS)
        val before = arena.children.count { it is Ball }

        // Grab the trap at its center, with a raw click on the press edge.
        input.pointerPosition = Vec2(400f, 300f)
        input.down = true
        input.clickedRaw = true
        loop.tick(FRAME_NANOS)
        input.clickedRaw = false
        // Drag far past the bottom-right corner: position must clamp inside.
        input.pointerPosition = Vec2(10_000f, 10_000f)
        loop.tick(FRAME_NANOS)
        input.down = false
        loop.tick(FRAME_NANOS)

        val half = 27f // TRAP_SIZE / 2
        assertEquals(800f - half, demo.state.trapPosition.x, 0.5f, "trap x must clamp to surface - half")
        assertEquals(600f - half, demo.state.trapPosition.y, 0.5f, "trap y must clamp to surface - half")
        val after = arena.children.count { it is Ball }
        assertEquals(before, after, "dragging the trap must not spawn a ball")
    }

    @Test
    fun `auto-spawn gate stops the automatic drip`() {
        val (tree, loop) = newTree()
        val input = loop.input as ScriptedInput
        val demo = loadSpawnCollide(tree, loop, input)
        val arena = demo.findChild("BoundaryWalls") as Node2D

        // Collide mode so the trap never despawns balls during the count.
        demo.state.trapMode = TrapMode.COLLIDE

        demo.state.autoSpawnEnabled = false
        loop.tick(FRAME_NANOS)
        val gatedStart = arena.children.count { it is Ball }
        repeat(180) { loop.tick(FRAME_NANOS) } // ~3s: the drip would fire many times if on
        val gatedEnd = arena.children.count { it is Ball }
        assertEquals(gatedStart, gatedEnd, "auto-spawn off must freeze the ball count")

        demo.state.autoSpawnEnabled = true
        repeat(180) { loop.tick(FRAME_NANOS) }
        val resumed = arena.children.count { it is Ball }
        assertTrue(resumed > gatedEnd, "auto-spawn on must resume the drip (got $gatedEnd -> $resumed)")
    }

    @Test
    fun `SpawnCollideWidget is registered on enter and unregistered on exit`() {
        val (tree, loop) = newTree()
        val input = loop.input as ScriptedInput
        loadSpawnCollide(tree, loop, input)

        assertNotNull(
            tree.debug.find<SpawnCollideWidget>(),
            "loading the demo must register a SpawnCollideWidget",
        )

        // Back to the menu removes the demo, firing onExit -> unregister.
        val back = (tree.root.findChild("DemoOverlay") as CanvasLayer).findChild("BackButton") as Button
        val backRect = back.screenRect()!!
        click(loop, input, Vec2(backRect.origin.x + backRect.size.x / 2f, backRect.origin.y + backRect.size.y / 2f))

        assertNull(
            tree.debug.find<SpawnCollideWidget>(),
            "returning to the menu must unregister the SpawnCollideWidget",
        )
        assertTrue(
            tree.debug.widgets.none { it is SpawnCollideWidget },
            "the widget must be gone from tree.debug.widgets",
        )
    }

    // --- click-to-focus -----------------------------------------------------

    private fun soloDemo(): Triple<SolarSystemDemo, GameLoop, ScriptedInput> {
        val demo = SolarSystemDemo()
        val tree = SceneTree(root = demo)
        tree.resize(800f, 600f)
        tree.textMeasurer = com.neoutils.engine.render.TextMeasurer { text, size ->
            Vec2(text.length * size * 0.5f, size)
        }
        tree.start()
        val input = ScriptedInput()
        val loop = GameLoop(tree = tree, renderer = NoOpRenderer, input = input)
        loop.tick(FRAME_NANOS) // finalize camera bounds to the surface
        return Triple(demo, loop, input)
    }

    private fun findCircle(node: Node, name: String): Circle2D? {
        if (node is Circle2D && node.name == name) return node
        node.children.forEach { findCircle(it, name)?.let { hit -> return hit } }
        return null
    }

    private fun focusName(demo: Node): String {
        val overlay = demo.findChild("FocusOverlay") as? CanvasLayer ?: return ""
        return (overlay.findChild("FocusName") as? Label)?.text ?: ""
    }

    @Test
    fun `pickBody honors smallest-radius tiebreak and the pixel floor`() {
        val (demo, _, _) = soloDemo()
        val cam = demo.findChild("Camera") as Camera2D
        cam.bounds = Rect(Vec2.ZERO, Vec2(800f, 600f)) // scale 1 → 12px floor

        val earth = findCircle(demo, "Earth")!!
        val moon = findCircle(demo, "Moon")!!
        val mercury = findCircle(demo, "Mercury")!!

        // Clicking the planet selects the planet; clicking the moon selects it.
        assertSame(earth, demo.pickBody(earth.world().position, cam))
        assertSame(moon, demo.pickBody(moon.world().position, cam))

        // Zoomed out, the floor grows to 30 world px so both Earth and its Moon
        // sit under the cursor at the moon — the smaller radius (moon) wins.
        cam.bounds = Rect(Vec2.ZERO, Vec2(2000f, 1500f)) // scale 0.4 → 30px floor
        assertSame(moon, demo.pickBody(moon.world().position, cam))

        // The 12px floor makes a 3px Mercury clickable from 10px off-center.
        cam.bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))
        assertSame(mercury, demo.pickBody(mercury.world().position + Vec2(10f, 0f), cam))

        // Empty space picks nothing.
        assertNull(demo.pickBody(Vec2(-5000f, -5000f), cam))
    }

    @Test
    fun `focusing a body keeps the camera centered on it as it orbits`() {
        val (demo, loop, input) = soloDemo()
        val cam = demo.findChild("Camera") as Camera2D

        // Freeze Earth's orbit so the down/up click lands deterministically.
        val earthOrbit = (demo.findChild("Center") as Node2D).findChild("EarthOrbit") as Rotator
        earthOrbit.angularVelocity = 0f
        val earth = findCircle(demo, "Earth")!!
        click(loop, input, cam.worldToScreen(earth.world().position, Vec2(800f, 600f)))
        assertEquals("Earth", focusName(demo), "clicking Earth must focus it")

        // Re-enable the orbit: after a tick the Rotator advances and the
        // FocusController recenters on Earth's up-to-date world position.
        earthOrbit.angularVelocity = 2f
        loop.tick(FRAME_NANOS)
        val center = cam.bounds.origin + cam.bounds.size * 0.5f
        val target = earth.world().position
        assertTrue(
            (center - target).length < 0.5f,
            "camera must stay centered on the focused body (got $center vs $target)",
        )
    }

    @Test
    fun `Esc, empty click and same-body click each unfocus`() {
        val (demo, loop, input) = soloDemo()
        val cam = demo.findChild("Camera") as Camera2D
        val sun = findCircle(demo, "Sun")!!
        val sunScreen = cam.worldToScreen(sun.world().position, Vec2(800f, 600f))

        // Esc unfocuses.
        click(loop, input, sunScreen)
        assertEquals("Sun", focusName(demo))
        input.esc = true
        loop.tick(FRAME_NANOS)
        input.esc = false
        assertEquals("", focusName(demo), "Esc must clear the focus")

        // Clicking empty space unfocuses.
        click(loop, input, sunScreen)
        assertEquals("Sun", focusName(demo))
        click(loop, input, Vec2(5f, 5f))
        assertEquals("", focusName(demo), "clicking empty space must clear the focus")

        // Clicking the same body again toggles it off.
        click(loop, input, sunScreen)
        assertEquals("Sun", focusName(demo))
        click(loop, input, sunScreen)
        assertEquals("", focusName(demo), "clicking the focused body must toggle it off")
    }
}
