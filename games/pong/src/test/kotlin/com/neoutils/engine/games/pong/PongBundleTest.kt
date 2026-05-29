package com.neoutils.engine.games.pong

import com.neoutils.engine.bundle.BundleLoader
import com.neoutils.engine.bundle.python.PythonScriptHost
import com.neoutils.engine.debug.DrawCommand
import com.neoutils.engine.physics.CharacterBody2D
import com.neoutils.engine.scene.Camera2D
import com.neoutils.engine.serialization.NodeRegistry
import com.neoutils.engine.tree.SceneTree
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PongBundleTest {

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
        val root = BundleLoader.fromResources("pong", scripting = python)

        val camera = root.findChild("MainCamera") as? Camera2D
        assertNotNull(camera, "MainCamera missing")
        assertTrue(camera.current, "MainCamera.current must be true")
        assertEquals(800f, camera.bounds.size.x)
        assertEquals(600f, camera.bounds.size.y)

        assertNotNull(root.findChild("Ball") as? CharacterBody2D, "Ball missing")
        assertNotNull(root.findChild("left"), "left paddle missing")
        assertNotNull(root.findChild("right"), "right paddle missing")
    }

    @Test
    fun `debug gizmos are off by default and the ball velocity vector shows once enabled`() {
        val python = PythonScriptHost.create()
        val root = BundleLoader.fromResources("pong", scripting = python)
        val tree = SceneTree(root = root)
        tree.start()

        // Disabled by default — `_process` calls the draw verbs but they no-op.
        assertEquals(false, tree.debug.draw.enabled, "immediate-draw must be off by default")
        tree.process(0.016f)
        assertEquals(0, tree.debug.draw.world.commands.size, "no gizmos while disabled")

        // Enabling (as the F1 HUD "Debug Draw" row does) makes the ball + AI
        // paddle scripts enqueue their world gizmos (cleared on render — so
        // assert before rendering).
        tree.debug.draw.enabled = true
        tree.process(0.016f)

        val world = tree.debug.draw.world.commands
        assertTrue(world.isNotEmpty(), "expected gizmos while enabled")
        // Exactly one circle: the ball's velocity-vector origin marker.
        assertEquals(1, world.count { it is DrawCommand.Circle }, "expected one ball marker")
        // Velocity vector (ball) + AI target/center/band lines (right paddle).
        assertTrue(world.count { it is DrawCommand.Line } >= 1, "expected at least the velocity line")
    }
}
