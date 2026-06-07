package com.neoutils.engine.scene

import com.neoutils.engine.dx.Log
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.render.Texture
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Draws a grid of tiles from a [TileSet] atlas, reusing
 * [Renderer.drawImage] once per non-empty cell. The map is a row-major
 * [tiles] array of size `columns * rows`: `tiles[r * columns + c]` is the
 * atlas index for cell `(c, r)`, or `-1` for an empty cell (drawn as nothing).
 *
 * Two grids are at play and independent: the **atlas** grid (which cells exist
 * in the texture, via [TileSet]) is indexed by the *value* `tiles[k]`; the
 * **map** grid (the layout in the world) is indexed by the *position* `k`.
 *
 * Origin is the grid's top-left corner (unlike [Sprite2D], which centers):
 * cell `(c, r)` draws at local `Rect(Vec2(c*tileWidth, r*tileHeight), ...)`,
 * so tile coordinates read straight off the grid.
 *
 * **Visual-only**: a `TileMap` is not (and does not create) a
 * `CollisionObject2D`, carries no `CollisionShape2D`, and never participates in
 * `PhysicsSystem.step`. Solid terrain is composed separately from hand-placed
 * `StaticBody2D` (invariant #3 untouched).
 *
 * With no texture backend (`tree.textures == null`, e.g. headless tests) the
 * handle stays `null`: [onDraw] is a no-op — the map is invisible but never
 * throws. [localBounds] depends only on the grid dimensions, so it is always
 * available.
 */
@Serializable
open class TileMap : Node2D() {

    /** The atlas this map's tile indices address. Embedded (no external resource system in v1). */
    @Inspect
    var tileSet: TileSet = TileSet()

    /** Map width in cells. */
    @Inspect
    var columns: Int = 0

    /** Map height in cells. */
    @Inspect
    var rows: Int = 0

    /** Row-major atlas indices (`columns * rows` expected); `-1` is an empty cell. */
    @Inspect
    var tiles: List<Int> = emptyList()

    @Transient
    private var texture: Texture? = null

    override fun onEnter() {
        super.onEnter()
        // Resolve through the tree's backend; null backend ⇒ null handle ⇒
        // invisible-but-safe (no draw).
        texture = tree?.textures?.load(tileSet.texturePath)
        val expected = columns * rows
        if (tiles.size != expected) {
            Log.w(
                TAG,
                "tiles.size ${tiles.size} != columns*rows $expected ($columns x $rows); " +
                    "out-of-range cells are skipped",
            )
        }
    }

    override fun onExit() {
        // Drop only the local reference — the backend owns the cache and frees
        // every texture on tree.stop(); disposing here would break other nodes
        // sharing the same cached handle.
        texture = null
        super.onExit()
    }

    override fun localBounds(): Rect = Rect(
        Vec2.ZERO,
        Vec2((columns * tileSet.tileWidth).toFloat(), (rows * tileSet.tileHeight).toFloat()),
    )

    override fun onDraw(renderer: Renderer) {
        val tex = texture
        if (tex != null) {
            val tw = tileSet.tileWidth
            val th = tileSet.tileHeight
            val count = minOf(tiles.size, columns * rows)
            for (k in 0 until count) {
                val index = tiles[k]
                if (index < 0) continue
                val c = k % columns
                val r = k / columns
                renderer.drawImage(
                    texture = tex,
                    src = tileSet.src(index, tex),
                    dst = Rect(
                        Vec2((c * tw).toFloat(), (r * th).toFloat()),
                        Vec2(tw.toFloat(), th.toFloat()),
                    ),
                )
            }
        }
        super.onDraw(renderer)
    }

    private companion object {
        const val TAG = "TileMap"
    }
}
