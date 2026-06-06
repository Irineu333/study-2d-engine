package com.neoutils.engine.lwjgl

import com.neoutils.engine.render.Texture
import com.neoutils.engine.render.TextureBackend
import org.lwjgl.nanovg.NanoVG
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

/**
 * [TextureBackend] over NanoVG image objects. Resolves assets from the
 * classpath, decodes each via `nvgCreateImageMem` with `NVG_IMAGE_NEAREST`
 * (pixel-art sampling, D5), and caches the handle by path. [dispose] deletes
 * every cached image.
 *
 * NanoVG image creation and deletion touch the GL/NVG context, which lives on
 * the render-loop thread. [load] is therefore only valid while driven by the
 * loop (e.g. from `Sprite2D.onEnter` during a tick) — never off-thread at init.
 */
class LwjglTextureBackend(private val nvg: Long) : TextureBackend {

    private val cache = LinkedHashMap<String, LwjglTexture>()
    private var disposed = false

    override fun load(path: String): Texture {
        check(!disposed) { "LwjglTextureBackend.load called after dispose()" }
        cache[path]?.let { return it }
        val bytes = openResource(path)
            ?: error("Texture asset not found on classpath: \"$path\"")
        // nvgCreateImageMem decodes a copy of the buffer (stb_image), so the
        // direct buffer can be freed right after. The buffer MUST be direct.
        val buffer = MemoryUtil.memAlloc(bytes.size)
        val image = try {
            buffer.put(bytes).flip()
            NanoVG.nvgCreateImageMem(nvg, NanoVG.NVG_IMAGE_NEAREST, buffer)
        } finally {
            MemoryUtil.memFree(buffer)
        }
        check(image != 0) { "Failed to decode texture asset \"$path\" (nvgCreateImageMem returned 0)" }
        val (w, h) = imageSize(image)
        val texture = LwjglTexture(image, w, h)
        cache[path] = texture
        return texture
    }

    override fun dispose() {
        disposed = true
        for (texture in cache.values) runCatching { NanoVG.nvgDeleteImage(nvg, texture.handle) }
        cache.clear()
    }

    private fun imageSize(image: Int): Pair<Int, Int> = MemoryStack.stackPush().use { stack ->
        val w = stack.mallocInt(1)
        val h = stack.mallocInt(1)
        NanoVG.nvgImageSize(nvg, image, w, h)
        w.get(0) to h.get(0)
    }

    private fun openResource(path: String): ByteArray? =
        (Thread.currentThread().contextClassLoader?.getResourceAsStream(path)
            ?: javaClass.classLoader?.getResourceAsStream(path))
            ?.use { it.readBytes() }
}
