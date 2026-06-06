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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Sprite2DTest {

    private class FakeTexture(override val width: Int, override val height: Int) : Texture

    private class FakeTextureBackend(private val w: Int, private val h: Int) : TextureBackend {
        override fun load(path: String): Texture = FakeTexture(w, h)
        override fun dispose() {}
    }

    private fun imageEvents(renderer: RecordingRenderer): List<RecordedEvent.Image> =
        renderer.events.filterIsInstance<RecordedEvent.Image>()

    @Test
    fun `draws the whole texture centered on local origin`() {
        val tree = SceneTree(Node())
        tree.textures = FakeTextureBackend(64, 48)
        tree.start()
        val sprite = Sprite2D().apply { texturePath = "x.png" }
        tree.root.addChild(sprite)

        val renderer = RecordingRenderer()
        tree.render(renderer)

        val image = imageEvents(renderer).single()
        assertEquals(Rect(Vec2.ZERO, Vec2(64f, 48f)), image.src)
        assertEquals(Rect(Vec2(-32f, -24f), Vec2(64f, 48f)), image.dst)
        assertEquals(false, image.flipH)
    }

    @Test
    fun `flipH propagates to drawImage`() {
        val tree = SceneTree(Node())
        tree.textures = FakeTextureBackend(32, 32)
        tree.start()
        tree.root.addChild(Sprite2D().apply { texturePath = "x.png"; flipH = true })

        val renderer = RecordingRenderer()
        tree.render(renderer)

        assertTrue(imageEvents(renderer).single().flipH)
    }

    @Test
    fun `centered local bounds reflect the texture`() {
        val tree = SceneTree(Node())
        tree.textures = FakeTextureBackend(20, 10)
        tree.start()
        val sprite = Sprite2D().apply { texturePath = "x.png" }
        tree.root.addChild(sprite)

        assertEquals(Rect(Vec2(-10f, -5f), Vec2(20f, 10f)), sprite.localBounds())
    }

    @Test
    fun `no backend means invisible but safe`() {
        val tree = SceneTree(Node())
        // textures stays null
        tree.start()
        val sprite = Sprite2D().apply { texturePath = "x.png" }
        tree.root.addChild(sprite)

        val renderer = RecordingRenderer()
        tree.render(renderer)

        assertTrue(imageEvents(renderer).isEmpty())
        assertEquals(Rect(Vec2.ZERO, Vec2.ZERO), sprite.localBounds())
    }
}
