package com.neoutils.engine.dx

object Debug {

    @Volatile var showFps: Boolean = false

    @Volatile var colliderVisualization: Boolean = false

    /** Toggled by the F3 key (see `GameConfig.toggleMomentumOverlayKey`). */
    @Volatile var showMomentumOverlay: Boolean = false

    /** Updated by the runtime each frame; read by overlay drawing code. */
    @Volatile var currentFps: Float = 0f

    val log: LogConfig = LogConfig()
}
