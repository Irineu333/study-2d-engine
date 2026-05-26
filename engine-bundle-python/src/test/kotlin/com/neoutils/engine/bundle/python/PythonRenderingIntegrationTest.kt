package com.neoutils.engine.bundle.python

import com.neoutils.engine.bundle.script.BundleSource
import com.neoutils.engine.bundle.script.PropCoercion
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.serialization.NodeRegistry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class RecordingRenderer : Renderer {
    val rects = mutableListOf<Triple<Rect, Color, Boolean>>()
    val texts = mutableListOf<Quadruple<String, Vec2, Float, Color>>()
    override fun clear(color: Color) {}
    override fun drawRect(rect: Rect, color: Color, filled: Boolean) {
        rects += Triple(rect, color, filled)
    }
    override fun drawCircle(center: Vec2, radius: Float, color: Color, filled: Boolean, thickness: Float) {}
    override fun drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color) {}
    override fun drawText(text: String, position: Vec2, size: Float, color: Color) {
        texts += Quadruple(text, position, size, color)
    }
    override fun measureText(text: String, size: Float): Vec2 = Vec2(0f, 0f)
    override fun drawPolygon(points: List<Vec2>, color: Color) {}
    override fun pushTransform(translation: Vec2, rotation: Float, scale: Vec2) {}
    override fun popTransform() {}
}

private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

class PythonRenderingIntegrationTest {

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
    fun `center_line draws dashed rects covering the full height`() {
        val source = """
# extends Node2D

x: float = 400.0
height: float = 600.0


def _draw(self, renderer):
    dash_height = 12.0
    gap = 8.0
    dash_color = Color(1.0, 1.0, 1.0, 0.3)
    y = 0.0
    while y < self.height:
        renderer.drawRect(
            Rect(Vec2(self.x - 1.0, y), Vec2(2.0, dash_height)),
            dash_color,
            True,
        )
        y += dash_height + gap
""".trimIndent()
        val bundle = object : BundleSource {
            override fun read(path: String) = source
            override fun exists(path: String) = path == "center_line.py"
        }
        val script = host.load("center_line.py", bundle)
        val instance = host.attach(Node2D(), script)
        val props = buildJsonObject {
            put("x", JsonPrimitive(400.0))
            put("height", JsonPrimitive(600.0))
        }
        for ((name, jsonEl) in props) {
            val export = script.exports.first { it.name == name }
            instance.setExport(name, PropCoercion.coerce(jsonEl, export.type, export.nullable))
        }
        val renderer = RecordingRenderer()
        instance.onDraw(renderer)
        assertEquals(30, renderer.rects.size)
        val (firstRect, firstColor, firstFilled) = renderer.rects.first()
        assertEquals(399f, firstRect.origin.x)
        assertEquals(0f, firstRect.origin.y)
        assertEquals(2f, firstRect.size.x)
        assertEquals(12f, firstRect.size.y)
        assertEquals(true, firstFilled)
        assertEquals(0.3f, firstColor.a)
    }

    @Test
    fun `score draws the value as text via on_render after on_enter`() {
        val source = """
# extends Node2D

textSize: float = 48.0
color: Color = Color(1.0, 1.0, 1.0, 1.0)


def _ready(self):
    self._value = 0


def _draw(self, renderer):
    renderer.drawText(str(self._value), self.world().position, self.textSize, self.color)


def increment(self):
    self._value = self._value + 1
""".trimIndent()
        val bundle = object : BundleSource {
            override fun read(path: String) = source
            override fun exists(path: String) = path == "score.py"
        }
        val script = host.load("score.py", bundle)
        val instance = host.attach(Node2D(), script)
        val props: JsonObject = buildJsonObject {
            put("textSize", JsonPrimitive(48.0))
            put("color", buildJsonObject {
                put("r", JsonPrimitive(1.0))
                put("g", JsonPrimitive(1.0))
                put("b", JsonPrimitive(1.0))
                put("a", JsonPrimitive(1.0))
            })
        }
        for ((name, jsonEl) in props) {
            val export = script.exports.first { it.name == name }
            instance.setExport(name, PropCoercion.coerce(jsonEl, export.type, export.nullable))
        }
        instance.onEnter()
        val renderer = RecordingRenderer()
        instance.onDraw(renderer)
        assertEquals(1, renderer.texts.size)
        val drawn = renderer.texts.single()
        assertEquals("0", drawn.a)
        assertEquals(48f, drawn.c)
        assertEquals(1f, drawn.d.r)
        assertEquals(1f, drawn.d.a)
    }
}
