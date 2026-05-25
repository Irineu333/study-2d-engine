package com.neoutils.engine.serialization

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Circle2D
import com.neoutils.engine.scene.ColorRect
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.scene.ScriptInstanceContract
import com.neoutils.engine.tree.SceneTree
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SceneLoaderTest {

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
    fun `save produces version and root`() {
        val root = Node().apply {
            name = "root"
            addChild(ColorRect().apply {
                name = "rectangle"
                size = Vec2(20f, 30f)
                color = Color.RED
            })
        }
        val text = SceneLoader.save(root)
        val obj: JsonObject = Json.parseToJsonElement(text).jsonObject
        assertEquals(2, obj["version"]!!.jsonPrimitive.content.toInt())
        val rootJson = obj["root"]!!.jsonObject
        assertEquals("com.neoutils.engine.scene.Node", rootJson["type"]!!.jsonPrimitive.content)
        assertEquals("root", rootJson["name"]!!.jsonPrimitive.content)
        assertTrue(rootJson["children"] != null)
        assertTrue(rootJson["properties"] != null)
        // No props field anywhere
        assertTrue("props" !in rootJson)
    }

    @Test
    fun `load preserves order and properties`() {
        val original = Node().apply {
            addChild(ColorRect().apply {
                name = "first"
                size = Vec2(10f, 10f)
                color = Color.GREEN
            })
            addChild(Circle2D().apply {
                name = "second"
                radius = 10f
                color = Color.BLUE
            })
        }
        val loaded = SceneLoader.load(SceneLoader.save(original))
        assertEquals(2, loaded.children.size)
        val first = loaded.children[0] as ColorRect
        val second = loaded.children[1] as Circle2D
        assertEquals("first", first.name)
        assertEquals(Vec2(10f, 10f), first.size)
        assertEquals(Color.GREEN, first.color)
        assertEquals("second", second.name)
        assertEquals(10f, second.radius)
    }

    @Test
    fun `load returns Node type, not a Scene subtype`() {
        val original = Node().apply {
            name = "root"
            addChild(ColorRect().apply { name = "child" })
        }
        val loaded: Node = SceneLoader.load(SceneLoader.save(original))
        assertEquals("root", loaded.name)
        assertEquals(Node::class, loaded::class)
    }

    @Test
    fun `load accepts Node2D as root`() {
        val jsonText = """
            {
              "version": 2,
              "root": {
                "type": "com.neoutils.engine.scene.Node2D",
                "name": "root2D",
                "properties": {},
                "children": []
              }
            }
        """.trimIndent()
        val loaded = SceneLoader.load(jsonText)
        assertTrue(loaded is Node2D)
        assertEquals("root2D", loaded.name)
    }

    @Test
    fun `round-trip is stable`() {
        val original = Node().apply {
            addChild(Circle2D().apply {
                radius = 25f
                color = Color.WHITE
            })
        }
        val first = SceneLoader.save(original)
        val second = SceneLoader.save(SceneLoader.load(first))
        assertEquals(first, second)
    }

    @Test
    fun `load returns detached root without firing onEnter`() {
        NodeRegistry.register(CounterNode::class) { CounterNode() }
        val original = Node().apply {
            name = "root"
            addChild(CounterNode().apply { name = "counter" })
        }
        val text = SceneLoader.save(original)
        CounterNode.totalEnters = 0
        val loaded = SceneLoader.load(text)
        assertFalse(loaded.isLive)
        assertEquals(0, CounterNode.totalEnters)
        SceneTree(loaded).start()
        assertEquals(1, CounterNode.totalEnters)
    }

    @Test
    fun `script-path identifier resolves like any other identifier`() {
        NodeRegistry.register("scripts/counter.nengine.kts", CounterNode::class) { CounterNode() }
        val jsonText = """
            {
              "version": 2,
              "root": {
                "type": "com.neoutils.engine.scene.Node",
                "name": "root",
                "properties": {},
                "children": [
                  {
                    "type": "scripts/counter.nengine.kts",
                    "name": "my_script",
                    "properties": {},
                    "children": []
                  }
                ]
              }
            }
        """.trimIndent()

        val loaded = SceneLoader.load(jsonText)
        assertEquals(1, loaded.children.size)
        assertTrue(loaded.children[0] is CounterNode)
        assertEquals("my_script", loaded.children[0].name)
    }

    @Test
    fun `unknown type fails fast regardless of suffix`() {
        val jsonText = """
            {
              "version": 2,
              "root": {
                "type": "com.neoutils.engine.scene.Node",
                "name": "root",
                "properties": {},
                "children": [
                  {
                    "type": "scripts/missing.nengine.kts",
                    "name": "x",
                    "properties": {},
                    "children": []
                  }
                ]
              }
            }
        """.trimIndent()
        val ex = assertFailsWith<UnknownNodeTypeException> {
            SceneLoader.load(jsonText)
        }
        assertEquals("scripts/missing.nengine.kts", ex.typeName)
    }

    @Test
    fun `save uses identifierFor when class was registered under a custom identifier`() {
        NodeRegistry.register("scripts/counter.nengine.kts", CounterNode::class) { CounterNode() }
        val root = Node().apply {
            name = "root"
            addChild(CounterNode().apply { name = "my_script" })
        }
        val text = SceneLoader.save(root)
        val obj = Json.parseToJsonElement(text).jsonObject
        val child = (obj["root"]!!.jsonObject["children"] as kotlinx.serialization.json.JsonArray)[0].jsonObject
        assertEquals("scripts/counter.nengine.kts", child["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `load rejects version 1 with explicit message`() {
        val jsonText = """
            {
              "version": 1,
              "root": {
                "type": "com.neoutils.engine.scene.Node",
                "name": "root",
                "properties": {},
                "children": []
              }
            }
        """.trimIndent()
        val ex = assertFailsWith<IllegalStateException> {
            SceneLoader.load(jsonText)
        }
        val msg = ex.message!!
        assertTrue(msg.contains("version 1"), msg)
        assertTrue(msg.contains("version 2"), msg)
        assertTrue(msg.contains("godot-style-properties"), msg)
    }

    @Test
    fun `unknown property in properties without script fails fast`() {
        val jsonText = """
            {
              "version": 2,
              "root": {
                "type": "com.neoutils.engine.scene.Node",
                "name": "root",
                "properties": {},
                "children": [
                  {
                    "type": "com.neoutils.engine.scene.ColorRect",
                    "name": "rect",
                    "properties": { "ballSizr": 16.0 },
                    "children": []
                  }
                ]
              }
            }
        """.trimIndent()
        val ex = assertFailsWith<IllegalStateException> {
            SceneLoader.load(jsonText)
        }
        val msg = ex.message!!
        assertTrue(msg.contains("ballSizr"), msg)
        assertTrue(msg.contains("rect"), msg)
        assertTrue(msg.contains("No script attached"), msg)
    }

    @Test
    fun `entry with script but null attachScript fails fast`() {
        val jsonText = """
            {
              "version": 2,
              "root": {
                "type": "com.neoutils.engine.scene.Node",
                "name": "root",
                "properties": {},
                "children": [
                  {
                    "type": "com.neoutils.engine.scene.ColorRect",
                    "name": "rect",
                    "script": "scripts/foo.py",
                    "properties": {},
                    "children": []
                  }
                ]
              }
            }
        """.trimIndent()
        val ex = assertFailsWith<IllegalStateException> {
            SceneLoader.load(jsonText, attachScript = null)
        }
        val msg = ex.message!!
        assertTrue(msg.contains("scripts/foo.py"), msg)
        assertTrue(msg.contains("no attachScript host"), msg)
    }

    @Test
    fun `round-trip is stable with attached script and currentValue`() {
        // Tree with one node carrying a fake script that exposes a single export
        val jsonText = """
            {
              "version": 2,
              "root": {
                "type": "com.neoutils.engine.scene.Node",
                "name": "root",
                "properties": {},
                "children": [
                  {
                    "type": "com.neoutils.engine.scene.ColorRect",
                    "name": "rect",
                    "script": "fake.py",
                    "properties": { "speed": 480.0 },
                    "children": []
                  }
                ]
              }
            }
        """.trimIndent()

        val instances = mutableMapOf<Node, FakeScriptInstance>()
        val attach: (Node, String) -> ScriptAttachment = { node, _ ->
            val inst = FakeScriptInstance(mutableMapOf("speed" to JsonPrimitive(360.0)))
            instances[node] = inst
            ScriptAttachment(
                instance = inst,
                exportNames = setOf("speed"),
                applyExport = { name, el -> inst.exports[name] = el },
            )
        }
        val serialize: (Node) -> Map<String, JsonElement>? = { node ->
            instances[node]?.exports?.toMap()
        }

        val loaded = SceneLoader.load(jsonText, attach)
        val first = SceneLoader.save(loaded, serialize)
        val loaded2 = SceneLoader.load(first, attach)
        val second = SceneLoader.save(loaded2, serialize)
        assertEquals(first, second)
        // sanity: emitted JSON carries the export inside `properties`
        val obj = Json.parseToJsonElement(first).jsonObject
        val child = (obj["root"]!!.jsonObject["children"] as kotlinx.serialization.json.JsonArray)[0].jsonObject
        val props = child["properties"]!!.jsonObject
        assertEquals(480.0, props["speed"]!!.jsonPrimitive.content.toDouble())
    }
}

class CounterNode : com.neoutils.engine.scene.Node() {
    override fun onEnter() { totalEnters++ }
    companion object {
        @Volatile var totalEnters: Int = 0
    }
}

private class FakeScriptInstance(
    val exports: MutableMap<String, JsonElement>,
) : ScriptInstanceContract {
    override val signals: Map<String, Signal<*>> = emptyMap()
    override fun onEnter() {}
    override fun onProcess(dt: Float) {}
    override fun onPhysicsProcess(dt: Float) {}
    override fun onDraw(renderer: Renderer) {}
    override fun onExit() {}
}
