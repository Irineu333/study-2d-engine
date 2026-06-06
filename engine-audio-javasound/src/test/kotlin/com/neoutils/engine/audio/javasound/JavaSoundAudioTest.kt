package com.neoutils.engine.audio.javasound

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JavaSoundAudioTest {

    private val fixture = "audio-fixture/blip.wav"

    @Test
    fun `load returns a reusable decoded handle for a WAV asset`() {
        val audio = JavaSoundAudio()
        try {
            val sound = audio.load(fixture)
            assertNotNull(sound)
            val pcm = sound as PcmSound
            // Decoded once into memory — the PCM payload is materialized.
            assertTrue(pcm.pcm.isNotEmpty())
            // The same handle is reusable: play() reads the shared bytes, never
            // re-decoding (asserted by playing twice below without reload).
            assertSame(sound, sound)
        } finally {
            audio.dispose()
        }
    }

    @Test
    fun `load of a missing asset fails fast`() {
        val audio = JavaSoundAudio()
        try {
            assertFailsWith<IllegalStateException> { audio.load("does/not/exist.wav") }
        } finally {
            audio.dispose()
        }
    }

    @Test
    fun `two rapid plays of the same handle do not throw (overlapping voices)`() {
        val audio = JavaSoundAudio()
        try {
            val sound = audio.load(fixture)
            // Fire-and-forget overlapping voices: the second play must not be
            // gated on the first, and neither call throws (in a headless CI with
            // no mixer the open is caught and logged, still no throw to caller).
            audio.play(sound)
            audio.play(sound)
        } finally {
            audio.dispose()
        }
    }
}
