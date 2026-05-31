package com.neoutils.engine.serialization

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.ColorRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InspectPropertiesTest {

    @Test
    fun `enumerates inspect properties with current values`() {
        val node = ColorRect().apply {
            name = "Box"
            size = Vec2(40f, 20f)
            position = Vec2(100f, 50f)
        }
        val entries = inspectProperties(node)
        val byName = entries.associate { it.displayName to it.value }

        // ColorRect's own @Inspect leaves, with the values just set.
        assertEquals(Vec2(40f, 20f), byName["size"])
        // The @Inspect `transform` carries the updated position.
        val transform = byName["transform"]
        assertTrue(transform is com.neoutils.engine.math.Transform)
        assertEquals(Vec2(100f, 50f), (transform).position)
        // Inherited @Inspect `name`.
        assertEquals("Box", byName["name"])
    }

    @Test
    fun `excludes transient runtime state`() {
        val node = ColorRect()
        node.world() // populates the @Transient cachedWorld
        val names = inspectProperties(node).map { it.displayName }
        // cachedWorld / parent / tree are @Transient and must never surface.
        assertTrue("cachedWorld" !in names)
        assertTrue("parent" !in names)
        assertTrue("tree" !in names)
    }
}
