package com.neoutils.engine.dx

/**
 * Moving-average FPS over a sliding window of frame timestamps. The default
 * window is 1 second, matching the dx-tooling spec.
 */
class FpsCounter(val windowSeconds: Float = 1f) {

    private val samples: ArrayDeque<Long> = ArrayDeque()

    var current: Float = 0f
        private set

    fun record(nowNanos: Long): Float {
        samples.addLast(nowNanos)
        val cutoff = nowNanos - (windowSeconds * 1_000_000_000f).toLong()
        while (samples.isNotEmpty() && samples.first() < cutoff) samples.removeFirst()
        val span = (nowNanos - samples.first()).coerceAtLeast(1L)
        current = if (samples.size <= 1) 0f else (samples.size - 1) * 1_000_000_000f / span
        return current
    }
}
