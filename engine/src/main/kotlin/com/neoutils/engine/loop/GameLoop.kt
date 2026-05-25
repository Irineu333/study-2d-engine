package com.neoutils.engine.loop

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

    /**
     * One frame of the engine. Logically:
     *
     *  1. accumulate the raw frame `dt`;
     *  2. while the accumulator can fit a full physics step (and we have not
     *     hit [maxStepsPerFrame]): drain pending, run `_physics_process`,
     *     drain pending, run `physics.step`, then decrement the accumulator;
     *  3. if [maxStepsPerFrame] was reached and the accumulator still holds
     *     more than one physics step, reset it to zero (spiral-of-death
     *     clamp — see design.md D3);
     *  4. drain pending, run `_process` with the frame `dt` (clamped to
     *     [maxDt] so visual interpolation does not warp on stalls);
     *  5. drain pending, then `render`.
     *
     * The accumulator lives in the loop, so backends (`SkikoHost`,
     * `ComposeHost`) need not be aware of fixed-step physics.
     */
    fun tick(dtNanos: Long) {
        if (!tree.root.isLive) tree.start()
        tree.input = input
        val rawDt = (dtNanos / 1_000_000_000f).coerceAtLeast(0f)
        accumulator += rawDt
        var steps = 0
        while (accumulator >= physicsDt && steps < maxStepsPerFrame) {
            tree.applyPending()
            tree.physicsProcess(physicsDt)
            tree.applyPending()
            physics.step(tree)
            accumulator -= physicsDt
            steps++
        }
        if (steps == maxStepsPerFrame && accumulator > physicsDt) {
            Log.w(
                TAG,
                "physics spiral-of-death clamp: dropping ${accumulator}s of " +
                    "accumulated dt (physicsHz=${(1f / physicsDt).toInt()}, " +
                    "frame dtNanos=$dtNanos)",
            )
            accumulator = 0f
        }
        val frameDt = rawDt.coerceAtMost(maxDt)
        tree.applyPending()
        tree.process(frameDt)
        tree.applyPending()
        tree.render(renderer)
    }

    companion object {
        private const val TAG = "GameLoop"
    }
}
