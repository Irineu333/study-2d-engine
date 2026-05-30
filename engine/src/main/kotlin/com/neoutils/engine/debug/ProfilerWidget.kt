package com.neoutils.engine.debug

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import java.util.Locale

/**
 * Screen-space readout of where the frame's time goes, per tick phase
 * (hitTest / physics / process / render) plus a derived "other" (total − Σ).
 *
 * [enabled] is the single control: it drives `GameLoop.tick`'s instrumentation
 * (the loop only measures when this is on) and gates the widget's own sampling
 * and drawing. Each enabled frame [onProcess] reads the tree's [FrameProfile]
 * into a per-phase ring buffer (~60 samples, in the spirit of `MomentumWidget`
 * / `FpsCounter`) and `drawDebug` shows the smoothed milliseconds per phase
 * with the latest frame's `physicsSteps`. Flipping `enabled` from `false` to
 * `true` resets the windows so no stale averages survive a disabled gap.
 */
class ProfilerWidget : ScreenDebugWidget() {

    override val title: String = "Profiler"

    private val capacity: Int = 60
    private val hitTestSamples: LongArray = LongArray(capacity)
    private val physicsSamples: LongArray = LongArray(capacity)
    private val processSamples: LongArray = LongArray(capacity)
    private val renderSamples: LongArray = LongArray(capacity)
    private val totalSamples: LongArray = LongArray(capacity)
    private var size: Int = 0
    private var head: Int = 0
    private var lastSteps: Int = 0

    init { name = "ProfilerWidget" }

    override var enabled: Boolean = false
        set(value) {
            val flipping = value && !field
            field = value
            if (flipping) {
                size = 0
                head = 0
                lastSteps = 0
            }
        }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        if (!enabled) return
        val profile = tree?.debug?.frameProfile ?: return
        hitTestSamples[head] = profile.hitTestNanos
        physicsSamples[head] = profile.physicsNanos
        processSamples[head] = profile.processNanos
        renderSamples[head] = profile.renderNanos
        totalSamples[head] = profile.totalNanos
        lastSteps = profile.physicsSteps
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    override fun drawDebug(renderer: Renderer) {
        if (size == 0) return
        val hitTest = avgMs(hitTestSamples)
        val physics = avgMs(physicsSamples)
        val process = avgMs(processSamples)
        val render = avgMs(renderSamples)
        val total = avgMs(totalSamples)
        val other = (total - hitTest - physics - process - render).coerceAtLeast(0f)

        val pad = 6f
        val lineHeight = 14f
        val textSize = 12f
        var y = pad + textSize
        row(renderer, "hitTest", hitTest, total, pad, y, textSize); y += lineHeight
        row(renderer, "physics ($lastSteps)", physics, total, pad, y, textSize); y += lineHeight
        row(renderer, "process", process, total, pad, y, textSize); y += lineHeight
        row(renderer, "render", render, total, pad, y, textSize); y += lineHeight
        row(renderer, "other", other, total, pad, y, textSize); y += lineHeight
        renderer.drawText(
            text = "total = ${fmt(total)} ms",
            position = Vec2(pad, y),
            size = textSize,
            color = Color.WHITE,
        )
    }

    private fun row(
        renderer: Renderer,
        label: String,
        ms: Float,
        total: Float,
        x: Float,
        y: Float,
        textSize: Float,
    ) {
        val pct = if (total > 0f) ms / total * 100f else 0f
        renderer.drawText(
            text = "$label = ${fmt(ms)} ms (${fmt0(pct)}%)",
            position = Vec2(x, y),
            size = textSize,
            color = Color.WHITE,
        )
    }

    private fun avgMs(buf: LongArray): Float {
        if (size == 0) return 0f
        var sum = 0L
        var i = 0
        var cursor = if (head - size < 0) head - size + capacity else head - size
        while (i < size) {
            sum += buf[cursor]
            cursor = (cursor + 1) % capacity
            i++
        }
        return sum.toFloat() / size / 1_000_000f
    }

    private fun fmt(v: Float): String = String.format(Locale.US, "%.2f", v)
    private fun fmt0(v: Float): String = String.format(Locale.US, "%.0f", v)
}