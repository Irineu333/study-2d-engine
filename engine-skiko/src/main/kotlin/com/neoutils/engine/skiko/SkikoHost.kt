package com.neoutils.engine.skiko

import com.neoutils.engine.dx.Debug
import com.neoutils.engine.dx.FpsCounter
import com.neoutils.engine.dx.renderDebugOverlay
import com.neoutils.engine.loop.GameLoop
import com.neoutils.engine.physics.PhysicsSystem
import com.neoutils.engine.render.Color
import com.neoutils.engine.runtime.GameConfig
import com.neoutils.engine.runtime.GameHost
import com.neoutils.engine.tree.SceneTree
import org.jetbrains.skia.Canvas
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.CountDownLatch
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * `GameHost` backed by Skiko's `SkiaLayer` embedded in a Swing `JFrame`. Each
 * `SkikoView.onRender(...)` callback drives one tick of the engine loop, then
 * calls `skiaLayer.needRedraw()` to schedule the next frame. Blocking is
 * achieved through a `CountDownLatch` released when the window disposes.
 */
class SkikoHost : GameHost {

    override fun run(tree: SceneTree, config: GameConfig) {
        val latch = CountDownLatch(1)

        SwingUtilities.invokeLater {
            val frame = JFrame(config.title).apply {
                defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
                preferredSize = Dimension(config.width, config.height)
                layout = BorderLayout()
            }

            val input = SkikoInput()
            val renderer = SkikoRenderer()
            val physics = PhysicsSystem()
            val loop = GameLoop(tree, renderer, input, physics, physicsHz = config.physicsHz)
            val fps = FpsCounter()

            val skiaLayer = SkiaLayer()
            var lastNanos = 0L

            skiaLayer.renderDelegate = SkikoRenderDelegate { canvas, width, height, nanoTime ->
                val pendingDt = if (lastNanos == 0L) 16_666_666L else nanoTime - lastNanos
                lastNanos = nanoTime

                input.beginTick()
                Debug.currentFps = fps.record(nanoTime)
                tree.resize(width.toFloat(), height.toFloat())
                renderer.bind(canvas)
                try {
                    renderer.clear(Color.BLACK)
                    loop.tick(pendingDt)
                    if (input.wasKeyPressed(config.toggleFpsKey)) {
                        Debug.showFps = !Debug.showFps
                    }
                    if (input.wasKeyPressed(config.toggleCollidersKey)) {
                        Debug.colliderVisualization = !Debug.colliderVisualization
                    }
                    if (input.wasKeyPressed(config.toggleMomentumOverlayKey)) {
                        Debug.showMomentumOverlay = !Debug.showMomentumOverlay
                        if (Debug.showMomentumOverlay) com.neoutils.engine.dx.MomentumOverlay.reset()
                    }
                    renderDebugOverlay(renderer, tree)
                } finally {
                    renderer.unbind()
                }
                skiaLayer.needRedraw()
            }

            skiaLayer.isFocusable = true
            frame.contentPane.add(skiaLayer, BorderLayout.CENTER)

            val keyListener = object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) = input.onAwtKey(e, pressed = true)
                override fun keyReleased(e: KeyEvent) = input.onAwtKey(e, pressed = false)
            }
            frame.addKeyListener(keyListener)
            skiaLayer.addKeyListener(keyListener)
            skiaLayer.addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    skiaLayer.requestFocusInWindow()
                    input.onAwtMouseButton(e, pressed = true)
                }
                override fun mouseReleased(e: MouseEvent) = input.onAwtMouseButton(e, pressed = false)
            })
            skiaLayer.addMouseMotionListener(object : MouseMotionAdapter() {
                override fun mouseMoved(e: MouseEvent) = input.onAwtMouseMoved(e, skiaLayer.contentScale)
                override fun mouseDragged(e: MouseEvent) = input.onAwtMouseMoved(e, skiaLayer.contentScale)
            })

            frame.addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent) {
                    tree.stop()
                    skiaLayer.dispose()
                    latch.countDown()
                }
            })

            frame.pack()
            frame.setLocationRelativeTo(null)
            frame.isVisible = true
            frame.requestFocus()
            skiaLayer.requestFocusInWindow()
            skiaLayer.needRedraw()
        }

        latch.await()
    }
}
