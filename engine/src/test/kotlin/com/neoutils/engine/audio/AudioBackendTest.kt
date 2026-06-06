package com.neoutils.engine.audio

import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AudioBackendTest {

    private class FakeSound : Sound

    private class FakeAudio : AudioBackend {
        var disposeCount = 0
        var playCount = 0
        override fun load(path: String): Sound = FakeSound()
        override fun play(sound: Sound, volume: Float) {
            playCount++
        }
        override fun dispose() {
            disposeCount++
        }
    }

    @Test
    fun `SceneTree defaults audio to null`() {
        assertNull(SceneTree(Node()).audio)
    }

    @Test
    fun `null audio backend play is a graceful no-op`() {
        val tree = SceneTree(Node())
        // The canonical call site used by nodes/scripts: must not throw.
        tree.audio?.play(FakeSound())
    }

    @Test
    fun `stop disposes the audio backend exactly once`() {
        val audio = FakeAudio()
        val tree = SceneTree(Node())
        tree.audio = audio
        tree.start()
        tree.stop()
        assertEquals(1, audio.disposeCount)
        // A second stop must not dispose again (handle was cleared).
        tree.stop()
        assertEquals(1, audio.disposeCount)
    }
}
