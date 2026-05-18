package com.neoutils.engine.dx

object Debug {

    @Volatile var showFps: Boolean = false

    @Volatile var colliderVisualization: Boolean = false

    /** Updated by the runtime each frame; read by overlay drawing code. */
    @Volatile var currentFps: Float = 0f

    val log: LogConfig = LogConfig()
}
