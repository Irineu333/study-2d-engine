package com.neoutils.engine.scripting

import com.neoutils.engine.scene.Node
import java.io.File
import kotlin.reflect.KClass
import kotlin.test.*

class KotlinScriptingHostTest {

    private lateinit var tempCacheDir: File

    @BeforeTest
    fun setUp() {
        tempCacheDir = File("build/tmp/test-scripting-cache-${System.nanoTime()}")
        tempCacheDir.mkdirs()
    }

    @AfterTest
    fun tearDown() {
        tempCacheDir.deleteRecursively()
    }

    @Test
    fun testSmoke() {
        val host = KotlinScriptingHost(emptyList(), tempCacheDir)
        val klass = host.compile("scripts/hello.nengine.kts")
        assertEquals("HelloNode", klass.simpleName)

        val factory = host.factoryFor("scripts/hello.nengine.kts")
        val instance = factory()
        assertNotNull(instance)
        assertTrue(instance is Node)
        assertEquals("HelloNode", instance.name)

        assertEquals("scripts/hello.nengine.kts", host.pathFor(klass))
    }

    @Test
    fun testMissingScript() {
        val host = KotlinScriptingHost(emptyList(), tempCacheDir)
        assertFailsWith<IllegalArgumentException> {
            host.compile("scripts/does_not_exist.nengine.kts")
        }
    }

    @Test
    fun testSyntaxError() {
        val host = KotlinScriptingHost(emptyList(), tempCacheDir)
        assertFailsWith<IllegalStateException> {
            host.compile("scripts/syntax_error.nengine.kts")
        }
    }

    @Test
    fun testZeroClasses() {
        val host = KotlinScriptingHost(emptyList(), tempCacheDir)
        val ex = assertFailsWith<IllegalStateException> {
            host.compile("scripts/zero_classes.nengine.kts")
        }
        assertTrue(ex.message!!.contains("contains zero top-level classes extending Node"))
    }

    @Test
    fun testTwoClasses() {
        val host = KotlinScriptingHost(emptyList(), tempCacheDir)
        val ex = assertFailsWith<IllegalStateException> {
            host.compile("scripts/two_classes.nengine.kts")
        }
        assertTrue(ex.message!!.contains("contains more than one top-level class extending Node"))
    }

    @Test
    fun testNonNode() {
        val host = KotlinScriptingHost(emptyList(), tempCacheDir)
        val ex = assertFailsWith<IllegalStateException> {
            host.compile("scripts/non_node.nengine.kts")
        }
        assertTrue(ex.message!!.contains("contains zero top-level classes extending Node"))
    }

    @Test
    fun testCacheHit() {
        // Compile first time
        val host1 = KotlinScriptingHost(emptyList(), tempCacheDir)
        val klass1 = host1.compile("scripts/hello.nengine.kts")
        
        // Find cache file
        val cacheFiles = tempCacheDir.listFiles { _, name -> name.endsWith(".bin") }
        assertNotNull(cacheFiles)
        assertEquals(1, cacheFiles.size)
        val cacheFile = cacheFiles[0]
        
        // Compile second time using another host and cache
        val host2 = KotlinScriptingHost(emptyList(), tempCacheDir)
        val klass2 = host2.compile("scripts/hello.nengine.kts")
        assertEquals(klass1.simpleName, klass2.simpleName)
    }

    @Test
    fun testManifestOrderSuccess() {
        // dep_b extends DepA, so we must register both in order: dep_a then dep_b
        val manifest = listOf("scripts/dep_a.nengine.kts", "scripts/dep_b.nengine.kts")
        val host = KotlinScriptingHost(manifest, tempCacheDir)

        val bKlass = host.compile("scripts/dep_b.nengine.kts")
        assertEquals("DepB", bKlass.simpleName)

        val factory = host.factoryFor("scripts/dep_b.nengine.kts")
        val instance = factory()
        
        // Use reflection to invoke runTest() on DepB
        val method = instance.javaClass.getMethod("runTest")
        val result = method.invoke(instance) as Int
        assertEquals(42, result)
    }

    @Test
    fun testManifestOrderFailure() {
        // If we compile dep_b without dep_a on classpath, it should fail compilation
        val host = KotlinScriptingHost(emptyList(), tempCacheDir)
        assertFailsWith<IllegalStateException> {
            host.compile("scripts/dep_b.nengine.kts")
        }
    }
}
