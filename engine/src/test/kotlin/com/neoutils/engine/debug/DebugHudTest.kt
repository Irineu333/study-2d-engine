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
        // Built-ins are 10 total; HUD excludes itself → 9 rows (FPS, Colliders,
        // Momentum, Log, Debug Draw, Shapes, Velocity, Contacts, Time).
        val buttons = panel.children.filterIsInstance<Button>()
        assertEquals(9, buttons.size)
        val labels = buttons.map { it.text }
        assertEquals("[ ] FPS", labels[0])
        assertEquals("[ ] Colliders", labels[1])
        assertEquals("[ ] Momentum", labels[2])
        assertEquals("[ ] Log", labels[3])
        assertEquals("[ ] Debug Draw", labels[4])
        assertEquals("[ ] Shapes", labels[5])
        assertEquals("[ ] Velocity", labels[6])
        assertEquals("[ ] Contacts", labels[7])
        assertEquals("[ ] Time", labels[8])
    }

    @Test
    fun `clicking a row flips the target widgets enabled and refreshes label`() {
        val tree = SceneTree(Node()).also { it.start() }
        tree.debug.hud.enabled = true
        tree.process(0.016f)
        tree.applyPending()
        val panel = tree.debug.hud.children.filterIsInstance<Panel>().single()
        val fpsButton = panel.children.filterIsInstance<Button>().first()
        assertEquals("[ ] FPS", fpsButton.text)
        // Emit the press signal directly — equivalent to a hit-tested click.
        fpsButton.pressed.emit(Unit)
        assertTrue(tree.debug.fps.enabled)
        // Next process refreshes the label.
        tree.process(0.016f)
        tree.applyPending()
        assertEquals("[x] FPS", fpsButton.text)
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
