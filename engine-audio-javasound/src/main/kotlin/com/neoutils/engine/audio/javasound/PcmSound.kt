package com.neoutils.engine.audio.javasound

import com.neoutils.engine.audio.Sound
import javax.sound.sampled.AudioFormat

/**
 * Concrete [Sound] for the Java Sound backend: the fully decoded PCM payload
 * plus its [AudioFormat], materialized once by [JavaSoundAudio.load]. The
 * bytes are shared (read-only) across every [JavaSoundAudio.play] of this
 * handle — only the output line is per-voice — so overlapping plays never
 * re-decode. Internal to the module; the SPI exposes only the opaque [Sound].
 */
internal class PcmSound(
    val format: AudioFormat,
    val pcm: ByteArray,
) : Sound
