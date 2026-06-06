package com.neoutils.engine.lwjgl

import com.neoutils.engine.render.Texture

/**
 * LWJGL-backed [Texture]: a NanoVG image [handle] plus its pixel dimensions.
 * The handle is owned by the [LwjglTextureBackend] that created it and freed via
 * `nvgDeleteImage` on dispose. [LwjglRenderer.drawImage] casts a [Texture] back
 * to this to reach the [handle].
 */
class LwjglTexture(
    val handle: Int,
    override val width: Int,
    override val height: Int,
) : Texture
