package com.neoutils.engine.audio

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards invariant #2 for the audio seam: `:engine` declares only the pure
 * `AudioBackend`/`Sound` interfaces — no native audio binding (`javax.sound`,
 * `org.lwjgl.openal`) and no dependency on the concrete backend module.
 */
class AudioInvariantTest {

    @Test
    fun `engine main sources import no native audio bindings`() {
        val root = locateEngineMain()
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .map { it.trim() }
                    .filter {
                        it.startsWith("import javax.sound") ||
                            it.startsWith("import org.lwjgl.openal")
                    }
                    .map { "${file.name}: $it" }
            }
            .toList()
        assertTrue(
            offenders.isEmpty(),
            ":engine must not import javax.sound/org.lwjgl.openal; found:\n${offenders.joinToString("\n")}",
        )
    }

    @Test
    fun `engine build does not depend on the audio backend module`() {
        val build = locate(
            "engine/build.gradle.kts",
            "build.gradle.kts",
            "../engine/build.gradle.kts",
        )
        val text = build.readText()
        assertTrue(
            !text.contains("engine-audio-javasound") && !text.contains("engineAudioJavasound"),
            ":engine/build.gradle.kts must not depend on :engine-audio-javasound",
        )
    }

    private fun locateEngineMain(): File = locateDir(
        "engine/src/main/kotlin",
        "src/main/kotlin",
        "../engine/src/main/kotlin",
    )

    private fun locate(vararg paths: String): File {
        for (p in paths) {
            val f = File(p)
            if (f.exists() && f.isFile) return f
        }
        error("file not found in any of: ${paths.toList()} (cwd=${File(".").absolutePath})")
    }

    private fun locateDir(vararg paths: String): File {
        for (p in paths) {
            val f = File(p)
            if (f.exists() && f.isDirectory) return f
        }
        error("dir not found in any of: ${paths.toList()} (cwd=${File(".").absolutePath})")
    }
}
