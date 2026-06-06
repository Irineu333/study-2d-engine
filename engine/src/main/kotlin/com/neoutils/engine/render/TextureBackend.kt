package com.neoutils.engine.render

/**
 * Platform SPI for loading image assets into reusable [Texture] handles.
 * Server-style service reached through
 * [com.neoutils.engine.tree.SceneTree.textures] — not a `Node`, not a
 * `Component` (invariant #1). Lives in `:engine` as a pure Kotlin interface;
 * concrete backends live in their render modules (`SkikoTextureBackend` in
 * `:engine-skiko`, `LwjglTextureBackend` in `:engine-lwjgl`) so `:engine`
 * never declares a native graphics dependency (invariant #2).
 *
 * The host wires a backend onto the tree at startup, mirroring
 * [com.neoutils.engine.tree.SceneTree.audio] and
 * [com.neoutils.engine.tree.SceneTree.textMeasurer]. When no backend is wired
 * the field is `null` and sprites degrade to invisible via
 * `tree.textures?.load(...)`.
 */
interface TextureBackend {

    /**
     * Resolves and decodes the image asset at [path] into a reusable [Texture]
     * handle. Implementations resolve [path] via the classpath using the
     * project's asset convention (e.g. `"demos/sprites/idle.png"`). The asset
     * is decoded **once** and the result **cached by path**: a repeated [load]
     * of the same [path] returns the **same** handle (no re-decode). A missing,
     * unreadable or undecodable asset fails fast with a descriptive exception —
     * never a silent no-op handle.
     */
    fun load(path: String): Texture

    /** Releases every live native texture (the whole cache). Idempotent. */
    fun dispose()
}
