package com.neoutils.engine.games.snake

import com.neoutils.engine.bundle.BundleLoader
import com.neoutils.engine.bundle.python.PythonScriptHost
import com.neoutils.engine.scene.Camera2D
import com.neoutils.engine.scene.ColorRect
import com.neoutils.engine.scene.Label
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.scene.Timer
import com.neoutils.engine.scene.TimerMode
import com.neoutils.engine.serialization.NodeRegistry
import com.neoutils.engine.tree.SceneTree
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SnakeBundleTest {

    @Before
    fun setUp() {
        NodeRegistry.clear()
    }

    @After
    fun tearDown() {
        NodeRegistry.clear()
    }

    @Test
    fun `bundle loads with expected tree shape`() {
        val python = PythonScriptHost.create()
        val root = BundleLoader.fromResources("snake", scripting = python)

        val camera = root.findChild("Camera2D") as? Camera2D
        assertNotNull(camera, "Camera2D missing")
        assertTrue(camera.current, "Camera2D.current must be true")
        assertEquals(400f, camera.bounds.size.x)
        assertEquals(400f, camera.bounds.size.y)

        val snake = root.findChild("Snake") as? Node2D
        assertNotNull(snake, "Snake node missing")
        val timer = snake.findChild("MoveTimer") as? Timer
        assertNotNull(timer, "MoveTimer missing")
        assertEquals(0.125f, timer.waitTime)
        assertTrue(timer.autostart)
        assertEquals(false, timer.oneShot)
        assertEquals(TimerMode.PHYSICS, timer.processCallback)

        assertNotNull(root.findChild("Food") as? ColorRect, "Food missing")
        assertNotNull(root.findChild("ScoreLabel") as? Label, "ScoreLabel missing")
        assertNotNull(root.findChild("GameOverLabel") as? Label, "GameOverLabel missing")
    }

    @Test
    fun `snake advances five cells to the right after five ticks`() {
        val python = PythonScriptHost.create()
        val root = BundleLoader.fromResources("snake", scripting = python)
        val tree = SceneTree(root = root)
        tree.start()

        val snake = root.findChild("Snake") as Node2D
        val timer = snake.findChild("MoveTimer") as Timer

        // Drive 5 timer ticks: waitTime=0.125, physicsHz=60 → 7.5 frames per tick.
        // Use a larger dt and drain pending mutations after each phase.
        repeat(5) {
            tree.physicsProcess(0.125f)
            tree.applyPending()
        }

        // Expected: head moved 5 cells right of startCell (10,10) → (15,10),
        // i.e. pixel position (300, 200). Snake length is 3 or 4 (4 if the
        // randomly-placed food happened to sit on the rightward path).
        val segments = snake.children.filterIsInstance<ColorRect>()
        assertTrue(segments.size in 3..4, "unexpected segment count: ${segments.size}")
        val expectedHeadX = 300f
        val expectedHeadY = 200f
        val headHit = segments.any {
            it.position.x == expectedHeadX && it.position.y == expectedHeadY
        }
        assertTrue(headHit, "no segment at expected head (300,200); got ${segments.map { it.position }}")

        timer.stop() // quiet any future ticks during teardown
    }
}
