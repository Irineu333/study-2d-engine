package com.neoutils.engine.physics

import com.neoutils.engine.dx.ConsoleLogSink
import com.neoutils.engine.dx.Log
import com.neoutils.engine.dx.LogLevel
import com.neoutils.engine.dx.LogSink
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun makeBody(size: Vec2, position: Vec2 = Vec2.ZERO): StaticBody2D {
    val body = StaticBody2D().apply { transform = Transform(position = position) }
    val shapeNode = CollisionShape2D().apply { shape = RectangleShape2D().apply { this.size = size } }
    body.addChild(shapeNode)
    return body
}

private fun makeArea(size: Vec2, position: Vec2 = Vec2.ZERO): Area2D {
    val area = Area2D().apply { transform = Transform(position = position) }
    val shapeNode = CollisionShape2D().apply { shape = RectangleShape2D().apply { this.size = size } }
    area.addChild(shapeNode)
    return area
}

private open class RecordingBody(
    size: Vec2,
    position: Vec2 = Vec2.ZERO,
) : StaticBody2D() {

    val bodyEnters: MutableList<PhysicsBody2D> = mutableListOf()
    val bodyExits: MutableList<PhysicsBody2D> = mutableListOf()
    val areaEnters: MutableList<Area2D> = mutableListOf()

    init {
        transform = Transform(position = position)
        addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { this.size = size } })
    }

    override fun onBodyEntered(body: PhysicsBody2D) { bodyEnters += body }
    override fun onBodyExited(body: PhysicsBody2D) { bodyExits += body }
    override fun onAreaEntered(area: Area2D) { areaEnters += area }
}

private class RecordingArea(
    size: Vec2,
    position: Vec2 = Vec2.ZERO,
) : Area2D() {

    val bodyEnters: MutableList<PhysicsBody2D> = mutableListOf()

    init {
        transform = Transform(position = position)
        addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { this.size = size } })
    }

    override fun onBodyEntered(body: PhysicsBody2D) { bodyEnters += body }
}

class PhysicsSystemTest {

    @Test
    fun `non-overlapping objects do not fire enter`() {
        val root = Node()
        val a = RecordingBody(Vec2(10f, 10f), position = Vec2(0f, 0f))
        val b = RecordingBody(Vec2(10f, 10f), position = Vec2(100f, 100f))
        root.addChild(a); root.addChild(b)
        val tree = SceneTree(root)
        tree.start()
        PhysicsSystem().step(tree)
        assertEquals(0, a.bodyEnters.size)
        assertEquals(0, b.bodyEnters.size)
    }

    @Test
    fun `overlapping body pair fires bodyEntered exactly once on each`() {
        val root = Node()
        val a = RecordingBody(Vec2(10f, 10f), position = Vec2(0f, 0f))
        val b = RecordingBody(Vec2(10f, 10f), position = Vec2(5f, 5f))
        root.addChild(a); root.addChild(b)
        val tree = SceneTree(root)
        tree.start()
        PhysicsSystem().step(tree)
        assertEquals(listOf<PhysicsBody2D>(b), a.bodyEnters)
        assertEquals(listOf<PhysicsBody2D>(a), b.bodyEnters)
    }

    @Test
    fun `Area-vs-Body dispatches across both APIs`() {
        val root = Node()
        val area = RecordingArea(Vec2(20f, 20f), position = Vec2(0f, 0f))
        val body = RecordingBody(Vec2(10f, 10f), position = Vec2(5f, 5f))
        root.addChild(area); root.addChild(body)
        val tree = SceneTree(root)
        tree.start()
        PhysicsSystem().step(tree)
        assertEquals(listOf<PhysicsBody2D>(body), area.bodyEnters)
        assertEquals(listOf<Area2D>(area), body.areaEnters)
    }

    @Test
    fun `sustained overlap does not re-fire enter`() {
        val root = Node()
        val a = RecordingBody(Vec2(10f, 10f), position = Vec2(0f, 0f))
        val b = RecordingBody(Vec2(10f, 10f), position = Vec2(5f, 5f))
        root.addChild(a); root.addChild(b)
        val tree = SceneTree(root)
        tree.start()
        val phys = PhysicsSystem()
        phys.step(tree)
        phys.step(tree)
        assertEquals(1, a.bodyEnters.size)
        assertEquals(1, b.bodyEnters.size)
    }

    @Test
    fun `exit fires when overlap ends`() {
        val root = Node()
        val a = RecordingBody(Vec2(10f, 10f), position = Vec2(0f, 0f))
        val b = RecordingBody(Vec2(10f, 10f), position = Vec2(5f, 5f))
        root.addChild(a); root.addChild(b)
        val tree = SceneTree(root)
        tree.start()
        val phys = PhysicsSystem()
        phys.step(tree)
        b.transform = Transform(position = Vec2(100f, 100f))
        phys.step(tree)
        assertEquals(1, a.bodyExits.size)
        assertEquals(b, a.bodyExits.single())
    }

    @Test
    fun `detached node does not generate exit event next step`() {
        val root = Node()
        val a = RecordingBody(Vec2(10f, 10f), position = Vec2(0f, 0f))
        val b = RecordingBody(Vec2(10f, 10f), position = Vec2(5f, 5f))
        root.addChild(a); root.addChild(b)
        val tree = SceneTree(root)
        tree.start()
        val phys = PhysicsSystem()
        phys.step(tree)
        root.removeChild(b)
        phys.step(tree)
        assertEquals(0, a.bodyExits.size)
    }

    @Test
    fun `circle shapes overlap is exact (rejects AABB-only matches)`() {
        val root = Node()
        val a = StaticBody2D().apply {
            transform = Transform(position = Vec2(0f, 0f))
            addChild(CollisionShape2D().apply { shape = CircleShape2D().apply { radius = 5f } })
        }
        // Position both circles so their AABBs overlap (diagonally adjacent)
        // but the circles themselves do not (distance > sum of radii).
        val b = RecordingBody(Vec2(0f, 0f), position = Vec2(8f, 8f)).also {
            it.children.toList().forEach { c -> it.removeChild(c) }
            it.addChild(CollisionShape2D().apply { shape = CircleShape2D().apply { radius = 5f } })
        }
        root.addChild(a); root.addChild(b)
        val tree = SceneTree(root)
        tree.start()
        PhysicsSystem().step(tree)
        assertEquals(0, b.bodyEnters.size)
    }

    @Test
    fun `multiple shapes per object — overlap is union`() {
        val root = Node()
        val a = StaticBody2D().apply { transform = Transform(position = Vec2(0f, 0f)) }
        a.addChild(CollisionShape2D().apply {
            transform = Transform(position = Vec2(0f, 0f))
            shape = RectangleShape2D().apply { size = Vec2(10f, 10f) }
        })
        a.addChild(CollisionShape2D().apply {
            transform = Transform(position = Vec2(100f, 0f))
            shape = RectangleShape2D().apply { size = Vec2(10f, 10f) }
        })
        val b = RecordingBody(Vec2(5f, 5f), position = Vec2(102f, 2f))
        root.addChild(a); root.addChild(b)
        val tree = SceneTree(root)
        tree.start()
        PhysicsSystem().step(tree)
        assertEquals(1, b.bodyEnters.size, "exactly one enter, despite two shapes on A")
    }

    @Test
    fun `disabled object is ignored`() {
        val root = Node()
        val a = RecordingBody(Vec2(10f, 10f), position = Vec2(0f, 0f))
        val b = RecordingBody(Vec2(10f, 10f), position = Vec2(5f, 5f)).apply { disabled = true }
        root.addChild(a); root.addChild(b)
        val tree = SceneTree(root)
        tree.start()
        PhysicsSystem().step(tree)
        assertEquals(0, a.bodyEnters.size)
    }

    @Test
    fun `RectangleShape2D bounds reflect transform scale`() {
        val shape = RectangleShape2D().apply { size = Vec2(10f, 20f) }
        val b = shape.bounds(
            world = Transform(position = Vec2(50f, 50f), scale = Vec2(2f, 2f), rotation = 0f),
            localOffset = Vec2.ZERO,
        )
        assertEquals(Vec2(50f, 50f), b.origin)
        assertEquals(Vec2(20f, 40f), b.size)
    }

    @Test
    fun `CircleShape2D bounds form a square envelope`() {
        val shape = CircleShape2D().apply { radius = 10f }
        val b = shape.bounds(
            world = Transform(position = Vec2(0f, 0f)),
            localOffset = Vec2.ZERO,
        )
        assertEquals(20f, b.size.x)
        assertEquals(20f, b.size.y)
    }

    @Test
    fun `CollisionShape2D worldBounds returns null when disabled`() {
        val s = CollisionShape2D().apply {
            shape = RectangleShape2D().apply { size = Vec2(10f, 10f) }
            disabled = true
        }
        assertEquals(null, s.worldBounds())
    }

    @Test
    fun `Pair tracking ignores swap order`() {
        val root = Node()
        val a = RecordingBody(Vec2(10f, 10f), position = Vec2(0f, 0f))
        val b = RecordingBody(Vec2(10f, 10f), position = Vec2(5f, 5f))
        root.addChild(a); root.addChild(b)
        val tree = SceneTree(root)
        tree.start()
        val phys = PhysicsSystem()
        phys.step(tree)
        phys.step(tree) // second step: no double enter regardless of internal order
        assertEquals(1, a.bodyEnters.size)
        assertTrue(a.bodyExits.isEmpty())
    }

    @Test
    fun `three-body pile-up emits all chained entered events in one step`() {
        // A overlaps B initially; C is far. A.onBodyEntered(B) reacts by
        // mutating B's transform so that B now overlaps C. The convergence
        // loop must detect the new (B, C) overlap *within the same step*.
        val root = Node()
        val c = RecordingBody(Vec2(10f, 10f), position = Vec2(50f, 0f))
        val b = RecordingBody(Vec2(10f, 10f), position = Vec2(5f, 0f))
        val a = object : RecordingBody(Vec2(10f, 10f), position = Vec2(0f, 0f)) {
            override fun onBodyEntered(body: PhysicsBody2D) {
                super.onBodyEntered(body)
                if (body === b) {
                    // Slide B over so it overlaps C.
                    b.transform = Transform(position = Vec2(48f, 0f))
                }
            }
        }
        root.addChild(a); root.addChild(b); root.addChild(c)
        val tree = SceneTree(root)
        tree.start()

        PhysicsSystem().step(tree)

        // First overlap detected and dispatched.
        assertEquals(listOf<PhysicsBody2D>(b), a.bodyEnters)
        // Cascading overlap dispatched in the same step.
        assertTrue(c in b.bodyEnters, "B should have received bodyEntered(C) within the same step")
        assertTrue(b in c.bodyEnters, "C should have received bodyEntered(B) within the same step")
    }

    @Test
    fun `fail-safe cap logs warning when oscillating without converging`() {
        // Two bodies whose enter handler pushes them apart and exit handler
        // pulls them back together — they oscillate forever in principle.
        // The cap must kick in, the warning must be logged, and step() must
        // return without exception.
        val root = Node()
        lateinit var a: OscillatingBody
        lateinit var b: OscillatingBody
        a = OscillatingBody(
            size = Vec2(10f, 10f),
            position = Vec2(0f, 0f),
            onEnter = { _, self -> self.transform = Transform(position = Vec2(100f, 0f)) },
            onExit = { _, self -> self.transform = Transform(position = Vec2(0f, 0f)) },
        )
        b = OscillatingBody(
            size = Vec2(10f, 10f),
            position = Vec2(5f, 0f),
            onEnter = { _, _ -> },
            onExit = { _, _ -> },
        )
        root.addChild(a); root.addChild(b)
        val tree = SceneTree(root)
        tree.start()

        val warnings = mutableListOf<String>()
        Log.sink = LogSink { _, level, tag, message ->
            if (level == LogLevel.Warn && tag == "PhysicsSystem") warnings += message
        }

        PhysicsSystem().step(tree)

        assertTrue(
            warnings.any { it.contains("MAX_RESOLUTION_ITERATIONS") },
            "expected PhysicsSystem warning naming MAX_RESOLUTION_ITERATIONS, got $warnings"
        )
    }

    @AfterTest
    fun restoreLogSink() {
        Log.sink = ConsoleLogSink
    }

    private class OscillatingBody(
        size: Vec2,
        position: Vec2,
        private val onEnter: (PhysicsBody2D, OscillatingBody) -> Unit,
        private val onExit: (PhysicsBody2D, OscillatingBody) -> Unit,
    ) : StaticBody2D() {

        init {
            transform = Transform(position = position)
            addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { this.size = size } })
        }

        override fun onBodyEntered(body: PhysicsBody2D) { onEnter(body, this) }
        override fun onBodyExited(body: PhysicsBody2D) { onExit(body, this) }
    }

    @Test
    fun `helper makeBody and makeArea sanity`() {
        // Smoke test for the small helpers used by other tests in the module.
        val body = makeBody(Vec2(4f, 4f), position = Vec2(1f, 1f))
        val area = makeArea(Vec2(8f, 8f), position = Vec2(0f, 0f))
        val root = Node().apply { addChild(body); addChild(area) }
        val tree = SceneTree(root)
        tree.start()
        PhysicsSystem().step(tree) // overlap area+body, no crash
    }
}
