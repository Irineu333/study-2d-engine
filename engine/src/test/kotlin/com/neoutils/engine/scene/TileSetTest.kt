package com.neoutils.engine.scene

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Texture
import kotlin.test.Test
import kotlin.test.assertEquals

class TileSetTest {

    private class FakeTexture(override val width: Int, override val height: Int) : Texture

    @Test
    fun `tile index maps to the correct atlas cell`() {
        // 352x176 atlas, 16px cells => 22 columns. Index 25 => (col 3, row 1).
        val atlas = FakeTexture(352, 176)
        val tileSet = TileSet().apply { tileWidth = 16; tileHeight = 16 }

        assertEquals(22, tileSet.columns(atlas))
        assertEquals(Rect(Vec2(48f, 16f), Vec2(16f, 16f)), tileSet.src(25, atlas))
    }

    @Test
    fun `first cell maps to the origin`() {
        val atlas = FakeTexture(64, 64)
        val tileSet = TileSet().apply { tileWidth = 16; tileHeight = 16 }

        assertEquals(Rect(Vec2(0f, 0f), Vec2(16f, 16f)), tileSet.src(0, atlas))
    }
}
