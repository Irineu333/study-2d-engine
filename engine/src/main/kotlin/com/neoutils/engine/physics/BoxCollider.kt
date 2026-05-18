package com.neoutils.engine.physics

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2

open class BoxCollider(var size: Vec2) : Collider() {

    override fun bounds(): Rect = Rect(worldPosition(), size)
}
