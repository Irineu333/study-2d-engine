package com.neoutils.engine.debug

import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Panel
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DebugHudTest {

    @Test
    fun `HUD disabled has no Panel child`() {
        val tree = SceneTree(Node()).also { it.start() }
        tree.debug.hud.enabled = false
        tree.process(0.016f)
        tree.applyPending()
        assertEquals(0, tree.debug.hud.children.filterIsInstance<Panel>().size)
    }

    @Test
    fun `enabling HUD builds Panel with one Button per non-HUD widget`() {
        val tree = SceneTree(Node()).also { it.start() }
        tree.debug.hud.enabled = true
        tree.process(0.016f)
        tree.applyPending()
        val panel = tree.debug.hud.children.filterIsInstance<Panel>().single()
        // Built-ins listed in `widgets` are 9; HUD excludes itself → 8 rows
        // (Colliders, Log, Debug Draw, Velocity, Contacts, Time, Profiler,
        // Picker). The SelectionGizmo is the picker's world-space arm and has
        // no row of its own.
        val buttons = panel.children.filterIsInstance<Button>()
        assertEquals(8, buttons.size)
        val labels = buttons.map { it.text }
        assertEquals("[ ] Colliders", labels[0])
        assertEquals("[ ] Log", labels[1])
        assertEquals("[ ] Debug Draw", labels[2])
        assertEquals("[ ] Velocity", labels[3])
        assertEquals("[ ] Contacts", labels[4])
        assertEquals("[ ] Time", labels[5])
        assertEquals("[ ] Profiler", labels[6])
        assertEquals("[ ] Picker", labels[7])
        assertTrue(labels.none { it.contains("Selection") })
    }

    @Test
    fun `clicking a row flips the target widgets enabled and refreshes label`() {
        val tree = SceneTree(Node()).also { it.start() }
        tree.debug.hud.enabled = true
        tree.process(0.016f)
        tree.applyPending()
        val panel = tree.debug.hud.children.filterIsInstance<Panel>().single()
        val collidersButton = panel.children.filterIsInstance<Button>().first()
        assertEquals("[ ] Colliders", collidersButton.text)
        // Emit the press signal directly — equivalent to a hit-tested click.
        collidersButton.pressed.emit(Unit)
        assertTrue(tree.debug.colliders.enabled)
        // Next process refreshes the label.
        tree.process(0.016f)
        tree.applyPending()
        assertEquals("[x] Colliders", collidersButton.text)
    }

    @Test
    fun `disabling HUD removes the Panel`() {
        val tree = SceneTree(Node()).also { it.start() }
        tree.debug.hud.enabled = true
        tree.process(0.016f)
        tree.applyPending()
        assertEquals(1, tree.debug.hud.children.filterIsInstance<Panel>().size)
        tree.debug.hud.enabled = false
        tree.process(0.016f)
        tree.applyPending()
        assertEquals(0, tree.debug.hud.children.filterIsInstance<Panel>().size)
    }
}
