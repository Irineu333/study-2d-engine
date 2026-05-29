package com.neoutils.engine.bundle.python

import com.neoutils.engine.bundle.script.BundleSource
import com.neoutils.engine.debug.DrawCommand
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.serialization.NodeRegistry
import com.neoutils.engine.tree.SceneTree
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PythonImmediateDrawTest {

    private lateinit var host: PythonScriptHost

    @Before
    fun setUp() {
        NodeRegistry.clear()
        NodeRegistry.registerEngineTypes()
        host = PythonScriptHost.create()
    }

    @After
    fun tearDown() {
        NodeRegistry.clear()
    }

    @Test
    fun `python script enqueues a world line via tree debug draw`() {
        val source = """
# extends Node2D


def _physics_process(self, dt):
    self.tree.debug.draw.world.line(Vec2(0.0, 0.0), Vec2(10.0, 10.0), Color(1.0, 0.0, 0.0, 1.0))
""".trimIndent()
        val bundle = object : BundleSource {
            override fun read(path: String) = source
            override fun exists(path: String) = path == "gizmo.py"
        }
        val node = Node2D()
        val script = host.load("gizmo.py", bundle)
        val instance = host.attach(node, script)

        val tree = SceneTree(node).also { it.start() }
        tree.debug.draw.enabled = true

        instance.onPhysicsProcess(0.016f)

        val commands = tree.debug.draw.world.commands
        assertEquals(1, commands.size)
        assertTrue(commands.single() is DrawCommand.Line)
    }
}
