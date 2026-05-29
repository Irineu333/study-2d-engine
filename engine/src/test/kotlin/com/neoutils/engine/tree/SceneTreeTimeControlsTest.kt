package com.neoutils.engine.tree

import com.neoutils.engine.scene.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SceneTreeTimeControlsTest {

    @Test
    fun `defaults are normal speed, not paused, no pending step`() {
        val tree = SceneTree(Node())
        assertEquals(1f, tree.timeScale)
        assertFalse(tree.paused)
        assertFalse(tree.hasPendingStep)
    }

    @Test
    fun `timeScale is coerced non-negative`() {
        val tree = SceneTree(Node())
        tree.timeScale = -2f
        assertEquals(0f, tree.timeScale)
        tree.timeScale = 0.5f
        assertEquals(0.5f, tree.timeScale)
    }

    @Test
    fun `requestStep sets a single-use pending flag consumed once`() {
        val tree = SceneTree(Node())
        tree.requestStep()
        assertTrue(tree.hasPendingStep)
        assertTrue(tree.consumePendingStep())
        assertFalse(tree.hasPendingStep, "consuming clears the flag")
        assertFalse(tree.consumePendingStep(), "a second consume returns false")
    }
}
