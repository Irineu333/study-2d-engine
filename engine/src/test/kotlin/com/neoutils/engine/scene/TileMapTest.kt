package com.neoutils.engine.scene

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.CollisionObject2D
import com.neoutils.engine.physics.PhysicsSystem
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.render.Texture
import com.neoutils.engine.render.TextureBackend
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TileMapTest {

    private class FakeTexture(override val width: Int, override val height: Int) : Texture

    private class FakeTextureBackend(private val w: Int, private val h: Int) : TextureBackend {
        override fun load(path: String): Texture = FakeTexture(w, h)
        override fun dispose() {}
    }

    private fun imageEvents(renderer: RecordingRenderer): List<RecordedEvent.Image> =
        renderer.events.filterIsInstance<RecordedEvent.Image>()

    private fun tileMap(): TileMap = TileMap().apply {
        tileSet = TileSet().apply { texturePath = "atlas.png"; tileWidth = 16; tileHeight = 16 }
        columns = 3
        rows = 2
        tiles = listOf(5, -1, 7, -1, 2, -1)
    }

    @Test
    fun `draws each non-empty cell at its grid position`() {
        // 64-wide atlas => 4 columns; indices map: 5 -> (1,1), 7 -> (3,1), 2 -> (2,0).
        val tree = SceneTree(Node())
        tree.textures = FakeTextureBackend(64, 32)
        tree.start()
        val map = tileMap()
        tree.root.addChild(map)

        val renderer = RecordingRenderer()
        tree.render(renderer)

        val images = imageEvents(renderer)
        assertEquals(3, images.size)

        // tile 7 sits at k=2 => map cell (c=2, r=0) => dst (32, 0).
        val tile7 = images.single { it.src == Rect(Vec2(48f, 16f), Vec2(16f, 16f)) }
        assertEquals(Rect(Vec2(32f, 0f), Vec2(16f, 16f)), tile7.dst)

        // tile 2 sits at k=4 => map cell (c=1, r=1) => dst (16, 16).
        val tile2 = images.single { it.src == Rect(Vec2(32f, 0f), Vec2(16f, 16f)) }
        assertEquals(Rect(Vec2(16f, 16f), Vec2(16f, 16f)), tile2.dst)
    }

    @Test
    fun `empty cells draw nothing`() {
        val tree = SceneTree(Node())
        tree.textures = FakeTextureBackend(64, 32)
        tree.start()
        // All-empty grid emits no draw at all.
        tree.root.addChild(TileMap().apply { columns = 2; rows = 1; tiles = listOf(-1, -1) })

        val renderer = RecordingRenderer()
        tree.render(renderer)

        assertTrue(imageEvents(renderer).isEmpty())
    }

    @Test
    fun `no backend means invisible but safe`() {
        val tree = SceneTree(Node())
        // textures stays null
        tree.start()
        val map = tileMap()
        tree.root.addChild(map)

        val renderer = RecordingRenderer()
        tree.render(renderer)

        assertTrue(imageEvents(renderer).isEmpty())
    }

    @Test
    fun `local bounds span the whole grid`() {
        val map = TileMap().apply {
            tileSet = TileSet().apply { tileWidth = 16; tileHeight = 16 }
            columns = 3
            rows = 2
        }
        assertEquals(Rect(Vec2.ZERO, Vec2(48f, 32f)), map.localBounds())
    }

    @Test
    fun `tilemap is not a collision participant`() {
        val tree = SceneTree(Node())
        tree.start()
        val map = tileMap()
        tree.root.addChild(map)

        assertFalse(map is CollisionObject2D)
        // A tree with only a TileMap steps cleanly — no collision participant.
        PhysicsSystem().step(tree, 1f / 60f)
    }
}
