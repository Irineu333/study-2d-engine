package com.neoutils.engine.games.demos

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.CharacterBody2D
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.physics.StaticBody2D
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node2D
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Enxame de quadrados com velocidade linear **e** angular, colidindo
 * elasticamente contra paredes e entre si com **resposta rotacional por
 * impulso** — o tipo de comportamento que Demo 4 (reflect-pela-normal)
 * não consegue expressar.
 *
 * Modelo de contato com dois sabores:
 *
 *  - **Wall hit** (square-vs-StaticBody2D): impulso normal elástico
 *    (`e = 1`) + impulso tangencial de **Coulomb** capped em `MU·|jn|`,
 *    aplicados no canto líder de A na direção `-n`. Quadrado raspando a
 *    parede converte sliding em rolling; quina batida gera spin pelo
 *    lever arm tangencial.
 *  - **Pair hit** (square-vs-square): só impulso normal elástico, sem
 *    fricção. Friction inter-corpos é fisicamente válida mas acopla
 *    sliding → spin "do nada" e fazia os dois quadrados ganharem rotação
 *    em pares simples. Sem ela, o par fica tipo bilhar: troca momento
 *    linear, troca momento angular via lever arm do contato, mas não
 *    gera spin a partir de movimento tangencial. O ponto de contato é o
 *    **midpoint dos cantos líderes** de A e B na direção um do outro,
 *    deixando `rA ≈ -rB` e impulsos angulares balanceados.
 *
 * Fórmula do impulso normal:
 *
 *   `jn = -(1+e)·(v_rel · n) / (1/mA + 1/mB + (rA×n)²/IA + (rB×n)²/IB)`
 *
 * com `rA = P - centroA` e `vAP = vA + ω × rA`. Aplicar `jn·n` no ponto P
 * muda velocidade linear E angular simultaneamente.
 *
 * O ponto de contato é aproximado por **canto mais avançado**: dos 4 cantos
 * world-space do quadrado, escolhemos o que tem menor projeção na normal
 * (= mais penetrado). Empates dentro de um epsilon (caso face-a-face) são
 * promediados, virando o midpoint da face. Resultado:
 *
 *  - hit face-a-face: dois cantos empatam → midpoint da face → `rA` paralelo
 *    a `-n` → `rA × n = 0` → zero spin induzido (correto).
 *  - hit canto-em-face: um canto ganha → `rA` com componente tangencial →
 *    spin induzido proporcional à perpendicularidade entre `rA` e `n`.
 *
 * `collision.point` hoje devolve o centro do OBB, não o ponto geométrico —
 * por isso a aproximação local. Refinar `SweepResult.point` é trabalho de
 * uma change futura.
 *
 * O sweep só fica rotacionado quando `transform.rotation != 0f`, então cada
 * pair routes através de `sweepRotatedRectRotatedRect`.
 */
private const val SQUARE_COUNT = 16
private const val SQUARE_SIZE = 24f
private const val WALL_THICKNESS = 10f

// mass = 1, momento de inércia uniforme de um quadrado: I = m·(w² + h²)/12.
// Para w == h == SIZE: I = SIZE²/6.
private const val SQUARE_INERTIA = SQUARE_SIZE * SQUARE_SIZE / 6f

// Coeficiente de fricção de Coulomb no contato. Cap do impulso tangencial:
// |j_t| <= MU · |j_n|. ~0.4 é típico de plástico/madeira; baixo → desliza,
// alto → "agarra" e rola.
private const val MU = 0.4f
private const val FRICTION_EPS = 1e-3f

@Serializable
class TumblingSwarmDemo : Node2D() {

    @Transient
    private val rng = Random(0xDEADBEEF7L)

    @Transient
    private var instantFps: Float = 0f

    init {
        name = "TumblingSwarmDemo"
    }

    override fun onEnter() {
        val tree = tree ?: return
        val w = tree.width
        val h = tree.height
        addChild(makeWall(Vec2(-WALL_THICKNESS, -WALL_THICKNESS), Vec2(w + 2f * WALL_THICKNESS, WALL_THICKNESS)).apply { name = "topWall" })
        addChild(makeWall(Vec2(-WALL_THICKNESS, h), Vec2(w + 2f * WALL_THICKNESS, WALL_THICKNESS)).apply { name = "bottomWall" })
        addChild(makeWall(Vec2(-WALL_THICKNESS, 0f), Vec2(WALL_THICKNESS, h)).apply { name = "leftWall" })
        addChild(makeWall(Vec2(w, 0f), Vec2(WALL_THICKNESS, h)).apply { name = "rightWall" })
        val padding = SQUARE_SIZE
        repeat(SQUARE_COUNT) { i ->
            val px = padding + rng.nextFloat() * (w - 2f * padding)
            val py = padding + rng.nextFloat() * (h - 2f * padding)
            val speed = 90f + rng.nextFloat() * 90f
            val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
            val localRotation = rng.nextFloat() * 2f * Math.PI.toFloat()
            // Initial angular velocity in ±2 rad/s — high enough that
            // contacts visibly transfer spin between squares.
            val angularVel = (rng.nextFloat() - 0.5f) * 4f
            addChild(
                TumblingSquare(
                    color = hue(i.toFloat() / SQUARE_COUNT),
                    initPos = Vec2(px, py),
                    initVx = cos(angle) * speed,
                    initVy = sin(angle) * speed,
                    initRotation = localRotation,
                    initAngularVel = angularVel,
                ).apply { name = "TumblingSquare$i" }
            )
        }
    }

    private fun makeWall(position: Vec2, size: Vec2): StaticBody2D {
        val body = StaticBody2D().apply { transform = Transform(position = position) }
        body.addChild(
            CollisionShape2D().apply {
                shape = RectangleShape2D().apply { this.size = size }
            }
        )
        return body
    }

    override fun onProcess(dt: Float) {
        if (dt > 0f) instantFps = 1f / dt
    }

    override fun onDraw(renderer: Renderer) {
        val text = "tumbling squares: $SQUARE_COUNT | fps: ${instantFps.roundToInt()}"
        val sceneW = tree?.width ?: 800f
        val textW = renderer.measureText(text, 14f).x
        renderer.drawText(text, Vec2(sceneW - textW - 8f, 18f), size = 14f, color = Color.WHITE)
    }

    private fun hue(h: Float): Color {
        val i = (h * 6f).toInt()
        val f = h * 6f - i
        return when (i % 6) {
            0 -> Color(1f, f, 0f)
            1 -> Color(1f - f, 1f, 0f)
            2 -> Color(0f, 1f, f)
            3 -> Color(0f, 1f - f, 1f)
            4 -> Color(f, 0f, 1f)
            else -> Color(1f, 0f, 1f - f)
        }
    }
}

class TumblingSquare(
    color: Color,
    initPos: Vec2,
    initVx: Float,
    initVy: Float,
    initRotation: Float,
    initAngularVel: Float,
) : CharacterBody2D() {

    @Transient
    internal var vx: Float = initVx

    @Transient
    internal var vy: Float = initVy

    @Transient
    internal var angularVel: Float = initAngularVel

    @Transient
    private val fillColor: Color = color

    init {
        transform = Transform(position = initPos, rotation = initRotation)
        addChild(
            CollisionShape2D().apply {
                // Center the rect on the body's position so rotation pivots
                // around the geometric center, not the top-left corner.
                transform = Transform(position = Vec2(-SQUARE_SIZE / 2f, -SQUARE_SIZE / 2f))
                shape = RectangleShape2D().apply { size = Vec2(SQUARE_SIZE, SQUARE_SIZE) }
            }
        )
    }

    override fun onPhysicsProcess(dt: Float) {
        // Integrate angular velocity into rotation before the sweep so the
        // shape uses the up-to-date orientation. moveAndCollide snapshots
        // rotation at the call's start; with this dt the intra-tick drift
        // is sub-pixel.
        transform = transform.copy(rotation = transform.rotation + angularVel * dt)

        val collision = moveAndCollide(Vec2(vx, vy) * dt) ?: return
        val n = collision.normal
        val other = collision.collider
        if (other is TumblingSquare) {
            // Identity-hash ordering: only the lower-hash side computes the
            // impulse, mutating both bodies in one go. Avoids double-apply
            // when the other side's onPhysicsProcess triggers the same pair.
            if (System.identityHashCode(this) < System.identityHashCode(other)) {
                resolveSquareSquare(this, other, n)
            }
        } else {
            resolveSquareWall(this, n)
        }
    }

    override fun onDraw(renderer: Renderer) {
        // Local-space polygon; SceneTree.render pushes our world transform
        // (position + rotation) so vertices rotate with the body.
        val h = SQUARE_SIZE / 2f
        renderer.drawPolygon(
            listOf(
                Vec2(-h, -h),
                Vec2(h, -h),
                Vec2(h, h),
                Vec2(-h, h),
            ),
            fillColor,
        )
    }
}

// Leading point on a rotated square's surface in the `-normal` direction.
// Returns the offset from the square's center to that point (i.e., `rA`
// directly, no center addition). Ties within epsilon are averaged so face-
// vs-face hits collapse to the face midpoint (zero tangential lever arm),
// while vertex hits keep a single corner (non-zero lever arm → spin).
private fun leadingOffset(rotation: Float, halfSize: Float, normal: Vec2): Vec2 {
    val c = cos(rotation)
    val s = sin(rotation)
    val locals = arrayOf(
        Vec2(-halfSize, -halfSize),
        Vec2(halfSize, -halfSize),
        Vec2(halfSize, halfSize),
        Vec2(-halfSize, halfSize),
    )
    val worldOffsets = Array(4) { i ->
        val l = locals[i]
        Vec2(l.x * c - l.y * s, l.x * s + l.y * c)
    }
    var minProj = Float.POSITIVE_INFINITY
    for (off in worldOffsets) {
        val p = off.x * normal.x + off.y * normal.y
        if (p < minProj) minProj = p
    }
    val eps = halfSize * 0.05f
    var sumX = 0f; var sumY = 0f; var count = 0
    for (off in worldOffsets) {
        val p = off.x * normal.x + off.y * normal.y
        if (p - minProj < eps) {
            sumX += off.x
            sumY += off.y
            count++
        }
    }
    return Vec2(sumX / count, sumY / count)
}

// Wall (mass = ∞): only the square accumulates impulse. The lever arm
// `rA` comes from the leading corner of the rotated square, so a quina
// hit produces non-zero `rA × n` and the wall imparts spin onto the body.
// After the normal impulse we also apply a tangential (Coulomb) friction
// impulse — what couples linear sliding and angular spin so a square
// scraping along a wall picks up roll instead of just bouncing.
private fun resolveSquareWall(a: TumblingSquare, n: Vec2) {
    val rA = leadingOffset(a.transform.rotation, SQUARE_SIZE / 2f, n)
    // velocity at contact = vA + ω × rA (2D cross: ω × r = (-ω·ry, ω·rx))
    val vAPx = a.vx - a.angularVel * rA.y
    val vAPy = a.vy + a.angularVel * rA.x
    val velRelN = vAPx * n.x + vAPy * n.y
    if (velRelN >= 0f) return // already separating

    // 2D scalar cross: r × n = rx·ny - ry·nx
    val rAxN = rA.x * n.y - rA.y * n.x
    val denomN = 1f /* 1/mA */ + (rAxN * rAxN) / SQUARE_INERTIA
    val jn = -2f * velRelN / denomN // e = 1 (elastic)
    a.vx += jn * n.x
    a.vy += jn * n.y
    a.angularVel += jn * rAxN / SQUARE_INERTIA

    // Coulomb friction at contact. Tangent unit vector points in the
    // direction A is sliding; apply an opposite impulse that tries to
    // brake the tangential velocity to zero, capped at MU·|jn|.
    val velTangX = vAPx - velRelN * n.x
    val velTangY = vAPy - velRelN * n.y
    val velTangLen = sqrt(velTangX * velTangX + velTangY * velTangY)
    if (velTangLen < FRICTION_EPS) return
    val tx = velTangX / velTangLen
    val ty = velTangY / velTangLen
    val rAxT = rA.x * ty - rA.y * tx
    val denomT = 1f + (rAxT * rAxT) / SQUARE_INERTIA
    val jtBrake = velTangLen / denomT // magnitude that would fully stop sliding
    val jt = min(jtBrake, MU * jn)
    a.vx -= jt * tx
    a.vy -= jt * ty
    a.angularVel -= jt * rAxT / SQUARE_INERTIA
}

// Equal-mass square-vs-square. Contact point is A's leading corner toward
// B (along `-n`); B's lever arm is taken relative to that same world point.
// Glancing hits (corner-into-face) thus generate spin on both bodies.
// Square-vs-square elastic impulse. Two design choices keep the angular
// behavior recognizable:
//
//  - **Symmetric contact point**: `P = midpoint(centroA + leadA, centroB +
//    leadB)`. With the old asymmetric `P = centroA + leadA`, `rB` grew
//    with the distance between centers and the angular impulse on B
//    scaled with that lever — distant offset hits spun B disproportion-
//    ally. The midpoint keeps `rA ≈ -rB`, so the angular impulses are
//    balanced and look like co-rotation about the contact (Newton's
//    third law on the lever).
//  - **No Coulomb friction between squares**. Friction is what converts
//    linear sliding into spin at the contact (rolling) — physically
//    correct, but it makes pair hits feel like both bodies "spin up from
//    nowhere". Wall hits still use friction (great for scraping/rolling
//    against the arena); pair hits stay clean billiard-elastic.
private fun resolveSquareSquare(a: TumblingSquare, b: TumblingSquare, n: Vec2) {
    val centerA = a.position
    val centerB = b.position
    val h = SQUARE_SIZE / 2f
    val leadA = leadingOffset(a.transform.rotation, h, n)
    // B's leading toward A is in the direction opposite to n (n points from
    // B outward toward A; B's "leading face" toward A lies on B's side
    // closest to A, i.e., where -n projects most into B).
    val leadB = leadingOffset(b.transform.rotation, h, Vec2(-n.x, -n.y))
    val pX = (centerA.x + leadA.x + centerB.x + leadB.x) * 0.5f
    val pY = (centerA.y + leadA.y + centerB.y + leadB.y) * 0.5f
    val rAx = pX - centerA.x
    val rAy = pY - centerA.y
    val rBx = pX - centerB.x
    val rBy = pY - centerB.y
    val vAPx = a.vx - a.angularVel * rAy
    val vAPy = a.vy + a.angularVel * rAx
    val vBPx = b.vx - b.angularVel * rBy
    val vBPy = b.vy + b.angularVel * rBx
    val velRelX = vAPx - vBPx
    val velRelY = vAPy - vBPy
    val velRelN = velRelX * n.x + velRelY * n.y
    if (velRelN >= 0f) return // separating

    val rAxN = rAx * n.y - rAy * n.x
    val rBxN = rBx * n.y - rBy * n.x
    val denomN = 1f + 1f + (rAxN * rAxN) / SQUARE_INERTIA + (rBxN * rBxN) / SQUARE_INERTIA
    val jn = -2f * velRelN / denomN
    a.vx += jn * n.x
    a.vy += jn * n.y
    b.vx -= jn * n.x
    b.vy -= jn * n.y
    a.angularVel += jn * rAxN / SQUARE_INERTIA
    b.angularVel -= jn * rBxN / SQUARE_INERTIA
}
