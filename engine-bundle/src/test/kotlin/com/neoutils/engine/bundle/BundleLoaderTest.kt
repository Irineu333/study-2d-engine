package com.neoutils.engine.bundle

import com.neoutils.engine.bundle.script.*
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.serialization.NodeRegistry
import java.io.File
import kotlin.reflect.KClass
import kotlin.test.*

// ── Fake ScriptHost infrastructure ──────────────────────────────────────────

private class FakeScript(
    override val path: String,
    override val extendsType: KClass<out Node> = Node2D::class,
    override val exports: List<ExportedProperty> = listOf(
        ExportedProperty("value", Int::class, default = 0)
    ),
) : Script

private class FakeScriptInstance : ScriptInstance {
    val applied = mutableMapOf<String, Any?>()
    var updateCallCount = 0

    override val signals: Map<String, com.neoutils.engine.serialization.Signal<*>> = emptyMap()
    override fun setExport(name: String, value: Any?) { applied[name] = value }
    override fun currentValue(name: String): Any? = applied[name]
    override fun onEnter() {}
    override fun onProcess(dt: Float) { updateCallCount++ }
    override fun onPhysicsProcess(dt: Float) {}
    override fun onDraw(renderer: Renderer) {}
    override fun onExit() {}
    override fun onCollide(other: Node) {}
}

private class FakeScriptHost(
    override val extension: String = ".py",
    private val scriptFactory: (String) -> FakeScript = { FakeScript(it) },
) : ScriptHost {
    val loaded = mutableListOf<String>()
    val instances = mutableListOf<FakeScriptInstance>()

    override fun load(path: String, bundle: BundleSource): Script {
        loaded += path
        return scriptFactory(path)
    }

    override fun attach(node: Node, script: Script): ScriptInstance {
        val instance = FakeScriptInstance()
        instances += instance
        return instance
    }
}

// ── Test class ──────────────────────────────────────────────────────────────

class BundleLoaderTest {

    private lateinit var fakeHost: FakeScriptHost

    @BeforeTest
    fun setUp() {
        NodeRegistry.clear()
        fakeHost = FakeScriptHost()
    }

    @AfterTest
    fun tearDown() {
        NodeRegistry.clear()
    }

    @Test
    fun `fromResources returns a detached scene with the expected tree`() {
        val scene = BundleLoader.fromResources("test-bundle", scripting = fakeHost)
        assertFalse(scene.isLive)
        assertEquals("TestRoot", scene.name)
        assertEquals(2, scene.children.size)

        val foo = scene.children[0]
        assertEquals("fooScript", foo.name)
        assertTrue(foo is Node2D)
        // verify scriptInstance was attached: calling onProcess propagates to the instance
        foo.onProcess(0f)
        assertEquals(1, fakeHost.instances[0].updateCallCount)

        val collider = scene.children[1]
        assertEquals("engineCollider", collider.name)
    }

    @Test
    fun `fromPath returns equivalent scene from a temp directory`() {
        val temp = createTempDir("bundle-test")
        materializeTestBundle(temp)
        try {
            val fromDisk = BundleLoader.fromPath(temp, scripting = fakeHost)
            assertEquals("TestRoot", fromDisk.name)
            assertEquals(2, fromDisk.children.size)
            assertEquals("fooScript", fromDisk.children[0].name)
            assertTrue(fromDisk.children[0] is Node2D)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `classpath and disk bundles produce semantically equivalent scenes`() {
        val temp = createTempDir("bundle-equiv")
        materializeTestBundle(temp)
        try {
            val fromResources = BundleLoader.fromResources("test-bundle", scripting = fakeHost)
            NodeRegistry.clear()
            val fromPath = BundleLoader.fromPath(temp, scripting = FakeScriptHost())
            assertEquals(treeShape(fromResources), treeShape(fromPath))
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `orphan script in scripts directory is not loaded`() {
        BundleLoader.fromResources("test-bundle", scripting = fakeHost)
        assertFalse(
            fakeHost.loaded.any { it.contains("orphan", ignoreCase = true) },
            "Orphan script should not have been loaded; loaded: ${fakeHost.loaded}"
        )
        assertTrue(
            fakeHost.loaded.any { it.contains("dummy", ignoreCase = true) },
            "dummy.py should have been loaded"
        )
    }

    @Test
    fun `same script referenced multiple times loads once`() {
        val temp = createTempDir("bundle-dup")
        try {
            materializeTestBundle(temp)
            File(temp, "scene.json").writeText(
                """
                {
                  "version": 2,
                  "root": {
                    "type": "com.neoutils.engine.scene.Node",
                    "name": "DupRoot",
                    "properties": {},
                    "children": [
                      { "type": "com.neoutils.engine.scene.Node2D", "name": "first", "properties": { "value": 1 },
                        "script": "scripts/dummy.py", "children": [] },
                      { "type": "com.neoutils.engine.scene.Node2D", "name": "second", "properties": { "value": 2 },
                        "script": "scripts/dummy.py", "children": [] }
                    ]
                  }
                }
                """.trimIndent()
            )
            val scene = BundleLoader.fromPath(temp, scripting = fakeHost)
            assertEquals(2, scene.children.size)
            assertEquals("first", scene.children[0].name)
            assertEquals("second", scene.children[1].name)
            // same script path → loaded only once
            assertEquals(1, fakeHost.loaded.count { it == "scripts/dummy.py" })
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `missing scene json raises exception naming the bundle`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            BundleLoader.fromResources("does-not-exist-bundle")
        }
        assertTrue(ex.message!!.contains("does-not-exist-bundle"))
    }

    @Test
    fun `custom types parameter registers compiled Node classes`() {
        val temp = createTempDir("bundle-custom")
        try {
            File(temp, "scene.json").writeText(
                """
                {
                  "version": 2,
                  "root": {
                    "type": "com.neoutils.engine.scene.Node",
                    "name": "CustomRoot",
                    "properties": {},
                    "children": [
                      { "type": "${TestCustomNode::class.qualifiedName}", "name": "custom",
                        "properties": {}, "children": [] }
                    ]
                  }
                }
                """.trimIndent()
            )
            val scene = BundleLoader.fromPath(temp, types = listOf(TestCustomNode::class))
            assertEquals(1, scene.children.size)
            assertTrue(scene.children[0] is TestCustomNode)
            assertEquals("custom", scene.children[0].name)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `engine types resolve without manual registration`() {
        val temp = createTempDir("bundle-engine-only")
        try {
            File(temp, "scene.json").writeText(
                """
                {
                  "version": 2,
                  "root": {
                    "type": "com.neoutils.engine.scene.Node",
                    "name": "EngineRoot",
                    "properties": {},
                    "children": [
                      {
                        "type": "com.neoutils.engine.physics.BoxCollider",
                        "name": "wall",
                        "properties": {
                          "transform": { "position": {"x": 0.0, "y": 0.0}, "scale": {"x": 1.0, "y": 1.0}, "rotation": 0.0 },
                          "size": { "x": 10.0, "y": 10.0 }
                        },
                        "children": []
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
            val scene = BundleLoader.fromPath(temp)
            assertEquals(1, scene.children.size)
            assertTrue(scene.children[0] is com.neoutils.engine.physics.BoxCollider)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `node with script and export properties attaches and applies export`() {
        val temp = createTempDir("bundle-script-props")
        try {
            File(temp, "scene.json").writeText(
                """
                {
                  "version": 2,
                  "root": {
                    "type": "com.neoutils.engine.scene.Node",
                    "name": "Root",
                    "properties": {},
                    "children": [
                      {
                        "type": "com.neoutils.engine.scene.Node2D",
                        "name": "scripted",
                        "properties": { "value": 42 },
                        "script": "scripts/dummy.py",
                        "children": []
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
            File(temp, "scripts").mkdirs()
            File(temp, "scripts/dummy.py").writeText("# extends Node2D\nvalue: int = 0\n")

            val scene = BundleLoader.fromPath(temp, scripting = fakeHost)
            val node = scene.children[0]
            node.onProcess(0f)
            assertEquals(1, fakeHost.instances[0].updateCallCount, "scriptInstance must be attached")
            assertEquals(42, fakeHost.instances[0].applied["value"])
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `script-less bundle loads without scripting`() {
        val temp = createTempDir("bundle-scriptless")
        try {
            File(temp, "scene.json").writeText(
                """
                {
                  "version": 2,
                  "root": {
                    "type": "com.neoutils.engine.scene.Node",
                    "name": "Empty",
                    "properties": {},
                    "children": []
                  }
                }
                """.trimIndent()
            )
            val scene = BundleLoader.fromPath(temp)
            assertEquals("Empty", scene.name)
            assertEquals(0, scene.children.size)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `bundle with scripts and no host fails fast`() {
        val temp = createTempDir("bundle-noscripting")
        try {
            File(temp, "scene.json").writeText(
                """
                {
                  "version": 2,
                  "root": {
                    "type": "com.neoutils.engine.scene.Node",
                    "name": "Root",
                    "properties": {},
                    "children": [
                      {
                        "type": "com.neoutils.engine.scene.Node2D",
                        "name": "scripted",
                        "properties": {},
                        "script": "scripts/foo.py",
                        "children": []
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
            File(temp, "scripts").mkdirs()
            File(temp, "scripts/foo.py").writeText("# extends Node2D\n")
            val ex = assertFailsWith<IllegalStateException> { BundleLoader.fromPath(temp) }
            val msg = ex.message!!
            assertTrue(msg.contains("scripts/foo.py"), msg)
            assertTrue(msg.contains("ScriptHost"), msg)
            assertTrue(msg.contains("scripting"), msg)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `extension mismatch fails fast`() {
        val temp = createTempDir("bundle-extmismatch")
        try {
            File(temp, "scene.json").writeText(
                """
                {
                  "version": 2,
                  "root": {
                    "type": "com.neoutils.engine.scene.Node",
                    "name": "Root",
                    "properties": {},
                    "children": [
                      {
                        "type": "com.neoutils.engine.scene.Node2D",
                        "name": "bad",
                        "properties": {},
                        "script": "scripts/thing.lua",
                        "children": []
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
            File(temp, "scripts").mkdirs()
            File(temp, "scripts/thing.lua").writeText("-- lua script")
            val ex = assertFailsWith<IllegalStateException> {
                BundleLoader.fromPath(temp, scripting = fakeHost)
            }
            val msg = ex.message!!
            assertTrue(msg.contains("scripts/thing.lua"), msg)
            assertTrue(msg.contains(".py"), msg)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `properties key matching both inspect and export is fatal collision`() {
        // Replace the default fake host with one whose script exports "transform"
        // (collides with Node2D @Inspect var transform).
        val collidingHost = FakeScriptHost(scriptFactory = {
            FakeScript(
                path = it,
                exports = listOf(ExportedProperty("transform", Int::class, default = 0)),
            )
        })
        val temp = createTempDir("bundle-collision")
        try {
            File(temp, "scene.json").writeText(
                """
                {
                  "version": 2,
                  "root": {
                    "type": "com.neoutils.engine.scene.Node",
                    "name": "Root",
                    "properties": {},
                    "children": [
                      {
                        "type": "com.neoutils.engine.scene.Node2D",
                        "name": "collider",
                        "properties": { "transform": 1 },
                        "script": "scripts/dummy.py",
                        "children": []
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
            File(temp, "scripts").mkdirs()
            File(temp, "scripts/dummy.py").writeText("# extends Node2D\ntransform: int = 0\n")
            val ex = assertFailsWith<IllegalStateException> { BundleLoader.fromPath(temp, scripting = collidingHost) }
            val msg = ex.message!!
            assertTrue(msg.contains("transform"), msg)
            assertTrue(msg.contains("collider"), msg)
            assertTrue(msg.contains("scripts/dummy.py"), msg)
            assertTrue(msg.contains("Node2D"), msg)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `properties unknown key with attached script is fatal listing both candidate sources`() {
        val temp = createTempDir("bundle-unknown")
        try {
            File(temp, "scene.json").writeText(
                """
                {
                  "version": 2,
                  "root": {
                    "type": "com.neoutils.engine.scene.Node",
                    "name": "Root",
                    "properties": {},
                    "children": [
                      {
                        "type": "com.neoutils.engine.scene.Node2D",
                        "name": "scripted",
                        "properties": { "mystery": 1 },
                        "script": "scripts/dummy.py",
                        "children": []
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
            File(temp, "scripts").mkdirs()
            File(temp, "scripts/dummy.py").writeText("# extends Node2D\nvalue: int = 0\n")
            val ex = assertFailsWith<IllegalStateException> { BundleLoader.fromPath(temp, scripting = fakeHost) }
            val msg = ex.message!!
            assertTrue(msg.contains("mystery"), msg)
            assertTrue(msg.contains("scripted"), msg)
            assertTrue(msg.contains("transform"), msg) // Node2D @Inspect candidate
            assertTrue(msg.contains("value"), msg)     // export candidate
            assertTrue(msg.contains("scripts/dummy.py"), msg)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `properties valid mix of inspect and export keys routes to both targets`() {
        val temp = createTempDir("bundle-mixed")
        try {
            File(temp, "scene.json").writeText(
                """
                {
                  "version": 2,
                  "root": {
                    "type": "com.neoutils.engine.scene.Node",
                    "name": "Root",
                    "properties": {},
                    "children": [
                      {
                        "type": "com.neoutils.engine.scene.Node2D",
                        "name": "mixed",
                        "properties": {
                          "transform": { "position": {"x": 3.0, "y": 4.0}, "scale": {"x": 1.0, "y": 1.0}, "rotation": 0.0 },
                          "value": 99
                        },
                        "script": "scripts/dummy.py",
                        "children": []
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
            File(temp, "scripts").mkdirs()
            File(temp, "scripts/dummy.py").writeText("# extends Node2D\nvalue: int = 0\n")
            val scene = BundleLoader.fromPath(temp, scripting = fakeHost)
            val n = scene.children[0] as Node2D
            assertEquals(3f, n.transform.position.x)
            assertEquals(4f, n.transform.position.y)
            assertEquals(99, fakeHost.instances[0].applied["value"])
        } finally {
            temp.deleteRecursively()
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun materializeTestBundle(target: File) {
        target.mkdirs()
        val sceneJson = readResource("test-bundle/scene.json")
        File(target, "scene.json").writeText(sceneJson)
        val scriptsDir = File(target, "scripts").apply { mkdirs() }
        File(scriptsDir, "dummy.py").writeText(readResource("test-bundle/scripts/dummy.py"))
        File(scriptsDir, "orphan.py").writeText(readResource("test-bundle/scripts/orphan.py"))
    }

    private fun readResource(path: String): String =
        BundleLoaderTest::class.java.classLoader.getResource(path)?.readText()
            ?: error("Test resource not found: $path")

    private fun treeShape(node: Node): String = buildString {
        append(node::class.simpleName)
        append('(')
        append(node.name)
        append(')')
        if (node.children.isNotEmpty()) {
            append('[')
            for ((idx, child) in node.children.withIndex()) {
                if (idx > 0) append(',')
                append(treeShape(child))
            }
            append(']')
        }
    }

    private fun createTempDir(prefix: String): File {
        val dir = File.createTempFile(prefix, "")
        dir.delete()
        dir.mkdirs()
        return dir
    }
}

class TestCustomNode : Node()
