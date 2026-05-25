package com.neoutils.engine.bundle.python

import com.neoutils.engine.bundle.script.BundleSource
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Timer
import com.neoutils.engine.serialization.NodeRegistry
import com.neoutils.engine.tree.SceneTree
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class PythonSignalBridgeTest {

    private lateinit var host: PythonScriptHost

    @Before
    fun setUp() {
        NodeRegistry.clear()
        NodeRegistry.registerEngineTypes()
        host = PythonScriptHost.create()
    }

    @After
    fun tearDown() {
        NodeRegistry.clear()
    }

    private fun loadScript(path: String, source: String) = host.load(path, object : BundleSource {
        override fun read(p: String) = source
        override fun exists(p: String) = p == path
    })

    @Test
    fun `python connects to Timer timeout and handler is invoked with no args`() {
        val script = loadScript(
            "parent.py",
            """
            # extends Node
            _fired_count: int = 0

            def _ready(self):
                self._fired_count = 0
                timer = script_of(self._node.findChild("MoveTimer"))
                def on_tick():
                    self._fired_count += 1
                self._handler = on_tick
                timer.timeout.connect(on_tick)
            """.trimIndent()
        )
        val root = Node().apply { name = "Root" }
        val timer = Timer().apply { name = "MoveTimer"; waitTime = 0.1f; autostart = true }
        root.addChild(timer)
        val instance = host.attach(root, script)
        instance.onEnter()

        val tree = SceneTree(root)
        tree.start()

        repeat(21) { tree.physicsProcess(1f / 60f) } // ~0.35s
        val count = instance.currentValue("_fired_count") as? Int
            ?: error("Could not read _fired_count: ${instance.currentValue("_fired_count")}")
        assertTrue(count in 2..4, "expected ~3 ticks, got $count")
    }

    @Test
    fun `python disconnect removes the handler`() {
        val script = loadScript(
            "parent.py",
            """
            # extends Node
            _count: int = 0

            def _ready(self):
                self._count = 0
                timer = script_of(self._node.findChild("T"))
                self._proxy = timer.timeout
                def h():
                    self._count += 1
                    if self._count >= 2:
                        self._proxy.disconnect(self._handler)
                self._handler = h
                self._proxy.connect(h)
            """.trimIndent()
        )
        val root = Node().apply { name = "Root" }
        val timer = Timer().apply { name = "T"; waitTime = 0.05f; autostart = true }
        root.addChild(timer)
        val instance = host.attach(root, script)
        instance.onEnter()

        val tree = SceneTree(root)
        tree.start()

        repeat(120) { tree.physicsProcess(1f / 60f) } // 2s — plenty of room past 2 ticks
        val finalCount = instance.currentValue("_count") as? Int ?: -1
        assertEquals(2, finalCount, "handler should fire exactly twice before disconnecting itself")
    }

    @Test
    fun `python handler exception propagates out of emit`() {
        val script = loadScript(
            "parent.py",
            """
            # extends Node

            def _ready(self):
                timer = script_of(self._node.findChild("T"))
                def boom():
                    raise ValueError("boom")
                self._handler = boom
                timer.timeout.connect(boom)
            """.trimIndent()
        )
        val root = Node().apply { name = "Root" }
        val timer = Timer().apply { name = "T"; waitTime = 0.01f; autostart = true }
        root.addChild(timer)
        val instance = host.attach(root, script)
        instance.onEnter()

        val tree = SceneTree(root)
        tree.start()

        val ex = assertFails {
            repeat(20) { tree.physicsProcess(1f / 60f) }
        }
        val chain = generateSequence<Throwable>(ex) { it.cause }.toList()
        val msgs = chain.mapNotNull { it.message }.joinToString(" | ")
        assertTrue(msgs.contains("boom"), "expected 'boom' in error chain: $msgs")
    }
}
