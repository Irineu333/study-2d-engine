package com.neoutils.engine.games.demos

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Circle2D
import com.neoutils.engine.scene.Node2D
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

@Serializable
class SolarSystemDemo : Node2D() {

    @Transient
    private var lastSize: Vec2 = Vec2.ZERO

    init {
        name = "SolarSystemDemo"
        if (children.isEmpty()) buildTree()
    }

    override fun onProcess(dt: Float) {
        val tree = tree ?: return
        if (tree.size == lastSize) return
        lastSize = tree.size
        val center = findChild("Center") as? Node2D ?: return
        center.transform = center.transform.copy(
            position = Vec2(tree.width / 2f, tree.height / 2f),
        )
    }

    // Orbit trails are drawn from here in world coordinates rather than from
    // sibling nodes, so the tree topology stays exactly as the spec describes
    // (Center.children, Planet.children counts unchanged).
    override fun onDraw(renderer: Renderer) {
        val center = findChild("Center") as? Node2D ?: return
        val centerWorld = center.world().position
        for (child in center.children) {
            if (child !is Rotator) continue
            val planet = child.children.firstOrNull() as? Node2D ?: continue
            drawDashedCircle(renderer, centerWorld, planet.transform.position.x)
            val planetWorld = planet.world().position
            for (moonChild in planet.children) {
                if (moonChild !is Rotator) continue
                val moon = moonChild.children.firstOrNull() as? Node2D ?: continue
                drawDashedCircle(renderer, planetWorld, moon.transform.position.x)
            }
        }
        super.onDraw(renderer)
    }

    private fun drawDashedCircle(renderer: Renderer, center: Vec2, radius: Float) {
        val step = TWO_PI / TRAIL_SEGMENTS
        var i = 0
        while (i < TRAIL_SEGMENTS) {
            val a0 = i * step
            val a1 = a0 + step
            val p0 = Vec2(center.x + cos(a0) * radius, center.y + sin(a0) * radius)
            val p1 = Vec2(center.x + cos(a1) * radius, center.y + sin(a1) * radius)
            renderer.drawLine(p0, p1, TRAIL_THICKNESS, TRAIL_COLOR)
            i += 2
        }
    }

    private fun buildTree() {
        val initialSize = tree?.size ?: Vec2(DEFAULT_WIDTH, DEFAULT_HEIGHT)
        val unit = min(initialSize.x, initialSize.y)

        val center = Node2D()
        center.name = "Center"
        center.transform = Transform(position = Vec2(initialSize.x / 2f, initialSize.y / 2f))

        val sun = Circle2D()
        sun.name = "Sun"
        sun.radius = Sizes.SUN
        sun.color = Palette.SUN
        center.addChild(sun)

        center.addChild(buildPlanet("Mercury", Radii.MERCURY * unit, Speeds.MERCURY, Sizes.MERCURY, Palette.MERCURY))
        center.addChild(buildPlanet("Venus", Radii.VENUS * unit, Speeds.VENUS, Sizes.VENUS, Palette.VENUS))

        val earth = planetOf(buildPlanet("Earth", Radii.EARTH * unit, Speeds.EARTH, Sizes.EARTH, Palette.EARTH).also(center::addChild))
        earth.addChild(buildMoon("Moon", Radii.MOON, Speeds.MOON, Sizes.MOON, Palette.MOON))

        center.addChild(buildPlanet("Mars", Radii.MARS * unit, Speeds.MARS, Sizes.MARS, Palette.MARS))

        val jupiter = planetOf(buildPlanet("Jupiter", Radii.JUPITER * unit, Speeds.JUPITER, Sizes.JUPITER, Palette.JUPITER).also(center::addChild))
        jupiter.addChild(buildMoon("Io", Radii.IO, Speeds.IO, Sizes.IO, Palette.IO))
        jupiter.addChild(buildMoon("Europa", Radii.EUROPA, Speeds.EUROPA, Sizes.EUROPA, Palette.EUROPA))
        jupiter.addChild(buildMoon("Ganymede", Radii.GANYMEDE, Speeds.GANYMEDE, Sizes.GANYMEDE, Palette.GANYMEDE))
        jupiter.addChild(buildMoon("Callisto", Radii.CALLISTO, Speeds.CALLISTO, Sizes.CALLISTO, Palette.CALLISTO))

        val saturn = planetOf(buildPlanet("Saturn", Radii.SATURN * unit, Speeds.SATURN, Sizes.SATURN, Palette.SATURN).also(center::addChild))
        saturn.addChild(SaturnRing())
        saturn.addChild(buildMoon("Titan", Radii.TITAN, Speeds.TITAN, Sizes.TITAN, Palette.TITAN))

        center.addChild(buildPlanet("Uranus", Radii.URANUS * unit, Speeds.URANUS, Sizes.URANUS, Palette.URANUS))

        val neptune = planetOf(buildPlanet("Neptune", Radii.NEPTUNE * unit, Speeds.NEPTUNE, Sizes.NEPTUNE, Palette.NEPTUNE).also(center::addChild))
        neptune.addChild(buildMoon("Triton", Radii.TRITON, Speeds.TRITON, Sizes.TRITON, Palette.TRITON))

        addChild(center)
    }

    // A planet is a (Rotator named "<Name>Orbit") with a single Circle2D child
    // named "<Name>" at local (radius, 0). The Circle2D is returned wrapped so
    // moons can be attached directly to it.
    private fun buildPlanet(
        bodyName: String,
        orbitRadius: Float,
        omega: Float,
        bodySize: Float,
        bodyColor: Color,
    ): Rotator {
        val orbit = Rotator()
        orbit.name = "${bodyName}Orbit"
        orbit.angularVelocity = omega
        orbit.transform = Transform(rotation = Random.nextFloat() * TWO_PI)
        val planet = Circle2D()
        planet.name = bodyName
        planet.radius = bodySize
        planet.color = bodyColor
        planet.transform = Transform(position = Vec2(orbitRadius, 0f))
        orbit.addChild(planet)
        return orbit
    }

    private fun buildMoon(
        bodyName: String,
        orbitRadius: Float,
        omega: Float,
        bodySize: Float,
        bodyColor: Color,
    ): Rotator = buildPlanet(bodyName, orbitRadius, omega, bodySize, bodyColor)

    // The planet Circle2D is the only child of its *Orbit Rotator — moons
    // hang off the planet itself, not the orbit, so they inherit the planet's
    // own (rotated) world transform.
    private fun planetOf(orbit: Rotator): Node2D = orbit.children.first() as Node2D

    companion object {
        private const val DEFAULT_WIDTH: Float = 800f
        private const val DEFAULT_HEIGHT: Float = 600f
        private const val TWO_PI: Float = (2.0 * PI).toFloat()

        // Orbit trail tunables: 64 segments alternating drawn/skipped = 32
        // visible dashes around each orbit, readable from ~18 px (moon) to
        // ~270 px (Neptune) without changing.
        private const val TRAIL_SEGMENTS: Int = 64
        private const val TRAIL_THICKNESS: Float = 1f
        private val TRAIL_COLOR = Color(1f, 1f, 1f, 0.22f)
    }

    object Radii {
        // Orbital radii as fractions of min(width, height). Spaced wider than
        // the viewport on purpose — outer planets are expected to clip the
        // window edges so the relative scale reads (Neptune ~1.5× unit, well
        // off-screen at 800×600).
        const val MERCURY: Float = 0.10f
        const val VENUS: Float = 0.18f
        const val EARTH: Float = 0.28f
        const val MARS: Float = 0.40f
        const val JUPITER: Float = 0.60f
        const val SATURN: Float = 0.85f
        const val URANUS: Float = 1.15f
        const val NEPTUNE: Float = 1.50f

        // Lunar orbital radii in absolute pixels (relative to parent planet).
        const val MOON: Float = 18f
        const val IO: Float = 22f
        const val EUROPA: Float = 30f
        const val GANYMEDE: Float = 40f
        const val CALLISTO: Float = 52f
        const val TITAN: Float = 35f
        const val TRITON: Float = 20f
    }

    object Speeds {
        // Angular velocities in rad/s, calibrated for legibility (not realism).
        const val MERCURY: Float = 1.45f
        const val VENUS: Float = 0.83f
        const val EARTH: Float = 0.60f
        const val MARS: Float = 0.39f
        const val JUPITER: Float = 0.13f
        const val SATURN: Float = 0.08f
        const val URANUS: Float = 0.04f
        const val NEPTUNE: Float = 0.02f

        const val MOON: Float = 4.0f
        const val IO: Float = 5.0f
        const val EUROPA: Float = 4.0f
        const val GANYMEDE: Float = 3.0f
        const val CALLISTO: Float = 2.0f
        const val TITAN: Float = 2.5f
        const val TRITON: Float = 3.5f
    }

    object Sizes {
        // Visual radii in absolute pixels.
        const val SUN: Float = 28f
        const val MERCURY: Float = 3f
        const val VENUS: Float = 5f
        const val EARTH: Float = 5f
        const val MOON: Float = 2f
        const val MARS: Float = 4f
        const val JUPITER: Float = 12f
        const val IO: Float = 2f
        const val EUROPA: Float = 2f
        const val GANYMEDE: Float = 3f
        const val CALLISTO: Float = 3f
        const val SATURN: Float = 10f
        const val TITAN: Float = 3f
        const val URANUS: Float = 8f
        const val NEPTUNE: Float = 8f
        const val TRITON: Float = 2f
    }

    object Palette {
        val SUN = Color(1.0f, 0.85f, 0.3f)
        val MERCURY = Color(0.6f, 0.6f, 0.6f)
        val VENUS = Color(0.95f, 0.85f, 0.6f)
        val EARTH = Color(0.3f, 0.5f, 0.95f)
        val MOON = Color(0.85f, 0.85f, 0.85f)
        val MARS = Color(0.85f, 0.35f, 0.2f)
        val JUPITER = Color(0.85f, 0.7f, 0.5f)
        val IO = Color(1.0f, 0.95f, 0.4f)
        val EUROPA = Color(0.95f, 0.9f, 0.85f)
        val GANYMEDE = Color(0.7f, 0.6f, 0.5f)
        val CALLISTO = Color(0.5f, 0.45f, 0.4f)
        val SATURN = Color(0.9f, 0.8f, 0.55f)
        val TITAN = Color(0.9f, 0.7f, 0.3f)
        val URANUS = Color(0.6f, 0.85f, 0.9f)
        val NEPTUNE = Color(0.25f, 0.4f, 0.85f)
        val TRITON = Color(0.8f, 0.8f, 0.95f)
    }
}

// Local visual: a hollow circle squashed on Y to read as an edge-on ellipse.
// Saturn-specific decoration; not promoted to :engine because (a) it's not a
// generic primitive and (b) the engine has no Ellipse node yet.
@Serializable
class SaturnRing : Node2D() {

    init {
        name = "SaturnRing"
        transform = Transform(scale = Vec2(1f, RING_FLATTEN))
    }

    override fun onDraw(renderer: Renderer) {
        renderer.drawCircle(
            center = Vec2.ZERO,
            radius = RING_RADIUS,
            color = RING_COLOR,
            filled = false,
            thickness = RING_THICKNESS,
        )
        super.onDraw(renderer)
    }

    companion object {
        const val RING_RADIUS: Float = 20f
        const val RING_THICKNESS: Float = 1.5f
        const val RING_FLATTEN: Float = 0.4f
        val RING_COLOR = Color(0.9f, 0.85f, 0.7f, 0.6f)
    }
}
