package com.neoutils.engine.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.neoutils.engine.dx.Debug
import com.neoutils.engine.dx.FpsCounter
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.loop.GameLoop
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.PhysicsSystem
import com.neoutils.engine.physics.collectColliders
import com.neoutils.engine.render.Color
import com.neoutils.engine.scene.Scene

private val DEBUG_COLLIDER_COLOR: Color = Color(0f, 1f, 0f, 0.8f)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameSurface(
    scene: Scene,
    modifier: Modifier = Modifier,
) {
    val input = remember { ComposeInput() }
    val physics = remember { PhysicsSystem() }
    val textMeasurer = rememberTextMeasurer()
    val renderer = remember(textMeasurer) { ComposeRenderer(textMeasurer) }
    val loop = remember(scene) { GameLoop(scene, renderer, input, physics) }
    val fps = remember { FpsCounter() }
    val focusRequester = remember { FocusRequester() }

    var frameNanos by remember { mutableStateOf(0L) }
    var pendingDt by remember { mutableStateOf(0L) }

    LaunchedEffect(scene) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                pendingDt = if (lastNanos == 0L) 16_666_666L else now - lastNanos
                lastNanos = now
                input.beginTick()
                Debug.currentFps = fps.record(now)
                frameNanos = now
            }
        }
    }

    DisposableEffect(scene) {
        onDispose { scene.stop() }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { input.onKeyEvent(it) }
            .onSizeChanged { scene.resize(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pos = event.changes.firstOrNull()?.position
                        if (pos != null) input.onPointerMove(pos.x, pos.y)
                        val mapped = event.button?.toEngineMouseButton()
                        if (mapped != null) {
                            when (event.type) {
                                PointerEventType.Press -> input.onPointerButton(mapped, pressed = true)
                                PointerEventType.Release -> input.onPointerButton(mapped, pressed = false)
                                else -> Unit
                            }
                        }
                    }
                }
            }
    ) {
        // `frameNanos` is monotonic, so reading it here subscribes the draw
        // block to every withFrameNanos pulse without the side-effect smell of
        // resizing the scene from inside DrawScope.
        @Suppress("UNUSED_VARIABLE") val recomposeTrigger = frameNanos
        renderer.bind(this)
        try {
            loop.tick(pendingDt)
            if (Debug.colliderVisualization) {
                for (collider in collectColliders(scene)) {
                    renderer.drawRect(collider.bounds(), DEBUG_COLLIDER_COLOR, filled = false)
                }
            }
            if (Debug.showFps) {
                val text = "fps ${Debug.currentFps.toInt()}"
                renderer.drawText(text, Vec2(8f, 24f), size = 18f, color = Color.WHITE)
            }
        } finally {
            renderer.unbind()
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

private fun PointerButton.toEngineMouseButton(): MouseButton? = when (this) {
    PointerButton.Primary -> MouseButton.Left
    PointerButton.Secondary -> MouseButton.Right
    PointerButton.Tertiary -> MouseButton.Middle
    else -> null
}
