package com.neoutils.engine.audio.javasound

import com.neoutils.engine.audio.AudioBackend
import com.neoutils.engine.audio.Sound
import com.neoutils.engine.dx.Log
import java.io.BufferedInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.LineEvent
import kotlin.math.ln

/**
 * JDK-pure [AudioBackend] over `javax.sound.sampled`. Host-agnostic: the same
 * instance serves `SkikoHost` and `LwjglHost` (audio and render are orthogonal
 * axes). WAV/PCM only in v1 — the JDK's `AudioSystem` reads it natively, so no
 * third-party decoder is pulled in (invariant #2 stays clean).
 *
 * Each [play] opens a fresh [Clip] from the shared decoded PCM so overlapping
 * voices of the same handle never cut each other (a single Clip can't play
 * itself twice — re-`start()` restarts it). Lines self-close on
 * [LineEvent.Type.STOP]; [dispose] is the safety net that closes any still live.
 */
class JavaSoundAudio : AudioBackend {

    // Live output lines, tracked so dispose() can close voices still playing.
    // Synchronized: play() runs on the gameplay thread while the STOP listener
    // fires on a javax.sound daemon thread.
    private val liveClips = LinkedHashSet<Clip>()

    @Volatile private var disposed = false

    override fun load(path: String): Sound {
        check(!disposed) { "JavaSoundAudio.load called after dispose()" }
        val stream = openResource(path)
            ?: error("Audio asset not found on classpath: \"$path\"")
        try {
            // AudioSystem needs mark/reset to sniff the container header.
            val audioInput = AudioSystem.getAudioInputStream(BufferedInputStream(stream))
            audioInput.use { input ->
                val pcm = input.readAllBytes()
                if (pcm.isEmpty()) error("Audio asset decoded to zero PCM bytes: \"$path\"")
                return PcmSound(input.format, pcm)
            }
        } catch (e: Exception) {
            // Fail fast with the offending path — never return a silent handle.
            throw IllegalStateException("Failed to load audio asset \"$path\": ${e.message}", e)
        } finally {
            runCatching { stream.close() }
        }
    }

    override fun play(sound: Sound, volume: Float) {
        if (disposed) return
        val pcm = sound as? PcmSound
            ?: error("play() received a foreign Sound handle: ${sound::class.qualifiedName}")
        try {
            val info = DataLine.Info(Clip::class.java, pcm.format)
            val clip = AudioSystem.getLine(info) as Clip
            clip.open(pcm.format, pcm.pcm, 0, pcm.pcm.size)
            applyGain(clip, volume)
            // Free the line as soon as the voice finishes; also drop our ref so
            // dispose() does not double-close it.
            clip.addLineListener { event ->
                if (event.type == LineEvent.Type.STOP) {
                    synchronized(liveClips) { liveClips.remove(clip) }
                    runCatching { clip.close() }
                }
            }
            synchronized(liveClips) { liveClips.add(clip) }
            clip.start()
        } catch (e: Exception) {
            // A single failed voice must not crash gameplay — log and move on.
            Log.w(TAG, "play() failed: ${e.message}")
        }
    }

    override fun dispose() {
        disposed = true
        val snapshot = synchronized(liveClips) {
            val copy = liveClips.toList()
            liveClips.clear()
            copy
        }
        for (clip in snapshot) {
            runCatching { clip.stop() }
            runCatching { clip.close() }
        }
    }

    private fun applyGain(clip: Clip, volume: Float) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) return
        val control = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
        val v = volume.coerceIn(0f, 1f)
        // Linear amplitude → dB. Silence maps to the control's floor.
        val db = if (v <= 0.0001f) control.minimum else (20.0 * ln(v.toDouble()) / LN10).toFloat()
        control.value = db.coerceIn(control.minimum, control.maximum)
    }

    private fun openResource(path: String) =
        Thread.currentThread().contextClassLoader?.getResourceAsStream(path)
            ?: javaClass.classLoader?.getResourceAsStream(path)

    private companion object {
        const val TAG = "JavaSoundAudio"
        val LN10 = ln(10.0)
    }
}
