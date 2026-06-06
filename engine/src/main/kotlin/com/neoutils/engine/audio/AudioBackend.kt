package com.neoutils.engine.audio

/**
 * Platform SPI for short, fire-and-forget sound effects. Server-style service
 * reached through [com.neoutils.engine.tree.SceneTree.audio] — not a `Node`,
 * not a `Component` (invariant #1). Lives in `:engine` as a pure Kotlin
 * interface; concrete backends live in their own modules (e.g.
 * `:engine-audio-javasound`) so `:engine` never declares a native audio
 * dependency (invariant #2).
 *
 * The host wires a backend onto the tree at startup, mirroring
 * [com.neoutils.engine.tree.SceneTree.textMeasurer]. When no backend is wired
 * the field is `null` and gameplay degrades to silence via `tree.audio?.play(...)`.
 */
interface AudioBackend {

    /**
     * Resolves and decodes the audio asset at [path] into a reusable [Sound]
     * handle. The asset is decoded **once** here (off the hot path); the
     * returned handle can be passed to [play] repeatedly without re-decoding.
     * Implementations resolve [path] via the classpath using the project's
     * asset convention (e.g. `"pong/sfx/blip.wav"`). A missing, unreadable or
     * unsupported asset fails fast with a descriptive exception — never a silent
     * no-op handle.
     */
    fun load(path: String): Sound

    /**
     * Fires playback of [sound] and returns immediately (fire-and-forget),
     * without blocking until the sound ends. Overlapping plays of the same
     * handle sound simultaneously — a rapid re-trigger never cuts a voice still
     * playing. [volume] in `[0f, 1f]` scales the gain (`1f` = nominal).
     */
    fun play(sound: Sound, volume: Float = 1f)

    /** Releases all live native resources (open lines, mixer). Idempotent. */
    fun dispose()
}
