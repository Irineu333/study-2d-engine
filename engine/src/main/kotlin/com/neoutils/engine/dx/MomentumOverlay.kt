package com.neoutils.engine.dx

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.totalAngularMomentum
import com.neoutils.engine.physics.totalKineticEnergy
import com.neoutils.engine.physics.totalLinearMomentum
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.tree.SceneTree
import java.util.Locale
import kotlin.math.abs

/**
 * Ring buffer of momentum + KE samples used to draw the F3 didactic overlay.
 * Each physics tick the host calls [recordSample] with the current
 * `SceneTree`; renders happen each frame via [renderMomentumOverlay] when
 * [Debug.showMomentumOverlay] is on.
 *
 * Lives in screen space (no Camera2D transform). 60 samples = 1 second at the
 * default 60 Hz physics rate.
 */
object MomentumOverlay {

    private const val CAPACITY = 60

    /** Most recent samples ordered oldest first; size <= [CAPACITY]. */
    private val pXSamples = FloatArray(CAPACITY)
    private val pYSamples = FloatArray(CAPACITY)
    private val lSamples = FloatArray(CAPACITY)
    private val keSamples = FloatArray(CAPACITY)

    private var size: Int = 0
    private var head: Int = 0 // next write position

    fun reset() {
        size = 0
        head = 0
    }

    /** Called once per physics tick. O(N) over RigidBody2D in tree. */
    fun recordSample(tree: SceneTree) {
        val p = tree.totalLinearMomentum()
        val l = tree.totalAngularMomentum()
        val ke = tree.totalKineticEnergy()
        pXSamples[head] = p.x
        pYSamples[head] = p.y
        lSamples[head] = l
        keSamples[head] = ke
        head = (head + 1) % CAPACITY
        if (size < CAPACITY) size++
    }

    fun renderOverlay(renderer: Renderer, surfaceWidth: Float, surfaceHeight: Float) {
        if (size == 0) return
        val lineHeight = 16f
        val textSize = 12f
        val sparkW = 80f
        val sparkH = 12f
        val pad = 6f
        val baseY = surfaceHeight - 60f
        val pNow = lastSample(pXSamples) to lastSample(pYSamples)
        val lNow = lastSample(lSamples)
        val keNow = lastSample(keSamples)

        renderer.drawText(
            text = "Σp = (${fmt1(pNow.first)}, ${fmt1(pNow.second)})",
            position = Vec2(pad, baseY),
            size = textSize,
            color = Color.WHITE,
        )
        renderer.drawText(
            text = "ΣL = ${fmt2(lNow)}",
            position = Vec2(pad, baseY + lineHeight),
            size = textSize,
            color = Color.WHITE,
        )
        renderer.drawText(
            text = "ΣKE = ${fmt1(keNow)}",
            position = Vec2(pad, baseY + lineHeight * 2),
            size = textSize,
            color = Color.WHITE,
        )

        val sparkX = pad + 220f
        drawSparkline(renderer, pXSamples, baseY + 2f, sparkX, sparkW, sparkH, Color(0.4f, 0.8f, 1f, 1f))
        drawSparkline(renderer, lSamples, baseY + 2f + lineHeight, sparkX, sparkW, sparkH, Color(0.4f, 1f, 0.6f, 1f))
        drawSparkline(renderer, keSamples, baseY + 2f + lineHeight * 2, sparkX, sparkW, sparkH, Color(1f, 0.6f, 0.4f, 1f))
    }

    private fun fmt1(v: Float): String = String.format(Locale.US, "%.1f", v)
    private fun fmt2(v: Float): String = String.format(Locale.US, "%.2f", v)

    private fun lastSample(buf: FloatArray): Float {
        if (size == 0) return 0f
        val idx = if (head == 0) CAPACITY - 1 else head - 1
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
        // Symmetric range around max abs value.
        var maxAbs = 0f
        var i = 0
        var cursor = if (head - size < 0) head - size + CAPACITY else head - size
        while (i < size) {
            val v = buf[cursor]
            if (abs(v) > maxAbs) maxAbs = abs(v)
            cursor = (cursor + 1) % CAPACITY
            i++
        }
        if (maxAbs == 0f) maxAbs = 1f
        cursor = if (head - size < 0) head - size + CAPACITY else head - size
        var prevX = x
        var prevY = y + h / 2f - buf[cursor] / maxAbs * h / 2f
        cursor = (cursor + 1) % CAPACITY
        for (j in 1 until size) {
            val nx = x + j.toFloat() / (size - 1).toFloat() * w
            val ny = y + h / 2f - buf[cursor] / maxAbs * h / 2f
            renderer.drawLine(Vec2(prevX, prevY), Vec2(nx, ny), 1f, color)
            prevX = nx
            prevY = ny
            cursor = (cursor + 1) % CAPACITY
        }
    }
}
