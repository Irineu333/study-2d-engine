package com.neoutils.engine.scene

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.render.Texture
import com.neoutils.engine.render.TextureBackend
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimatedSprite2DTest {

    private class FakeTexture(override val width: Int, override val height: Int) : Texture

    private class FakeTextureBackend(private val w: Int, private val h: Int) : TextureBackend {
        override fun load(path: String): Texture = FakeTexture(w, h)
        override fun dispose() {}
    }

    private fun imageEvents(renderer: RecordingRenderer): List<RecordedEvent.Image> =
        renderer.events.filterIsInstance<RecordedEvent.Image>()

    private fun attached(
        width: Int = 384,
        height: Int = 32,
        block: AnimatedSprite2D.() -> Unit = {},
    ): AnimatedSprite2D {
        val tree = SceneTree(Node())
        tree.textures = FakeTextureBackend(width, height)
        tree.start()
        val sprite = AnimatedSprite2D().apply { texturePath = "sheet.png"; block() }
        tree.root.addChild(sprite)
        return sprite
    }

    @Test
    fun `fps controls the advance rate`() {
        val sprite = attached { frameCount = 12; fps = 10f; loop = true; currentFrame = 0 }
        // 0.25s in five 0.05s ticks: one frame every 0.1s -> exactly 2 advances.
        repeat(5) { sprite.onProcess(0.05f) }
        assertEquals(2, sprite.currentFrame)
    }

    @Test
    fun `looping animation wraps around`() {
        val sprite = attached { frameCount = 4; fps = 10f; loop = true; currentFrame = 3 }
        sprite.onProcess(0.1f)
        assertEquals(0, sprite.currentFrame)
    }

    @Test
    fun `non-looping animation stops on the last frame`() {
        val sprite = attached { frameCount = 4; fps = 10f; loop = false; currentFrame = 3 }
        sprite.onProcess(0.1f)
        assertEquals(3, sprite.currentFrame)
        assertFalse(sprite.playing)
    }

    @Test
    fun `paused animation does not advance`() {
        val sprite = attached { frameCount = 4; fps = 10f; playing = false; currentFrame = 1 }
        sprite.onProcess(10f)
        assertEquals(1, sprite.currentFrame)
    }

    @Test
    fun `onDraw selects the current frame's source rectangle`() {
        val sprite = attached(384, 32) { frameCount = 12; playing = false; currentFrame = 3 }
        val renderer = RecordingRenderer()
        sprite.tree!!.render(renderer)

        val image = imageEvents(renderer).single()
        assertEquals(Rect(Vec2(96f, 0f), Vec2(32f, 32f)), image.src)
        assertEquals(Rect(Vec2(-16f, -16f), Vec2(32f, 32f)), image.dst)
    }

    @Test
    fun `flipH propagates to drawImage`() {
        val sprite = attached(384, 32) { frameCount = 12; playing = false; flipH = true }
        val renderer = RecordingRenderer()
        sprite.tree!!.render(renderer)
        assertTrue(imageEvents(renderer).single().flipH)
    }

    @Test
    fun `frame size derives from texture width and frameCount`() {
        val sprite = attached(384, 32) { frameCount = 12 }
        assertEquals(Rect(Vec2(-16f, -16f), Vec2(32f, 32f)), sprite.localBounds())
    }

    @Test
    fun `no backend means invisible but safe`() {
        val tree = SceneTree(Node())
        // textures stays null
        tree.start()
        val sprite = AnimatedSprite2D().apply { texturePath = "sheet.png"; frameCount = 4 }
        tree.root.addChild(sprite)

        val renderer = RecordingRenderer()
        tree.render(renderer)

        assertTrue(imageEvents(renderer).isEmpty())
        assertEquals(Rect(Vec2.ZERO, Vec2.ZERO), sprite.localBounds())
    }
}
