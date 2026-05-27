package com.neoutils.engine.input

import com.neoutils.engine.math.Vec2

interface Input {

    /**
     * Pointer position in **the same coordinate space as `SceneTree.size`**.
     * Backends MUST keep these two aligned — game code uses
     * `tree.screenToWorld(input.pointerPosition)` and assumes they live in the
     * same units. Today both happen to be in physical pixels on `:engine-skiko`
     * (mouse × `SkiaLayer.contentScale`, surface = render buffer dimensions);
     * the implicit "physical pixels" contract is a known debt to be formalized
     * in a future `surface-units-spec` change (logical units everywhere with
     * HiDPI absorbed at the backend edge). Until then, new backends must
     * mirror the alignment.
     */
    val pointerPosition: Vec2

    fun isKeyDown(key: Key): Boolean

    fun wasKeyPressed(key: Key): Boolean

    fun isMouseDown(button: MouseButton): Boolean

    fun wasMouseClicked(button: MouseButton): Boolean
}
