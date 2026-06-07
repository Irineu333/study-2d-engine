package com.neoutils.engine.bundle.lua

import org.junit.Test
import kotlin.test.assertTrue

class LuaTileMapBindingTest {

    @Test
    fun `nengine TileMap resolves to a non-nil binding`() {
        val host = LuaScriptHost.create()
        val tileMap = host.globals.get("nengine").get("TileMap")
        assertTrue(!tileMap.isnil(), "expected nengine.TileMap to be bound, got nil")
    }

    @Test
    fun `nengine TileSet resolves to a non-nil binding`() {
        val host = LuaScriptHost.create()
        val tileSet = host.globals.get("nengine").get("TileSet")
        assertTrue(!tileSet.isnil(), "expected nengine.TileSet to be bound, got nil")
    }
}
