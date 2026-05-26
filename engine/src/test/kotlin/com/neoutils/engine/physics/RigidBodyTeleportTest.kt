package com.neoutils.engine.physics

import com.neoutils.engine.dx.Log
import com.neoutils.engine.dx.LogLevel
import com.neoutils.engine.dx.LogSink
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RigidBodyTeleportTest {

    private val warnings = mutableListOf<String>()
    private lateinit var oldSink: LogSink

    @BeforeTest
    fun setUp() {
        warnings.clear()
        oldSink = Log.sink
        Log.sink = LogSink { _, level, tag, msg ->
            if (level == LogLevel.Warn && tag == "RigidBody2D") warnings += msg
        }
    }

    @AfterTest
    fun tearDown() {
        Log.sink = oldSink
    }

    @Test
    fun `first teleport on live body logs warning, second is silent`() {
        val root = Node()
        val body = RigidBody2D().apply { name = "Player" }
        root.addChild(body)
        SceneTree(root).start()

        body.position = Vec2(100f, 0f)
        body.position = Vec2(200f, 0f)

        assertEquals(1, warnings.size, "expected exactly one warning, got $warnings")
        assertTrue(warnings[0].contains("Player"), "warning should name the body, got ${warnings[0]}")
    }

    @Test
    fun `teleport on non-attached body does not warn`() {
        val body = RigidBody2D().apply { name = "Floating" }
        body.position = Vec2(50f, 0f)
        body.position = Vec2(100f, 0f)
        assertEquals(0, warnings.size, "no warning expected, got $warnings")
    }
}
