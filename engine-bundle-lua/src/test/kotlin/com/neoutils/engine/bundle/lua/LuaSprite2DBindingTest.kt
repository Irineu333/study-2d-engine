package com.neoutils.engine.bundle.lua

import org.junit.Test
import kotlin.test.assertTrue

class LuaSprite2DBindingTest {

    @Test
    fun `nengine Sprite2D resolves to a non-nil binding`() {
        val host = LuaScriptHost.create()
        val sprite = host.globals.get("nengine").get("Sprite2D")
        assertTrue(!sprite.isnil(), "expected nengine.Sprite2D to be bound, got nil")
    }
}
