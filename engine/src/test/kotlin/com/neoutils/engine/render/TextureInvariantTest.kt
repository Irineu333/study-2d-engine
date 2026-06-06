package com.neoutils.engine.render

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards invariant #2 for the texture seam: `:engine` declares only the pure
 * [TextureBackend]/[Texture] interfaces — no native graphics binding
 * (`org.jetbrains.skia`, `org.lwjgl`) — and the [Texture] handle exposes
 * exactly `width`/`height`, nothing that could leak a backend detail.
 */
class TextureInvariantTest {

    @Test
    fun `engine main sources import no native graphics bindings`() {
        val root = locateEngineMain()
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .map { it.trim() }
                    .filter {
                        it.startsWith("import org.jetbrains.skia") ||
                            it.startsWith("import org.jetbrains.skiko") ||
                            it.startsWith("import org.lwjgl")
                    }
                    .map { "${file.name}: $it" }
            }
            .toList()
        assertTrue(
            offenders.isEmpty(),
            ":engine must not import org.jetbrains.skia/skiko or org.lwjgl; found:\n${offenders.joinToString("\n")}",
        )
    }

    @Test
    fun `Texture exposes only width and height`() {
        val members = Texture::class.members.map { it.name }.toSet()
        // Beyond width/height, only the universal Any members remain.
        assertEquals(setOf("width", "height", "equals", "hashCode", "toString"), members)
    }

    private fun locateEngineMain(): File {
        for (p in listOf("engine/src/main/kotlin", "src/main/kotlin", "../engine/src/main/kotlin")) {
            val f = File(p)
            if (f.exists() && f.isDirectory) return f
        }
        error("engine main source dir not found (cwd=${File(".").absolutePath})")
    }
}
