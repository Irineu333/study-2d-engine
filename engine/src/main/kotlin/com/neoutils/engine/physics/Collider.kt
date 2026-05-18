package com.neoutils.engine.physics

import com.neoutils.engine.math.Rect
import com.neoutils.engine.scene.Node2D

abstract class Collider : Node2D() {

    abstract fun bounds(): Rect

    open fun onCollide(other: Collider) {}
}
