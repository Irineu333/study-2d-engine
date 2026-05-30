package com.neoutils.engine.debug

/**
 * Per-`SceneTree` record of the last frame's tick phase durations, in
 * nanoseconds. `GameLoop.tick` writes here once per frame while profiling is
 * enabled (gated by `tree.debug.profiler.enabled`); `ProfilerWidget` reads it
 * each frame into its moving-average windows.
 *
 * Runtime-only — not `@Serializable`, never persisted, one per tree.
 *
 * - [hitTestNanos] wraps `hitTestUI`.
 * - [physicsNanos] is the sum across every fixed-step iteration that frame
 *   (`physicsProcess` + `physics.step`), with [physicsSteps] counting them.
 * - [processNanos] wraps `process`.
 * - [renderNanos] wraps `render`.
 * - [totalNanos] spans the whole tick (≥ the sum of the four phases; the
 *   remainder is per-tick overhead).
 */
class FrameProfile {
    var hitTestNanos: Long = 0L
    var physicsNanos: Long = 0L
    var processNanos: Long = 0L
    var renderNanos: Long = 0L
    var totalNanos: Long = 0L
    var physicsSteps: Int = 0
}