package com.neoutils.engine.scene

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2

open class Node2D : Node() {

    var transform: Transform = Transform()

    fun worldPosition(): Vec2 {
        var accumX = 0f
        var accumY = 0f
        var current: Node? = this
        while (current != null) {
            if (current is Node2D) {
                accumX += current.transform.position.x
                accumY += current.transform.position.y
            }
            current = current.parent
        }
        return Vec2(accumX, accumY)
    }
}
