package com.neoutils.engine.audio

/**
 * Opaque handle to a pre-decoded audio asset, produced by [AudioBackend.load]
 * and replayed via [AudioBackend.play].
 *
 * Intentionally a marker interface **without members**: no backend detail
 * (raw bytes, sample format, duration) leaks across this seam (invariant #2).
 * Any future query (duration, channels) is added as an [AudioBackend] method
 * taking a `Sound`, never as a member here. The decoded PCM is materialized
 * once at `load` time so re-triggering a sound never pays I/O or decode again.
 */
interface Sound
