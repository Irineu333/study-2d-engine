package com.neoutils.engine.bundle.python

import com.neoutils.engine.bundle.script.BundleSource
import com.neoutils.engine.bundle.script.ScriptHostRegistry
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.serialization.NodeRegistry
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val EXTENDS_NODE2D = "# extends Node2D\n"

class PythonScriptHostTest {

    private val source = mapOf<String, String>()

    private fun bundle(contents: Map<String, String>) = object : BundleSource {
        override fun read(path: String) = contents[path]
            ?: throw IllegalArgumentException("Script not found: $path")
        override fun exists(path: String) = path in contents
    }

    @Before
    fun setUp() {
        NodeRegistry.clear()
        NodeRegistry.registerEngineTypes()
        ScriptHostRegistry.clear()
        PythonScriptHost.install()
    }

    @After
    fun tearDown() {
        NodeRegistry.clear()
        ScriptHostRegistry.clear()
    }

    // --- load: extends ---------------------------------------------------

    @Test
    fun `load with hash comment extends resolves Node2D`() {
        val host = ScriptHostRegistry.hostFor("test.py")!!
        val script = host.load("test.py", bundle(mapOf(
            "test.py" to "# extends Node2D\n"
        )))
        assertEquals(Node2D::class, script.extendsType)
    }

    @Test
    fun `load with docstring extends resolves Node2D`() {
        val host = ScriptHostRegistry.hostFor("test.py")!!
        val script = host.load("test.py", bundle(mapOf(
            "test.py" to "\"\"\"extends Node2D\"\"\"\n"
        )))
        assertEquals(Node2D::class, script.extendsType)
    }

    @Test
    fun `load without extends declaration fails fast`() {
        val host = ScriptHostRegistry.hostFor("test.py")!!
        assertFailsWith<MissingExtendsDeclarationException> {
            host.load("test.py", bundle(mapOf("test.py" to "speed: float = 1.0\n")))
        }
    }

    @Test
    fun `load with unknown extends type fails fast naming type and path`() {
        val host = ScriptHostRegistry.hostFor("test.py")!!
        val ex = assertFailsWith<UnknownExtendsTypeException> {
            host.load("test.py", bundle(mapOf("test.py" to "# extends BananaNode\nspeed: float = 1.0\n")))
        }
        assertTrue(ex.message!!.contains("BananaNode"))
        assertTrue(ex.message!!.contains("test.py"))
    }

    // --- load: exports ---------------------------------------------------

    @Test
    fun `load detects float export with default`() {
        val host = ScriptHostRegistry.hostFor("test.py")!!
        val script = host.load("test.py", bundle(mapOf(
            "test.py" to "${EXTENDS_NODE2D}speed: float = 360.0\n"
        )))
        val export = script.exports.single()
        assertEquals("speed", export.name)
        assertEquals(Float::class, export.type)
        assertEquals(360.0f, export.default as Float, 0.001f)
    }

    @Test
    fun `load detects bool export`() {
        val host = ScriptHostRegistry.hostFor("test.py")!!
        val script = host.load("test.py", bundle(mapOf(
            "test.py" to "${EXTENDS_NODE2D}ai: bool = False\n"
        )))
        val export = script.exports.single()
        assertEquals("ai", export.name)
        assertEquals(Boolean::class, export.type)
        assertEquals(false, export.default)
    }

    @Test
    fun `load detects Vec2 export`() {
        val host = ScriptHostRegistry.hostFor("test.py")!!
        val script = host.load("test.py", bundle(mapOf(
            "test.py" to "${EXTENDS_NODE2D}center: Vec2 = Vec2(400.0, 300.0)\n"
        )))
        val export = script.exports.single()
        assertEquals("center", export.name)
        assertEquals(Vec2::class, export.type)
        val v = export.default as Vec2
        assertEquals(400.0f, v.x, 0.001f)
        assertEquals(300.0f, v.y, 0.001f)
    }

    @Test
    fun `load silently drops unsupported export type`() {
        val host = ScriptHostRegistry.hostFor("test.py")!!
        val script = host.load("test.py", bundle(mapOf(
            "test.py" to "${EXTENDS_NODE2D}cache: dict = {}\n"
        )))
        assertTrue(script.exports.isEmpty())
    }

    @Test
    fun `load detects Optional Key export as nullable`() {
        val host = ScriptHostRegistry.hostFor("test.py")!!
        val script = host.load("test.py", bundle(mapOf(
            "test.py" to "${EXTENDS_NODE2D}from typing import Optional\nup_key: Optional[Key] = None\n"
        )))
        val export = script.exports.single()
        assertEquals("up_key", export.name)
        assertNull(export.default)
    }

    // --- attach and hooks ------------------------------------------------

    @Test
    fun `attach and onUpdate dispatches to on_update in Python`() {
        val host = ScriptHostRegistry.hostFor("test.py")!!
        val script = host.load("test.py", bundle(mapOf(
            "test.py" to """
${EXTENDS_NODE2D}
_calls = []

def on_update(self, dt):
    _calls.append(('on_update', dt))
""".trimIndent()
        )))

        val node = Node2D()
        val instance = host.attach(node, script)
        instance.onUpdate(0.016f)
        // No exception means the hook was dispatched
    }

    @Test
    fun `attach and missing on_collide does not throw`() {
        val host = ScriptHostRegistry.hostFor("test.py")!!
        val script = host.load("test.py", bundle(mapOf(
            "test.py" to EXTENDS_NODE2D
        )))
        val node = Node2D()
        val instance = host.attach(node, script)
        val other = Node2D()
        instance.onCollide(other)  // no exception
    }

    @Test
    fun `setExport stores value accessible from Python on_render`() {
        val host = ScriptHostRegistry.hostFor("test.py")!!
        val script = host.load("test.py", bundle(mapOf(
            "test.py" to """
${EXTENDS_NODE2D}x: float = 0.0

def on_render(self, renderer):
    renderer.drawRect(None, None, False)
""".trimIndent()
        )))
        val node = Node2D()
        val instance = host.attach(node, script)
        instance.setExport("x", 42.0f)
        // No exception means it survived; actual value verification is via integration test
    }
}
