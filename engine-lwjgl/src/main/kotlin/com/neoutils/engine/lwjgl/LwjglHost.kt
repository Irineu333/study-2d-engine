package com.neoutils.engine.lwjgl

import com.neoutils.engine.audio.javasound.JavaSoundAudio
import com.neoutils.engine.dx.Log
import com.neoutils.engine.loop.GameLoop
import com.neoutils.engine.physics.PhysicsSystem
import com.neoutils.engine.render.Color
import com.neoutils.engine.runtime.GameConfig
import com.neoutils.engine.runtime.GameHost
import com.neoutils.engine.tree.SceneTree
import org.lwjgl.glfw.Callbacks
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.NULL

/**
 * `GameHost` backed by a GLFW window over an OpenGL 3.3 core context.
 * Runs the render loop on the calling thread — on macOS this thread MUST be
 * the process's main thread (started with `-XstartOnFirstThread`), because
 * GLFW binds to Cocoa via `NSApp` which is main-thread-only. The Gradle task
 * `runLwjgl` injects that JVM flag automatically; manual `java -cp` callers
 * need it too. See Decision 3 in `openspec/changes/engine-lwjgl/design.md`.
 */
class LwjglHost : GameHost {

    override fun run(tree: SceneTree, config: GameConfig) {
        GLFWErrorCallback.createPrint(System.err).set()
        check(GLFW.glfwInit()) { "Failed to initialize GLFW" }

        var window: Long = NULL
        try {
            GLFW.glfwDefaultWindowHints()
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3)
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE)
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)

            window = GLFW.glfwCreateWindow(config.width, config.height, config.title, NULL, NULL)
            check(window != NULL) { "Failed to create GLFW window" }

            GLFW.glfwMakeContextCurrent(window)
            GL.createCapabilities()
            GLFW.glfwSwapInterval(1)

            val input = LwjglInput()
            GLFW.glfwSetKeyCallback(window) { _, key, _, action, _ -> input.onGlfwKey(key, action) }
            GLFW.glfwSetMouseButtonCallback(window) { _, button, action, _ -> input.onGlfwMouseButton(button, action) }
            GLFW.glfwSetCursorPosCallback(window) { _, x, y -> input.onGlfwCursorPos(x.toFloat(), y.toFloat()) }
            GLFW.glfwSetScrollCallback(window) { _, xoffset, yoffset -> input.onGlfwScroll(xoffset.toFloat(), yoffset.toFloat()) }

            val renderer = LwjglRenderer()
            renderer.init()
            val physics = PhysicsSystem()
            val loop = GameLoop(tree, renderer, input, physics, physicsHz = config.physicsHz)
            tree.debugHudKey = config.debugHudKey
            // Wire off-frame text metrics before the first frame so
            // `Label.localBounds()` resolves even before any draw. Built after
            // `renderer.init()` so the NanoVG context and font are registered.
            tree.textMeasurer = renderer.createTextMeasurer()
            // Same host-agnostic JDK audio backend as SkikoHost. Init failure
            // (headless / no sound device) is tolerated: log and leave
            // `tree.audio` null so `audio?.play(...)` degrades to silence.
            tree.audio = runCatching { JavaSoundAudio() }
                .onFailure { Log.w("LwjglHost", "Audio backend unavailable: ${it.message}") }
                .getOrNull()
            // Texture backend over the same NanoVG context (loop thread). Init
            // failure is tolerated: log and leave `tree.textures` null so
            // sprites degrade to invisible.
            tree.textures = runCatching { renderer.createTextureBackend() }
                .onFailure { Log.w("LwjglHost", "Texture backend unavailable: ${it.message}") }
                .getOrNull()
            var lastNanos = 0L

            GLFW.glfwShowWindow(window)

            while (!GLFW.glfwWindowShouldClose(window)) {
                // `beginTick` clears the per-tick press/click sets BEFORE
                // `glfwPollEvents` fires the callbacks that populate them —
                // GLFW dispatches synchronously inside pollEvents (unlike AWT
                // in SkikoHost, which queues events between frames). Inverting
                // this order silently drops every press inside the same tick.
                input.beginTick()
                GLFW.glfwPollEvents()

                val nanoTime = System.nanoTime()
                val pendingDt = if (lastNanos == 0L) 16_666_666L else nanoTime - lastNanos
                lastNanos = nanoTime

                val (winW, winH) = queryWindowSize(window)
                val (fbW, fbH) = queryFramebufferSize(window)
                val pixelRatio = if (winW > 0) fbW.toFloat() / winW.toFloat() else 1f
                tree.resize(winW.toFloat(), winH.toFloat())

                GL11.glViewport(0, 0, fbW, fbH)
                renderer.bind(winW, winH, pixelRatio)
                try {
                    // `Renderer.clear` issues glClear directly; safe to invoke
                    // inside the NanoVG frame because NanoVG only flushes its
                    // accumulated draws at `nvgEndFrame` (in `unbind`).
                    renderer.clear(Color.BLACK)
                    loop.tick(pendingDt)
                } finally {
                    renderer.unbind()
                }
                GLFW.glfwSwapBuffers(window)
            }

            tree.stop()
            renderer.shutdown()
            Callbacks.glfwFreeCallbacks(window)
            GLFW.glfwDestroyWindow(window)
        } finally {
            GLFW.glfwTerminate()
            GLFW.glfwSetErrorCallback(null)?.free()
        }
    }
}

private fun queryWindowSize(window: Long): Pair<Int, Int> = MemoryStack.stackPush().use { stack ->
    val w = stack.mallocInt(1)
    val h = stack.mallocInt(1)
    GLFW.glfwGetWindowSize(window, w, h)
    w.get(0) to h.get(0)
}

private fun queryFramebufferSize(window: Long): Pair<Int, Int> = MemoryStack.stackPush().use { stack ->
    val w = stack.mallocInt(1)
    val h = stack.mallocInt(1)
    GLFW.glfwGetFramebufferSize(window, w, h)
    w.get(0) to h.get(0)
}
