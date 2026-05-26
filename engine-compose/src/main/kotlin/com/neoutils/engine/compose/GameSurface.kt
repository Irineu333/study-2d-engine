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
import com.neoutils.engine.dx.renderDebugOverlay
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.loop.GameLoop
import com.neoutils.engine.physics.PhysicsSystem
import com.neoutils.engine.runtime.GameConfig
import com.neoutils.engine.tree.SceneTree

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameSurface(
    tree: SceneTree,
    modifier: Modifier = Modifier,
    config: GameConfig = GameConfig(),
) {
    val input = remember { ComposeInput() }
    val physics = remember { PhysicsSystem() }
    val textMeasurer = rememberTextMeasurer()
    val renderer = remember(textMeasurer) { ComposeRenderer(textMeasurer) }
    // `loop` MUST share `renderer`'s remember key. Without this, a density
    // change (e.g. dragging the window across monitors with different DPI)
    // re-creates the renderer while `loop` keeps capturing the previous
    // instance. The Canvas lambda then binds the new renderer's DrawScope
    // but `loop.tick` draws into the old (unbound) one and crashes with
    // "ComposeRenderer used outside a DrawScope".
    val loop = remember(tree, renderer) { GameLoop(tree, renderer, input, physics, physicsHz = config.physicsHz) }
    val fps = remember { FpsCounter() }
    val focusRequester = remember { FocusRequester() }

    var frameNanos by remember { mutableStateOf(0L) }
    var pendingDt by remember { mutableStateOf(0L) }

    LaunchedEffect(tree) {
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

    DisposableEffect(tree) {
        onDispose { tree.stop() }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { input.onKeyEvent(it) }
            .onSizeChanged { tree.resize(it.width.toFloat(), it.height.toFloat()) }
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
        // resizing the tree from inside DrawScope.
        @Suppress("UNUSED_VARIABLE") val recomposeTrigger = frameNanos
        renderer.bind(this)
        try {
            loop.tick(pendingDt)
            if (input.wasKeyPressed(config.toggleFpsKey)) Debug.showFps = !Debug.showFps
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
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

private fun PointerButton.toEngineMouseButton(): MouseButton? = when (this) {
    PointerButton.Primary -> MouseButton.Left
    PointerButton.Secondary -> MouseButton.Right
    PointerButton.Tertiary -> MouseButton.Middle
    else -> null
}
