package com.neoutils.engine.bundle.lua

import org.junit.Test
import kotlin.test.assertTrue

class LuaAnimatedSprite2DBindingTest {

    @Test
    fun `nengine AnimatedSprite2D resolves to a non-nil binding`() {
        val host = LuaScriptHost.create()
        val animated = host.globals.get("nengine").get("AnimatedSprite2D")
        assertTrue(!animated.isnil(), "expected nengine.AnimatedSprite2D to be bound, got nil")
    }
}
