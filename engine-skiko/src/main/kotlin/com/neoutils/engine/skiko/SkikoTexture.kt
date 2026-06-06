package com.neoutils.engine.skiko

import com.neoutils.engine.render.Texture
import org.jetbrains.skia.Image

/**
 * Skiko-backed [Texture]: a decoded Skia [Image]. The concrete type lives here
 * in `:engine-skiko`, never in `:engine` (invariant #2). [SkikoRenderer.drawImage]
 * casts a [Texture] back to this to reach the underlying [image].
 */
class SkikoTexture(val image: Image) : Texture {
    override val width: Int get() = image.width
    override val height: Int get() = image.height
}
