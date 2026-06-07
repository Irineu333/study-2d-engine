package com.neoutils.engine.games.platformer

import com.neoutils.engine.bundle.BundleLoader
import com.neoutils.engine.bundle.lua.LuaScriptHost
import com.neoutils.engine.input.Input
import com.neoutils.engine.input.Key
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.loop.GameLoop
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.CharacterBody2D
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.render.Texture
import com.neoutils.engine.physics.StaticBody2D
import com.neoutils.engine.scene.AnimatedSprite2D
import com.neoutils.engine.scene.Camera2D
import com.neoutils.engine.scene.TileMap
import com.neoutils.engine.serialization.NodeRegistry
import com.neoutils.engine.tree.SceneTree
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Minimal scripted [Input]: [held] keys read by `isKeyDown`, [pressed] keys
 * fire `wasKeyPressed` exactly once then clear (edge semantics) on
 * [endTick].
 */
class ScriptedInput : Input {
    val held = mutableSetOf<Key>()
    private val pressed = mutableSetOf<Key>()

    fun press(key: Key) { pressed.add(key); held.add(key) }
    fun endTick() { pressed.clear() }

    override val pointerPosition: Vec2 = Vec2.ZERO
    override fun isKeyDown(key: Key) = key in held
    override fun wasKeyPressed(key: Key) = key in pressed
    override fun isMouseDown(button: MouseButton) = false
    override fun wasMouseClickedRaw(button: MouseButton) = false
    override var mouseClickConsumed: Boolean = false
}

/** Headless renderer: the loop needs one, but the test only observes physics. */
private object NoOpRenderer : Renderer {
    override fun clear(color: Color) {}
    override fun drawRect(rect: Rect, color: Color, filled: Boolean) {}
    override fun drawCircle(center: Vec2, radius: Float, color: Color, filled: Boolean, thickness: Float) {}
    override fun drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color) {}
    override fun drawText(text: String, position: Vec2, size: Float, color: Color) {}
    override fun measureText(text: String, size: Float): Vec2 = Vec2.ZERO
    override fun drawPolygon(points: List<Vec2>, color: Color) {}
    override fun drawImage(texture: Texture, src: Rect, dst: Rect, flipH: Boolean) {}
    override fun pushTransform(translation: Vec2, rotation: Float, scale: Vec2) {}
    override fun popTransform() {}
    override fun pushClip(rect: Rect) {}
    override fun popClip() {}
}

// Just above physicsDt (1/60 s) so every tick drains exactly one fixed physics
// step — never zero (a float-epsilon miss would drop the jump's edge press).
private const val FRAME_NANOS = 16_700_000L

class PlatformerBundleTest {

    @Before
    fun setUp() {
        NodeRegistry.clear()
    }

    @After
    fun tearDown() {
        NodeRegistry.clear()
    }

    @Test
    fun `bundle loads with the platformer structural nodes`() {
        val lua = LuaScriptHost.create()
        val root = BundleLoader.fromResources("platformer", scripting = lua)

        val camera = root.findChild("MainCamera") as? Camera2D
        assertNotNull(camera, "MainCamera missing")
        assertTrue(camera.current, "MainCamera.current must be true")
        assertTrue(camera.bounds.size.x == 320f && camera.bounds.size.y == 180f, "camera bounds must be 320x180")

        assertNotNull(root.findChild("Terrain") as? TileMap, "Terrain TileMap missing")
        assertNotNull(root.findChild("Ground") as? StaticBody2D, "Ground StaticBody2D missing")

        val player = root.findChild("Player") as? CharacterBody2D
        assertNotNull(player, "Player CharacterBody2D missing")
        assertNotNull(player.findChild("Sprite") as? AnimatedSprite2D, "Player AnimatedSprite2D child missing")
    }

    @Test
    fun `gravity drops the player and the ground stops it`() {
        val lua = LuaScriptHost.create()
        val root = BundleLoader.fromResources("platformer", scripting = lua)
        val tree = SceneTree(root = root)
        tree.start()

        val player = root.findChild("Player") as CharacterBody2D
        val startY = player.position.y

        // Drive two seconds of fixed physics steps: the script's gravity +
        // move_and_collide must pull the player down and rest it on the Ground
        // body (collider top at y=160, half-box 12 -> origin settles near 148).
        repeat(120) { tree.physicsProcess(1f / 60f) }
        val restY = player.position.y

        assertTrue(restY > startY, "player should fall (start=$startY, rest=$restY)")
        assertTrue(restY in 146f..150f, "player should rest on the ground near y=148 (got $restY)")
    }

    @Test
    fun `input moves the player right and a jump lifts it off the floor`() {
        val lua = LuaScriptHost.create()
        val root = BundleLoader.fromResources("platformer", scripting = lua)
        val tree = SceneTree(root = root)
        val input = ScriptedInput()
        // GameLoop wires `tree.input = input` each tick (the setter is engine-internal).
        val loop = GameLoop(tree = tree, renderer = NoOpRenderer, input = input)
        fun tick() { loop.tick(FRAME_NANOS); input.endTick() }

        val player = root.findChild("Player") as CharacterBody2D

        // Settle on the ground first (start x=40 has open sky above — no platform).
        repeat(90) { tick() }
        val groundedX = player.position.x
        val groundedY = player.position.y

        // One jump edge-press: track the arc's peak (y shrinks upward), then it
        // must rise well above the resting height before falling back.
        input.press(Key.SPACE)
        var peakY = player.position.y
        // Full arc (apex ~60px, time of flight ~0.75s) plus margin to land.
        repeat(60) { tick(); peakY = minOf(peakY, player.position.y) }
        assertTrue(peakY < groundedY - 20f, "a jump should lift the player off the floor (peak=$peakY, floor=$groundedY)")
        assertTrue(player.position.y in (groundedY - 2f)..(groundedY + 2f), "player should land back on the floor")

        // Hold right for half a second: the player must travel to the right.
        input.held.add(Key.ARROW_RIGHT)
        repeat(30) { tick() }
        assertTrue(player.position.x > groundedX + 5f, "holding right should move the player right")
        input.held.remove(Key.ARROW_RIGHT)
    }
}
