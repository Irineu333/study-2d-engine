package com.neoutils.engine.scene

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Texture
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

/**
 * Describes a tile atlas: a single image cut into a grid of equally-sized
 * cells. A descriptor of data (`@Serializable`, **not** a Node) shared by
 * [TileMap]s, analogous to [com.neoutils.engine.physics.Shape2D] held by a
 * `CollisionShape2D` — it has no transform nor lifecycle of its own.
 *
 * The atlas column count is **derived** from the loaded texture
 * (`texture.width / tileWidth`) rather than stored, so it can never drift from
 * the real image. A tile index `i` (>= 0) addresses the cell `(i % columns,
 * i / columns)` in row-major order; [src] turns it into the source rect that
 * [com.neoutils.engine.render.Renderer.drawImage] samples.
 */
@Serializable
class TileSet {

    /** Classpath path of the atlas image (e.g. `"demos/tiles/terrain.png"`). */
    @Inspect
    var texturePath: String = ""

    /** Width of one atlas cell, in texture pixels. */
    @Inspect
    var tileWidth: Int = 16

    /** Height of one atlas cell, in texture pixels. */
    @Inspect
    var tileHeight: Int = 16

    /** Number of cells per atlas row, derived from the texture width. */
    fun columns(texture: Texture): Int = texture.width / tileWidth

    /**
     * Source rect (in texture pixels) for tile [index] in [texture]. The atlas
     * is row-major: `col = index % columns`, `row = index / columns`.
     */
    fun src(index: Int, texture: Texture): Rect {
        val columns = columns(texture).coerceAtLeast(1)
        val col = index % columns
        val row = index / columns
        return Rect(
            Vec2((col * tileWidth).toFloat(), (row * tileHeight).toFloat()),
            Vec2(tileWidth.toFloat(), tileHeight.toFloat()),
        )
    }
}
