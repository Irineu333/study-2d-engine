package com.neoutils.engine.skiko

import com.neoutils.engine.render.Texture
import com.neoutils.engine.render.TextureBackend
import org.jetbrains.skia.Image

/**
 * [TextureBackend] over Skia's [Image] decoder. Resolves assets from the
 * classpath, decodes each PNG once via [Image.makeFromEncoded], and caches the
 * resulting handle by path so repeated `load` of the same asset shares one
 * decode. [dispose] closes every cached [Image].
 */
class SkikoTextureBackend : TextureBackend {

    private val cache = LinkedHashMap<String, SkikoTexture>()
    private var disposed = false

    override fun load(path: String): Texture {
        check(!disposed) { "SkikoTextureBackend.load called after dispose()" }
        cache[path]?.let { return it }
        val bytes = openResource(path)?.use { it.readBytes() }
            ?: error("Texture asset not found on classpath: \"$path\"")
        val image = try {
            Image.makeFromEncoded(bytes)
        } catch (e: Exception) {
            // Fail fast with the offending path — never return a silent handle.
            throw IllegalStateException("Failed to decode texture asset \"$path\": ${e.message}", e)
        }
        val texture = SkikoTexture(image)
        cache[path] = texture
        return texture
    }

    override fun dispose() {
        disposed = true
        for (texture in cache.values) runCatching { texture.image.close() }
        cache.clear()
    }

    private fun openResource(path: String) =
        Thread.currentThread().contextClassLoader?.getResourceAsStream(path)
            ?: javaClass.classLoader?.getResourceAsStream(path)
}
