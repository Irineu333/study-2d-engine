package com.neoutils.engine.physics

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

private fun makeArea(size: Vec2, position: Vec2 = Vec2.ZERO): Area2D {
    val area = Area2D().apply { transform = Transform(position = position) }
    area.addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { this.size = size } })
    return area
}

private fun makeBody(size: Vec2, position: Vec2 = Vec2.ZERO): StaticBody2D {
    val body = StaticBody2D().apply { transform = Transform(position = position) }
    body.addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { this.size = size } })
    return body
}

private fun mount(root: Node): SceneTree {
    val tree = SceneTree(root)
    val system = PhysicsSystem()
    tree.physicsSystem = system
    tree.start()
    system.step(tree)
    return tree
}

class OverlapQueryTest {

    @Test
    fun `getOverlappingAreas returns the peer area for an overlapping pair`() {
        val root = Node()
        val a = makeArea(Vec2(10f, 10f), Vec2(0f, 0f))
        val b = makeArea(Vec2(10f, 10f), Vec2(5f, 5f))
        root.addChild(a); root.addChild(b)
        mount(root)
        assertEquals(listOf<Area2D>(b), a.getOverlappingAreas())
        assertEquals(listOf<Area2D>(a), b.getOverlappingAreas())
    }

    @Test
    fun `getOverlappingBodies discriminates PhysicsBody2D from Area2D`() {
        val root = Node()
        val area = makeArea(Vec2(20f, 20f), Vec2(0f, 0f))
        val body = makeBody(Vec2(10f, 10f), Vec2(5f, 5f))
        val anotherArea = makeArea(Vec2(10f, 10f), Vec2(8f, 8f))
        root.addChild(area); root.addChild(body); root.addChild(anotherArea)
        mount(root)
        assertEquals(listOf<PhysicsBody2D>(body), area.getOverlappingBodies())
        assertContains(area.getOverlappingAreas(), anotherArea)
        assertEquals(1, area.getOverlappingAreas().size)
    }

    @Test
    fun `getOverlappingAreas on detached area returns emptyList`() {
        val detached = makeArea(Vec2(10f, 10f), Vec2(0f, 0f))
        assertEquals(emptyList<Area2D>(), detached.getOverlappingAreas())
        assertEquals(emptyList<PhysicsBody2D>(), detached.getOverlappingBodies())
    }

    @Test
    fun `query inside _on_area_entered observes the peer (post-dispatch state)`() {
        class Recording(size: Vec2, position: Vec2) : Area2D() {
            init {
                transform = Transform(position = position)
                addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { this.size = size } })
            }
            var observedDuringEnter: List<Area2D> = emptyList()
            override fun onAreaEntered(area: Area2D) {
                observedDuringEnter = getOverlappingAreas()
            }
        }
        val root = Node()
        val a = Recording(Vec2(10f, 10f), Vec2(0f, 0f))
        val b = makeArea(Vec2(10f, 10f), Vec2(5f, 5f))
        root.addChild(a); root.addChild(b)
        mount(root)
        assertEquals(listOf<Area2D>(b), a.observedDuringEnter)
    }
}
