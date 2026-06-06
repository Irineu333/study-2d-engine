package com.neoutils.engine.render

/**
 * Handle to a decoded bitmap, produced by [TextureBackend.load] and drawn via
 * [Renderer.drawImage]. Opaque as to the backend: the concrete type (a Skia
 * `Image`, a NanoVG image handle) lives in the render module, never in
 * `:engine` (invariant #2).
 *
 * Unlike the audio [com.neoutils.engine.audio.Sound] marker, a `Texture`
 * **exposes its dimensions** — sprite/tilemap logic needs `width`/`height` to
 * compute source rects — but **nothing else**: no bytes, no pixels, no native
 * handle. Any query beyond dimension is added as a [TextureBackend] method
 * taking a `Texture`, never as a member here (same discipline as `Sound`).
 */
interface Texture {

    /** Bitmap width in pixels. */
    val width: Int

    /** Bitmap height in pixels. */
    val height: Int
}
