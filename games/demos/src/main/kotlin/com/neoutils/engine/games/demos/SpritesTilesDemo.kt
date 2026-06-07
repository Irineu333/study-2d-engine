package com.neoutils.engine.games.demos

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.CharacterBody2D
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.scene.AnimatedSprite2D
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.scene.Sprite2D
import com.neoutils.engine.scene.TileMap
import com.neoutils.engine.scene.TileSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.abs

private const val TILE = 16
private const val TILE_SCALE = 3f
private const val CELL = TILE * TILE_SCALE
private const val GROUND_ROWS = 2

private const val PLAYER_W = 44f
private const val PLAYER_H = 72f
private const val PLAYER_SCALE = 2.5f
private const val DECORATION_SCALE = 2.5f
private const val WALK_SPEED = 120f
private const val GRAVITY = 900f
private const val WALL_THICKNESS = 20f

/**
 * Funde os antigos `Sprite` + `Animated` + `Tilemap`: um `TileMap` monta o chão
 * a partir do atlas real, um `AnimatedSprite2D` (dentro do player) "corre" sobre
 * ele com avanço de frame engine-driven, e um `Sprite2D` estático decora a cena.
 * O player é um `CharacterBody2D` que cai por gravidade e anda sobre o chão de
 * `StaticBody2D` via `moveAndCollide` (movimento separado por eixo), virando ao
 * bater nas paredes laterais. Sentinela cross-backend (Skiko + LWJGL) de
 * `texture-rendering`, `sprite-animation` e `tilemap-visual` numa só tela.
 */
@Serializable
class SpritesTilesDemo : Node2D() {

    init {
        name = "SpritesTilesDemo"
    }

    override fun onEnter() {
        val tree = tree ?: return
        val w = tree.width
        val h = tree.height
        val cols = (w / CELL).toInt() + 1
        val groundTop = h - GROUND_ROWS * CELL

        addChild(buildGround(cols, groundTop))

        // Solid floor (StaticBody2D) aligned with the tilemap grass top. Same
        // parent as the player so moveAndCollide's same-parent sweep finds it.
        addChild(
            makeStaticWall(
                position = Vec2(w / 2f, groundTop + WALL_THICKNESS / 2f),
                size = Vec2(w + 2f * WALL_THICKNESS, WALL_THICKNESS),
            ).apply { name = "Floor" }
        )
        // Side walls turn the runner around.
        addChild(
            makeStaticWall(Vec2(-WALL_THICKNESS / 2f, h / 2f), Vec2(WALL_THICKNESS, h))
                .apply { name = "LeftWall" }
        )
        addChild(
            makeStaticWall(Vec2(w + WALL_THICKNESS / 2f, h / 2f), Vec2(WALL_THICKNESS, h))
                .apply { name = "RightWall" }
        )

        // Static decorative sprite: a single-frame apple item resting on the
        // ground (not a second character — exercises Sprite2D's whole-texture
        // draw with a non-redundant visual).
        addChild(
            Sprite2D().apply {
                name = "Apple"
                texturePath = "demos/sprites/apple.png"
                transform = Transform(
                    position = Vec2(110f, groundTop - 14f),
                    scale = Vec2(DECORATION_SCALE, DECORATION_SCALE),
                )
            }
        )

        // The running player, spawned above the floor so it visibly falls onto it.
        addChild(
            Runner().apply {
                name = "Runner"
                transform = Transform(position = Vec2(w / 2f, groundTop - 200f))
            }
        )
    }

    private fun buildGround(cols: Int, groundTop: Float): TileMap {
        // Row 0 = grass cap (atlas col 7 = middle grass), row 1 = dirt (col 29).
        val tiles = ArrayList<Int>(cols * GROUND_ROWS)
        repeat(cols) { tiles += 7 }
        repeat(cols) { tiles += 29 }
        return TileMap().apply {
            name = "Ground"
            tileSet = TileSet().apply {
                texturePath = "demos/tiles/terrain.png"
                tileWidth = TILE
                tileHeight = TILE
            }
            columns = cols
            rows = GROUND_ROWS
            this.tiles = tiles
            transform = Transform(
                position = Vec2(0f, groundTop),
                scale = Vec2(TILE_SCALE, TILE_SCALE),
            )
        }
    }
}

/**
 * `CharacterBody2D` player: axis-separated `moveAndCollide` walk over the
 * `StaticBody2D` floor, falling under gravity and turning around (flipping the
 * run animation) when it hits a side wall.
 */
class Runner : CharacterBody2D() {

    @Transient
    private var vx: Float = WALK_SPEED

    @Transient
    private var vy: Float = 0f

    init {
        addChild(
            CollisionShape2D().apply {
                shape = RectangleShape2D().apply { size = Vec2(PLAYER_W, PLAYER_H) }
            }
        )
        addChild(
            AnimatedSprite2D().apply {
                name = "art"
                texturePath = "demos/sprites/pink-man-run.png"
                frameCount = 12
                fps = 20f
                transform = Transform(scale = Vec2(PLAYER_SCALE, PLAYER_SCALE))
            }
        )
    }

    override fun onPhysicsProcess(dt: Float) {
        // Horizontal: walk; flip on side-wall contact.
        val hit = moveAndCollide(Vec2(vx * dt, 0f))
        if (hit != null && abs(hit.normal.x) > abs(hit.normal.y)) {
            vx = -vx
            (findChild("art") as? AnimatedSprite2D)?.flipH = vx < 0f
        }
        // Vertical: gravity; zero on floor/ceiling contact.
        vy += GRAVITY * dt
        val fall = moveAndCollide(Vec2(0f, vy * dt))
        if (fall != null) vy = 0f
    }
}
