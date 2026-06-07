package com.neoutils.engine.games.demos

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.scene.TileMap
import com.neoutils.engine.scene.TileSet
import kotlinx.serialization.Serializable

/**
 * Tilemap sentinel: a single [TileMap] assembling one simple ground strip from
 * the real Pixel Adventure 1 `Terrain (16x16).png` atlas (352x176, 16px cells
 * => 22 columns), scaled up so the index->cell->dst mapping is obvious and
 * nearest-neighbor stays crisp. Runs identically on Skiko (default) and LWJGL
 * (`runLwjgl`), proving the mapping before the platformer demo.
 *
 * The grid is written out as a literal so the mapping reads straight off the
 * source: each number is an atlas tile index, `-1` is empty (sky), and the
 * green-grass block lives at atlas cols 6..8 — row 0 is the grass cap
 * (`6,7,8` = left/mid/right) and row 1 is the dirt below (`28,29,30`).
 */
@Serializable
class TileMapDemo : Node2D() {

    init {
        name = "TileMapDemo"
    }

    override fun onEnter() {
        val tree = tree ?: return

        val cols = 6
        val rows = 3
        val tiles = listOf(
            -1, -1, -1, -1, -1, -1, //   sky
            6, 7, 7, 7, 7, 8,       //   grass cap: left, mid×4, right
            28, 29, 29, 29, 29, 30, //   dirt:      left, mid×4, right
        )

        val scale = 4f
        val mapW = cols * 16 * scale
        val mapH = rows * 16 * scale
        addChild(
            TileMap().apply {
                tileSet = TileSet().apply {
                    texturePath = "demos/tiles/terrain.png"
                    tileWidth = 16
                    tileHeight = 16
                }
                columns = cols
                this.rows = rows
                this.tiles = tiles
                transform = Transform(
                    position = Vec2((tree.width - mapW) / 2f, (tree.height - mapH) / 2f),
                    scale = Vec2(scale, scale),
                )
            },
        )
    }

    override fun onDraw(renderer: Renderer) {
        renderer.drawText(
            "TileMap (atlas grid -> drawImage per cell, 3x) — same on Skiko + LWJGL",
            Vec2(8f, tree?.height?.minus(24f) ?: 576f),
            size = 14f,
            color = Color(1f, 1f, 1f, 0.85f),
        )
    }
}
