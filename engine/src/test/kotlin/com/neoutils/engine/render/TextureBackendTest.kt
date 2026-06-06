package com.neoutils.engine.render

import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class TextureBackendTest {

    private class FakeTexture(override val width: Int, override val height: Int) : Texture

    private class FakeTextureBackend : TextureBackend {
        var disposeCount = 0
        private val cache = mutableMapOf<String, Texture>()
        override fun load(path: String): Texture =
            cache.getOrPut(path) { FakeTexture(32, 32) }
        override fun dispose() {
            disposeCount++
        }
    }

    @Test
    fun `SceneTree defaults textures to null`() {
        assertNull(SceneTree(Node()).textures)
    }

    @Test
    fun `null texture backend load is a graceful no-op`() {
        val tree = SceneTree(Node())
        // The canonical call site used by Sprite2D: must not throw, returns null.
        assertNull(tree.textures?.load("missing.png"))
    }

    @Test
    fun `stop disposes the texture backend exactly once`() {
        val textures = FakeTextureBackend()
        val tree = SceneTree(Node())
        tree.textures = textures
        tree.start()
        tree.stop()
        assertEquals(1, textures.disposeCount)
        // A second stop must not dispose again (handle was cleared).
        tree.stop()
        assertEquals(1, textures.disposeCount)
    }

    @Test
    fun `load caches by path`() {
        val textures = FakeTextureBackend()
        assertSame(textures.load("a.png"), textures.load("a.png"))
    }
}
