package com.neoutils.engine.bundle

import com.neoutils.engine.scene.Timer
import com.neoutils.engine.scene.TimerMode
import com.neoutils.engine.serialization.NodeRegistry
import com.neoutils.engine.serialization.SceneLoader
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SceneLoaderTimerTest {

    @BeforeTest
    fun setUp() {
        NodeRegistry.clear()
        NodeRegistry.registerEngineTypes()
    }

    @AfterTest
    fun tearDown() {
        NodeRegistry.clear()
    }

    @Test
    fun `scene_json with engine_Timer loads with all properties`() {
        val json = """
            {
              "version": 2,
              "root": {
                "type": "com.neoutils.engine.scene.Node",
                "name": "Root",
                "properties": {},
                "children": [
                  {
                    "type": "engine.Timer",
                    "name": "MoveTimer",
                    "properties": {
                      "waitTime": 0.125,
                      "autostart": true,
                      "oneShot": false,
                      "processCallback": "PHYSICS"
                    },
                    "children": []
                  }
                ]
              }
            }
        """.trimIndent()
        val root = SceneLoader.load(json)
        val timer = root.children[0] as Timer
        assertEquals("MoveTimer", timer.name)
        assertEquals(0.125f, timer.waitTime)
        assertEquals(true, timer.autostart)
        assertEquals(false, timer.oneShot)
        assertEquals(TimerMode.PHYSICS, timer.processCallback)
    }

    @Test
    fun `round-trip preserves all four inspect fields`() {
        val original = com.neoutils.engine.scene.Node().apply { name = "Root" }
        original.addChild(Timer().apply {
            name = "MoveTimer"
            waitTime = 0.25f
            autostart = true
            oneShot = true
            processCallback = TimerMode.IDLE
        })
        val saved = SceneLoader.save(original)
        val reloaded = SceneLoader.load(saved)
        val t = reloaded.children[0] as Timer
        assertEquals("MoveTimer", t.name)
        assertEquals(0.25f, t.waitTime)
        assertEquals(true, t.autostart)
        assertEquals(true, t.oneShot)
        assertEquals(TimerMode.IDLE, t.processCallback)
    }

    @Test
    fun `runtime timeLeft does not appear in JSON`() {
        val root = com.neoutils.engine.scene.Node().apply { name = "Root" }
        val timer = Timer().apply { name = "MoveTimer"; waitTime = 0.5f }
        root.addChild(timer)
        timer.timeLeft = 0.3f
        val saved = SceneLoader.save(root)
        assertFalse(saved.contains("timeLeft"), "Saved JSON should not contain timeLeft: $saved")
    }

    @Test
    fun `lowercase processCallback fails with rich error message`() {
        val json = """
            {
              "version": 2,
              "root": {
                "type": "com.neoutils.engine.scene.Node",
                "name": "Root",
                "properties": {},
                "children": [
                  {
                    "type": "engine.Timer",
                    "name": "MyTimer",
                    "properties": {
                      "waitTime": 1.0,
                      "autostart": false,
                      "oneShot": false,
                      "processCallback": "physics"
                    },
                    "children": []
                  }
                ]
              }
            }
        """.trimIndent()
        val ex = assertFailsWith<IllegalStateException> { SceneLoader.load(json) }
        val msg = ex.message!!
        assertTrue(msg.contains("MyTimer"), "node name: $msg")
        assertTrue(msg.contains("processCallback"), "property name: $msg")
        assertTrue(msg.contains("physics"), "offending value: $msg")
        assertTrue(msg.contains("TimerMode"), "enum class: $msg")
        assertTrue(msg.contains("PHYSICS"), "valid entry PHYSICS: $msg")
        assertTrue(msg.contains("IDLE"), "valid entry IDLE: $msg")
    }
}
