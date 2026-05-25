package com.neoutils.engine.scene

import com.neoutils.engine.serialization.Inspect
import com.neoutils.engine.serialization.Signal
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Logical node that emits the [timeout] signal at fixed intervals.
 *
 * `Timer` extends [Node] directly (not [Node2D]) — it carries no spatial state
 * and is the first precedent for non-visual logical nodes in the engine.
 *
 * The active countdown callback is selected per-tick by [processCallback]:
 * `PHYSICS` decrements in [onPhysicsProcess] (deterministic, fixed `dt`),
 * `IDLE` decrements in [onProcess] (variable `dt`, may accumulate fractional
 * drift). [processCallback] is re-sampled on every tick — assigning a new
 * value at runtime takes effect on the next tick of the new callback.
 *
 * Calling [start] with no argument resets `timeLeft` to [waitTime]. Calling
 * [start] with a positive `override` applies that value to the next emission
 * only; subsequent emissions (when `oneShot == false`) use [waitTime].
 */
@Serializable
class Timer : Node() {

    @Inspect
    var waitTime: Float = 1f

    @Inspect
    var autostart: Boolean = false

    @Inspect
    var oneShot: Boolean = false

    @Inspect
    var processCallback: TimerMode = TimerMode.PHYSICS

    @Transient
    var timeLeft: Float = 0f

    @Transient
    val timeout: Signal<Unit> = Signal()

    val isStopped: Boolean get() = timeLeft <= 0f

    /**
     * Resets [timeLeft] to [waitTime] (no argument) or to [override] (one-shot
     * override applied only to the next emission). Subsequent emissions
     * continue to use [waitTime].
     *
     * @throws IllegalArgumentException when [override] is provided and is not
     *   strictly positive.
     */
    fun start(override: Float? = null) {
        if (override != null) {
            require(override > 0f) { "Timer.start override must be positive, got $override" }
            timeLeft = override
        } else {
            timeLeft = waitTime
        }
    }

    fun stop() {
        timeLeft = 0f
    }

    override fun onEnter() {
        super.onEnter()
        if (autostart) start()
    }

    override fun onPhysicsProcess(dt: Float) {
        super.onPhysicsProcess(dt)
        if (processCallback == TimerMode.PHYSICS) tick(dt)
    }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        if (processCallback == TimerMode.IDLE) tick(dt)
    }

    override fun onExit() {
        super.onExit()
        stop()
    }

    private fun tick(dt: Float) {
        if (timeLeft <= 0f) return
        timeLeft -= dt
        if (timeLeft > 0f) return
        if (oneShot) {
            timeLeft = 0f
            timeout.emit(Unit)
        } else {
            val overshoot = -timeLeft
            timeLeft = waitTime - overshoot
            if (timeLeft < 0f) timeLeft = 0f
            timeout.emit(Unit)
        }
    }
}
