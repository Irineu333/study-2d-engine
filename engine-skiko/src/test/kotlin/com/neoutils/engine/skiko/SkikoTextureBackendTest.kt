package com.neoutils.engine.skiko

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class SkikoTextureBackendTest {

    @Test
    fun `load returns a handle with the bitmap dimensions`() {
        val backend = SkikoTextureBackend()
        try {
            val tex = backend.load("textures/checker.png")
            assertEquals(6, tex.width)
            assertEquals(4, tex.height)
        } finally {
            backend.dispose()
        }
    }

    @Test
    fun `load caches by path`() {
        val backend = SkikoTextureBackend()
        try {
            assertSame(backend.load("textures/checker.png"), backend.load("textures/checker.png"))
        } finally {
            backend.dispose()
        }
    }

    @Test
    fun `missing asset fails fast`() {
        val backend = SkikoTextureBackend()
        try {
            assertFailsWith<IllegalStateException> { backend.load("textures/does-not-exist.png") }
        } finally {
            backend.dispose()
        }
    }
}
