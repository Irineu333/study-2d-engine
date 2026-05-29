package com.neoutils.engine.bundle.lua

import com.neoutils.engine.bundle.script.BundleSource
import com.neoutils.engine.debug.DrawCommand
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.serialization.NodeRegistry
import com.neoutils.engine.tree.SceneTree
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LuaImmediateDrawTest {

    @Before
    fun setUp() {
        NodeRegistry.registerEngineTypes()
    }

    @Test
    fun `lua script enqueues a screen circle via tree debug draw`() {
        val host = LuaScriptHost.create()
        val script = host.load(
            "scripts/gizmo.lua",
            FakeBundle(
                "scripts/gizmo.lua" to """
                    return {
                        extends = "Node2D",
                        _process = function(self, dt)
                            self.tree.debug.draw.screen:circle(nengine.Vec2(5, 5), 4, nengine.Color(0, 1, 0, 1))
                        end,
                    }
                """.trimIndent(),
            ),
        )
        val node = Node2D()
        val instance = host.attach(node, script)

        val tree = SceneTree(node).also { it.start() }
        tree.debug.draw.enabled = true

        instance.onProcess(0.016f)

        val commands = tree.debug.draw.screen.commands
        assertEquals(1, commands.size)
        assertTrue(commands.single() is DrawCommand.Circle)
    }

    private class FakeBundle(vararg files: Pair<String, String>) : BundleSource {
        private val map = files.toMap()
        override fun read(path: String): String = map.getValue(path)
        override fun exists(path: String): Boolean = map.containsKey(path)
    }
}
