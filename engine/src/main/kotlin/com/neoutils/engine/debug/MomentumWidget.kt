package com.neoutils.engine.debug

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.totalAngularMomentum
import com.neoutils.engine.physics.totalKineticEnergy
import com.neoutils.engine.physics.totalLinearMomentum
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import java.util.Locale
import kotlin.math.abs

private const val LINE_HEIGHT: Float = 16f
private const val SPARK_COLUMN: Float = 210f
private const val SPARK_WIDTH: Float = 80f
private const val SPARK_HEIGHT: Float = 12f

/**
 * Screen-space readout of `Σp`, `ΣL`, and `ΣKE` with one-second sparklines.
 * Owns its ring buffer (no shared singleton); records on every
 * `onPhysicsProcess` tick while enabled. Flipping `enabled` from `false`
 * to `true` resets the buffer so the user does not see stale samples from
 * the previous enabled window.
 */
class MomentumWidget : ScreenDebugWidget() {

    override val title: String = "Momentum"

    override val defaultSlot: DockSlot = DockSlot.BOTTOM_LEFT

    private val capacity: Int = 60
    private val pXSamples: FloatArray = FloatArray(capacity)
    private val pYSamples: FloatArray = FloatArray(capacity)
    private val lSamples: FloatArray = FloatArray(capacity)
    private val keSamples: FloatArray = FloatArray(capacity)
    private var size: Int = 0
    private var head: Int = 0

    init { name = "MomentumWidget" }

    override var enabled: Boolean = false
        set(value) {
            val flipping = value && !field
            field = value
            if (flipping) {
                size = 0
                head = 0
            }
        }

    override fun onPhysicsProcess(dt: Float) {
        super.onPhysicsProcess(dt)
        if (!enabled) return
        val owningTree = tree ?: return
        val p = owningTree.totalLinearMomentum()
        val l = owningTree.totalAngularMomentum()
        val ke = owningTree.totalKineticEnergy()
        pXSamples[head] = p.x
        pYSamples[head] = p.y
        lSamples[head] = l
        keSamples[head] = ke
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    override fun bodySize(): Vec2 {
        if (size == 0) return Vec2.ZERO
        val pad = DebugTheme.padding
        return Vec2(pad * 2f + SPARK_COLUMN + SPARK_WIDTH, pad * 2f + LINE_HEIGHT * 3f)
    }

    override fun drawDebug(renderer: Renderer) {
        if (size == 0) return
        val pad = DebugTheme.padding
        val textSize = DebugTheme.bodyTextSize
        val body = bodyOrigin
        val originX = body.x + pad
        val baseY = body.y + pad
        val pNowX = lastSample(pXSamples)
        val pNowY = lastSample(pYSamples)
        val lNow = lastSample(lSamples)
        val keNow = lastSample(keSamples)

        renderer.drawText(
            text = "Σp = (${fmt1(pNowX)}, ${fmt1(pNowY)})",
            position = Vec2(originX, baseY),
            size = textSize,
            color = DebugTheme.textColor,
        )
        renderer.drawText(
            text = "ΣL = ${fmt2(lNow)}",
            position = Vec2(originX, baseY + LINE_HEIGHT),
            size = textSize,
            color = DebugTheme.textColor,
        )
        renderer.drawText(
            text = "ΣKE = ${fmt1(keNow)}",
            position = Vec2(originX, baseY + LINE_HEIGHT * 2),
            size = textSize,
            color = DebugTheme.textColor,
        )

        val sparkX = originX + SPARK_COLUMN
        drawSparkline(renderer, pXSamples, baseY + 2f, sparkX, SPARK_WIDTH, SPARK_HEIGHT, Color(0.4f, 0.8f, 1f, 1f))
        drawSparkline(renderer, lSamples, baseY + 2f + LINE_HEIGHT, sparkX, SPARK_WIDTH, SPARK_HEIGHT, Color(0.4f, 1f, 0.6f, 1f))
        drawSparkline(renderer, keSamples, baseY + 2f + LINE_HEIGHT * 2, sparkX, SPARK_WIDTH, SPARK_HEIGHT, Color(1f, 0.6f, 0.4f, 1f))
    }

    private fun fmt1(v: Float): String = String.format(Locale.US, "%.1f", v)
    private fun fmt2(v: Float): String = String.format(Locale.US, "%.2f", v)

    private fun lastSample(buf: FloatArray): Float {
        if (size == 0) return 0f
        val idx = if (head == 0) capacity - 1 else head - 1
        return buf[idx]
    }

    private fun drawSparkline(
        renderer: Renderer,
        buf: FloatArray,
        y: Float,
        x: Float,
        w: Float,
        h: Float,
        color: Color,
    ) {
        if (size < 2) return
        var maxAbs = 0f
        var i = 0
        var cursor = if (head - size < 0) head - size + capacity else head - size
        while (i < size) {
            val v = buf[cursor]
            if (abs(v) > maxAbs) maxAbs = abs(v)
            cursor = (cursor + 1) % capacity
            i++
        }
        if (maxAbs == 0f) maxAbs = 1f
        cursor = if (head - size < 0) head - size + capacity else head - size
        var prevX = x
        var prevY = y + h / 2f - buf[cursor] / maxAbs * h / 2f
        cursor = (cursor + 1) % capacity
        for (j in 1 until size) {
            val nx = x + j.toFloat() / (size - 1).toFloat() * w
            val ny = y + h / 2f - buf[cursor] / maxAbs * h / 2f
            renderer.drawLine(Vec2(prevX, prevY), Vec2(nx, ny), 1f, color)
            prevX = nx
            prevY = ny
            cursor = (cursor + 1) % capacity
        }
    }
}
