package com.neoutils.engine.debug

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.scene.Camera2D
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Enqueues an immediate-draw command during `physicsProcess`. */
private class WorldLineEmitter : Node() {
    override fun onPhysicsProcess(dt: Float) {
        super.onPhysicsProcess(dt)
        tree?.debug?.draw?.world?.line(Vec2(0f, 0f), Vec2(10f, 10f), Color.RED)
    }
}

private class ScreenTextEmitter : Node() {
    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        tree?.debug?.draw?.screen?.text(Vec2(5f, 5f), "hi", Color.WHITE)
    }
}

class ImmediateDrawTest {

    @Test
    fun `each verb enqueues one command and world screen are distinct buffers`() {
        val tree = SceneTree(Node()).also { it.start() }
        tree.debug.draw.enabled = true

        tree.debug.draw.world.line(Vec2.ZERO, Vec2.ONE, Color.RED)
        tree.debug.draw.world.circle(Vec2.ZERO, 4f, Color.GREEN)
        tree.debug.draw.world.rect(Rect(Vec2.ZERO, Vec2.ONE), Color.BLUE)
        tree.debug.draw.screen.text(Vec2.ZERO, "hi", Color.WHITE)

        assertEquals(3, tree.debug.draw.world.commands.size)
        assertEquals(1, tree.debug.draw.screen.commands.size)
        // Enqueued in call order, distinct command types.
        assertTrue(tree.debug.draw.world.commands[0] is DrawCommand.Line)
        assertTrue(tree.debug.draw.world.commands[1] is DrawCommand.Circle)
        assertTrue(tree.debug.draw.world.commands[2] is DrawCommand.Rect)
        assertTrue(tree.debug.draw.screen.commands[0] is DrawCommand.Text)
    }

    @Test
    fun `command enqueued in physics is drawn once in the world pass under the view transform`() {
        val camera = Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2(100f, 100f))
            current = true
        }
        val emitter = WorldLineEmitter()
        val root = Node().apply {
            addChild(camera)
            addChild(emitter)
        }
        val tree = SceneTree(root).also {
            it.resize(200f, 200f)
            it.start()
        }
        tree.debug.draw.enabled = true

        tree.physicsProcess(0.016f)
        val recorder = RecordingRenderer()
        tree.render(recorder)

        val lines = recorder.events.filterIsInstance<RecordedEvent.Line>()
        assertEquals(1, lines.size)
        // The camera pushes a view transform before the world pass draws the
        // line — so a Push precedes the Line in the event stream.
        val lineIndex = recorder.events.indexOfFirst { it is RecordedEvent.Line }
        val pushIndex = recorder.events.indexOfFirst { it is RecordedEvent.Push }
        assertTrue(pushIndex in 0 until lineIndex, "world line must render under the camera Push")
    }

    @Test
    fun `screen command renders in the UI pass`() {
        val emitter = ScreenTextEmitter()
        val tree = SceneTree(Node().apply { addChild(emitter) }).also {
            it.resize(200f, 200f)
            it.start()
        }
        tree.debug.draw.enabled = true

        tree.process(0.016f)
        val recorder = RecordingRenderer()
        tree.render(recorder)

        assertEquals(1, recorder.events.filterIsInstance<RecordedEvent.Text>().size)
    }

    @Test
    fun `buffers are empty after render with no accumulation across frames`() {
        val tree = SceneTree(Node()).also { it.start() }
        tree.debug.draw.enabled = true
        tree.debug.draw.world.line(Vec2.ZERO, Vec2.ONE, Color.RED)
        tree.debug.draw.screen.text(Vec2.ZERO, "hi", Color.WHITE)

        val first = RecordingRenderer()
        tree.render(first)
        assertEquals(0, tree.debug.draw.world.commands.size)
        assertEquals(0, tree.debug.draw.screen.commands.size)
        assertEquals(1, first.events.filterIsInstance<RecordedEvent.Line>().size)

        // Second frame: nothing enqueued → zero draws.
        val second = RecordingRenderer()
        tree.render(second)
        assertEquals(0, second.events.filterIsInstance<RecordedEvent.Line>().size)
        assertEquals(0, second.events.filterIsInstance<RecordedEvent.Text>().size)
    }

    @Test
    fun `disabled verbs enqueue nothing and emit zero draws`() {
        val tree = SceneTree(Node()).also { it.start() }
        tree.debug.draw.enabled = false

        tree.debug.draw.world.line(Vec2.ZERO, Vec2.ONE, Color.RED)
        tree.debug.draw.screen.circle(Vec2.ZERO, 3f, Color.GREEN)

        assertEquals(0, tree.debug.draw.world.commands.size)
        assertEquals(0, tree.debug.draw.screen.commands.size)

        val recorder = RecordingRenderer()
        tree.render(recorder)
        assertEquals(0, recorder.events.filterIsInstance<RecordedEvent.Line>().size)
        assertEquals(0, recorder.events.filterIsInstance<RecordedEvent.Circle>().size)
    }

    @Test
    fun `HUD lists a single Debug Draw row that flips draw enabled`() {
        val tree = SceneTree(Node()).also { it.start() }
        val rows = tree.debug.widgets.filter { it.title == "Debug Draw" }
        assertEquals(1, rows.size)
        val toggle = rows.single()

        assertEquals(false, tree.debug.draw.enabled)
        toggle.enabled = true
        assertEquals(true, tree.debug.draw.enabled)
        toggle.enabled = false
        assertEquals(false, tree.debug.draw.enabled)
    }
}
