package com.neoutils.engine.dx

import java.util.concurrent.ConcurrentHashMap

fun interface LogSink {
    fun emit(timestampMillis: Long, level: LogLevel, tag: String, message: String)
}

object ConsoleLogSink : LogSink {
    override fun emit(timestampMillis: Long, level: LogLevel, tag: String, message: String) {
        val seconds = timestampMillis / 1000
        val millis = timestampMillis % 1000
        val stream = if (level == LogLevel.Error || level == LogLevel.Warn) System.err else System.out
        stream.println("[$seconds.$millis] [$level] [$tag] $message")
    }
}

class LogConfig {

    @Volatile var globalLevel: LogLevel = LogLevel.Info
    private val tagLevels: MutableMap<String, LogLevel> = ConcurrentHashMap()

    fun setTagLevel(tag: String, level: LogLevel) {
        tagLevels[tag] = level
    }

    fun clearTagLevel(tag: String) {
        tagLevels.remove(tag)
    }

    fun effectiveLevel(tag: String): LogLevel = tagLevels[tag] ?: globalLevel
}

object Log {

    /** Process-wide log configuration. Read by [log] before emitting. */
    val config: LogConfig = LogConfig()

    @Volatile var sink: LogSink = ConsoleLogSink

    fun d(tag: String, message: String) = log(LogLevel.Debug, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.Info, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.Warn, tag, message)
    fun e(tag: String, message: String) = log(LogLevel.Error, tag, message)

    private fun log(level: LogLevel, tag: String, message: String) {
        if (level.ordinal < config.effectiveLevel(tag).ordinal) return
        sink.emit(System.currentTimeMillis(), level, tag, message)
    }
}
