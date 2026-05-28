package com.neoutils.engine.dx

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class RecordingSink : LogSink {
    val entries: MutableList<Triple<LogLevel, String, String>> = mutableListOf()
    override fun emit(timestampMillis: Long, level: LogLevel, tag: String, message: String) {
        entries += Triple(level, tag, message)
    }
}

class LogTest {

    private lateinit var sink: RecordingSink
    private var previousSink: LogSink = ConsoleLogSink
    private var previousGlobal: LogLevel = LogLevel.Info

    @BeforeTest
    fun setup() {
        sink = RecordingSink()
        previousSink = Log.sink
        previousGlobal = Log.config.globalLevel
        Log.sink = sink
    }

    @AfterTest
    fun teardown() {
        Log.sink = previousSink
        Log.config.globalLevel = previousGlobal
        Log.config.clearTagLevel("Physics")
        Log.config.clearTagLevel("Render")
    }

    @Test
    fun `global Info hides Debug calls`() {
        Log.config.globalLevel = LogLevel.Info
        Log.d("Render", "noisy")
        Log.i("Render", "useful")
        assertEquals(1, sink.entries.size)
        assertEquals(LogLevel.Info, sink.entries[0].first)
    }

    @Test
    fun `per-tag override surfaces below global level`() {
        Log.config.globalLevel = LogLevel.Warn
        Log.config.setTagLevel("Physics", LogLevel.Debug)
        Log.d("Physics", "step")
        Log.d("Render", "frame")
        assertEquals(1, sink.entries.size)
        assertEquals("Physics", sink.entries[0].second)
        assertEquals("step", sink.entries[0].third)
    }

    @Test
    fun `clearTagLevel restores global behaviour`() {
        Log.config.globalLevel = LogLevel.Warn
        Log.config.setTagLevel("Physics", LogLevel.Debug)
        Log.d("Physics", "before")
        Log.config.clearTagLevel("Physics")
        Log.d("Physics", "after")
        assertEquals(1, sink.entries.size)
        assertTrue(sink.entries[0].third == "before")
    }
}
