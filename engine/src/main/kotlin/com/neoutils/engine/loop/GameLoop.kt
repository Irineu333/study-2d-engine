package com.neoutils.engine.loop

import com.neoutils.engine.debug.FrameProfile
import com.neoutils.engine.dx.Log
import com.neoutils.engine.input.Input
import com.neoutils.engine.physics.PhysicsSystem
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.tree.SceneTree

class GameLoop(
    val tree: SceneTree,
    val renderer: Renderer,
    val input: Input,
    val physics: PhysicsSystem = PhysicsSystem(),
    physicsHz: Int = 60,
) {

    /**
     * Maximum frame `dt` (seconds) emitted to `SceneTree.process`. Clamps the
     * first frame and any stalls (debugger pause, GC) so a single tick never
     * advances `_process` by more than ~50 ms in one shot.
     */
    var maxDt: Float = 0.05f

    private val physicsDt: Float = 1f / physicsHz
    private val maxStepsPerFrame: Int = 5
    private var accumulator: Float = 0f

    init {
        tree.physicsSystem = physics
    }

    /**
     * One frame of the engine. Logically:
     *
     *  1. hit-test UI (always), then resolve the effective gameplay delta:
     *     `gameplayDt = if (paused) 0 else rawDt * tree.timeScale`;
     *  2. step path — if the tree is frozen (`paused` or `timeScale == 0`) and
     *     a [SceneTree.requestStep] is pending, advance exactly one fixed
     *     physics step (drain pending, `_physics_process`, `physics.step`,
     *     `_process(physicsDt)`, `render`) and return; the pending flag is
     *     consumed every tick so a step while time flows is a no-op;
     *  3. accumulate `gameplayDt` (not the raw `dt`), so `timeScale`/`paused`
     *     naturally scale how many physics steps drain (zero when frozen);
     *  4. while the accumulator can fit a full physics step (and we have not
     *     hit [maxStepsPerFrame]): drain pending, run `_physics_process`,
     *     drain pending, run `physics.step`, then decrement the accumulator;
     *  5. if [maxStepsPerFrame] was reached and the accumulator still holds
     *     more than one physics step, reset it to zero (spiral-of-death
     *     clamp — covers high `timeScale`, which accumulates faster);
     *  6. drain pending, run `_process` with the scaled frame `dt` (clamped to
     *     [maxDt] so visual interpolation does not warp on stalls) — frozen
     *     runs `process(0f)` rather than skipping it, keeping debug/UI alive;
     *  7. drain pending, then `render`.
     *
     * With the defaults (`timeScale == 1`, `paused == false`, no pending step)
     * this is byte-for-byte the previous tick. The accumulator lives in the
     * loop, so backends (`SkikoHost`, LWJGL host) need not be aware of
     * fixed-step physics.
     */
    fun tick(dtNanos: Long) {
        // Gated profiling: when off, the whole timing path is skipped — not one
        // nanoTime is called, so production frames carry zero overhead. The
        // ProfilerWidget's `enabled` is the single switch (set via the HUD row).
        if (!tree.debug.profiler.enabled) {
            tickRaw(dtNanos)
            return
        }
        val profile = tree.debug.frameProfile
        val tickStart = System.nanoTime()
        var hitTestNanos = 0L
        var physicsNanos = 0L
        var processNanos = 0L
        var renderNanos = 0L
        var physicsSteps = 0

        if (!tree.root.isLive) tree.start()
        tree.input = input
        val hitTestStart = System.nanoTime()
        tree.hitTestUI(input)
        hitTestNanos = System.nanoTime() - hitTestStart

        val rawDt = (dtNanos / 1_000_000_000f).coerceAtLeast(0f)
        val frozen = tree.paused || tree.timeScale == 0f
        val stepRequested = tree.consumePendingStep()
        if (frozen && stepRequested) {
            tree.applyPending()
            val physicsStart = System.nanoTime()
            tree.physicsProcess(physicsDt)
            tree.applyPending()
            physics.step(tree, physicsDt)
            physicsNanos += System.nanoTime() - physicsStart
            physicsSteps++
            tree.applyPending()
            val processStart = System.nanoTime()
            tree.process(physicsDt)
            processNanos = System.nanoTime() - processStart
            tree.applyPending()
            val renderStart = System.nanoTime()
            tree.render(renderer)
            renderNanos = System.nanoTime() - renderStart
            writeProfile(profile, hitTestNanos, physicsNanos, processNanos, renderNanos, tickStart, physicsSteps)
            return
        }
        val gameplayDt = if (tree.paused) 0f else rawDt * tree.timeScale
        accumulator += gameplayDt
        while (accumulator >= physicsDt && physicsSteps < maxStepsPerFrame) {
            tree.applyPending()
            val physicsStart = System.nanoTime()
            tree.physicsProcess(physicsDt)
            tree.applyPending()
            physics.step(tree, physicsDt)
            physicsNanos += System.nanoTime() - physicsStart
            accumulator -= physicsDt
            physicsSteps++
        }
        if (physicsSteps == maxStepsPerFrame && accumulator > physicsDt) {
            Log.w(TAG, spiralMessage(dtNanos))
            accumulator = 0f
        }
        val frameDt = gameplayDt.coerceAtMost(maxDt)
        tree.applyPending()
        val processStart = System.nanoTime()
        tree.process(frameDt)
        processNanos = System.nanoTime() - processStart
        tree.applyPending()
        val renderStart = System.nanoTime()
        tree.render(renderer)
        renderNanos = System.nanoTime() - renderStart
        writeProfile(profile, hitTestNanos, physicsNanos, processNanos, renderNanos, tickStart, physicsSteps)
    }

    /** The original, un-instrumented tick — the production path when profiling is off. */
    private fun tickRaw(dtNanos: Long) {
        if (!tree.root.isLive) tree.start()
        tree.input = input
        // UI hit-test runs first so any consumption is visible to scripts in
        // the same tick (gameplay's `wasMouseClicked` returns false when the
        // click landed on a Button).
        tree.hitTestUI(input)
        val rawDt = (dtNanos / 1_000_000_000f).coerceAtLeast(0f)
        val frozen = tree.paused || tree.timeScale == 0f
        // Consume the step flag every tick so a request never survives one and
        // requestStep() while time flows is a no-op.
        val stepRequested = tree.consumePendingStep()
        if (frozen && stepRequested) {
            tree.applyPending()
            tree.physicsProcess(physicsDt)
            tree.applyPending()
            physics.step(tree, physicsDt)
            tree.applyPending()
            tree.process(physicsDt)
            tree.applyPending()
            tree.render(renderer)
            return
        }
        val gameplayDt = if (tree.paused) 0f else rawDt * tree.timeScale
        accumulator += gameplayDt
        var steps = 0
        while (accumulator >= physicsDt && steps < maxStepsPerFrame) {
            tree.applyPending()
            tree.physicsProcess(physicsDt)
            tree.applyPending()
            physics.step(tree, physicsDt)
            accumulator -= physicsDt
            steps++
        }
        if (steps == maxStepsPerFrame && accumulator > physicsDt) {
            Log.w(TAG, spiralMessage(dtNanos))
            accumulator = 0f
        }
        val frameDt = gameplayDt.coerceAtMost(maxDt)
        tree.applyPending()
        tree.process(frameDt)
        tree.applyPending()
        tree.render(renderer)
    }

    private fun writeProfile(
        profile: FrameProfile,
        hitTestNanos: Long,
        physicsNanos: Long,
        processNanos: Long,
        renderNanos: Long,
        tickStart: Long,
        physicsSteps: Int,
    ) {
        profile.hitTestNanos = hitTestNanos
        profile.physicsNanos = physicsNanos
        profile.processNanos = processNanos
        profile.renderNanos = renderNanos
        profile.physicsSteps = physicsSteps
        profile.totalNanos = System.nanoTime() - tickStart
    }

    private fun spiralMessage(dtNanos: Long): String =
        "physics spiral-of-death clamp: dropping ${accumulator}s of " +
            "accumulated dt (physicsHz=${(1f / physicsDt).toInt()}, " +
            "frame dtNanos=$dtNanos)"

    companion object {
        private const val TAG = "GameLoop"
    }
}
