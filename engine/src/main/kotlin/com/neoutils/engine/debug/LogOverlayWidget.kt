package com.neoutils.engine.debug

import com.neoutils.engine.dx.Log
import com.neoutils.engine.dx.LogLevel
import com.neoutils.engine.dx.LogSink
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer

/**
 * Screen-space tail of the last [capacity] log entries. Subscribes itself as
 * a [LogSink] while [enabled] and unsubscribes when disabled, so a closed
 * overlay records nothing and an opened one shows a *live tail* of entries
 * emitted from then on — not past history.
 *
 * `Log.*` may run on any thread while `drawDebug` runs on the render thread,
 * so the ring buffer is guarded by [lock]: [emit] writes under it and
 * `drawDebug` copies a snapshot under it before drawing.
 */
class LogOverlayWidget : ScreenDebugWidget(), LogSink {

    override val title: String = "Log"

    override val defaultSlot: DockSlot = DockSlot.BOTTOM_LEFT

    /** Display-only floor; orthogonal to `Log.config`. Set freely at runtime. */
    var minLevel: LogLevel = LogLevel.Debug

    private val capacity: Int = 12
    private val buffer: Array<LogEntry?> = arrayOfNulls(capacity)
    private var head: Int = 0
    private var size: Int = 0
    private val lock = Any()

    // The dock measures via contentSize before drawDebug runs; cache the same
    // visible snapshot so both agree within a frame.
    private var lastVisible: List<LogEntry> = emptyList()

    init { name = "LogOverlayWidget" }

    override var enabled: Boolean = false
        set(value) {
            val flippingOn = value && !field
            val flippingOff = !value && field
            field = value
            when {
                flippingOn -> {
                    synchronized(lock) {
                        head = 0
                        size = 0
                    }
                    Log.addSink(this)
                }
                flippingOff -> Log.removeSink(this)
            }
        }

    override fun emit(timestampMillis: Long, level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(timestampMillis, level, tag, message)
        synchronized(lock) {
            buffer[head] = entry
            head = (head + 1) % capacity
            if (size < capacity) size++
        }
    }

    /** Oldest→newest visible tail, snapshotted under [lock] and cached. */
    private fun snapshotVisible(): List<LogEntry> {
        val snapshot = synchronized(lock) {
            val out = ArrayList<LogEntry>(size)
            var cursor = (head - size + capacity) % capacity
            repeat(size) {
                buffer[cursor]?.let(out::add)
                cursor = (cursor + 1) % capacity
            }
            out
        }
        return snapshot.filter { it.level.ordinal >= minLevel.ordinal }
    }

    override fun bodySize(): Vec2 {
        lastVisible = snapshotVisible()
        if (lastVisible.isEmpty()) return Vec2.ZERO
        return Vec2(WIDTH, DebugTheme.padding * 2f + lastVisible.size * LINE_HEIGHT)
    }

    override fun drawDebug(renderer: Renderer) {
        val visible = lastVisible
        if (visible.isEmpty()) return

        val body = bodyOrigin
        // Oldest visible line on top, newest at the base; drawn newest-first so
        // overlapping (none here) and recorded order stay newest-first.
        val top = body.y + DebugTheme.padding
        for ((rowFromBottom, entry) in visible.asReversed().withIndex()) {
            val index = visible.size - 1 - rowFromBottom
            renderer.drawText(
                text = "[${entry.tag}] ${entry.message}",
                position = Vec2(body.x + DebugTheme.padding, top + index * LINE_HEIGHT),
                size = TEXT_SIZE,
                color = colorFor(entry.level),
            )
        }
    }

    private fun colorFor(level: LogLevel): Color = when (level) {
        LogLevel.Warn -> DebugTheme.logWarnColor
        LogLevel.Error -> DebugTheme.logErrorColor
        else -> DebugTheme.logNeutralColor
    }

    companion object {
        private const val WIDTH: Float = 360f
        private const val LINE_HEIGHT: Float = 16f
        private const val TEXT_SIZE: Float = 12f
    }
}
