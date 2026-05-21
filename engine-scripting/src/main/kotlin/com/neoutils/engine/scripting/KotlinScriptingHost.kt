package com.neoutils.engine.scripting

import com.neoutils.engine.scene.Node
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.URLClassLoader
import java.security.MessageDigest
import kotlin.reflect.KClass
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

class KotlinScriptingHost(
    private val manifest: List<String>,
    private val cacheDir: File
) : ScriptHost {

    private val version = "1.0"
    private val classesDir = File(cacheDir, "classes").absoluteFile.apply { mkdirs() }
    private val compiledClasses = mutableMapOf<String, KClass<out Node>>()
    private val reverseMapping = mutableMapOf<KClass<out Node>, String>()
    private val compiledWrapperClasses = mutableListOf<String>()
    private val classLoader: URLClassLoader

    init {
        classLoader = URLClassLoader(arrayOf(classesDir.toURI().toURL()), this::class.java.classLoader)
        for (scriptPath in manifest) {
            compileScript(scriptPath)
        }
    }

    override fun compile(path: String): KClass<out Node> {
        return compiledClasses[path] ?: compileScript(path)
    }

    override fun factoryFor(path: String): () -> Node {
        val klass = compile(path)
        val constructor = klass.java.getDeclaredConstructor()
        constructor.isAccessible = true
        return { constructor.newInstance() as Node }
    }

    override fun pathFor(klass: KClass<out Node>): String? {
        return reverseMapping[klass]
    }

    private fun compileScript(path: String): KClass<out Node> {
        val resource = this::class.java.classLoader.getResource(path)
            ?: throw IllegalArgumentException("Script not found: $path")
        val scriptContent = resource.readText()
        val cacheKey = sha256(scriptContent) + "_$version"
        val cacheFile = File(cacheDir, "$cacheKey.bin")

        val filesMap = if (cacheFile.exists()) {
            loadFromCache(cacheFile)
        } else {
            val compiledFiles = performCompilation(path, scriptContent, compiledWrapperClasses)
            saveToCache(cacheFile, compiledFiles)
            compiledFiles
        }

        for ((name, bytes) in filesMap) {
            val destFile = File(classesDir, name)
            destFile.parentFile.mkdirs()
            destFile.writeBytes(bytes)
        }

        val wrapperClassName = filesMap.keys
            .firstOrNull { it.endsWith(".class") && !it.contains("$") }
            ?.removeSuffix(".class")
            ?.replace('/', '.')
            ?: throw IllegalStateException("No wrapper class found in compiled files for script $path")

        compiledWrapperClasses.add(wrapperClassName)

        val nodeClasses = mutableListOf<Class<*>>()
        for (filename in filesMap.keys) {
            if (filename.endsWith(".class")) {
                val className = filename.removeSuffix(".class").replace('/', '.')
                val prefix = "$wrapperClassName$"
                val isDirectNested = className.startsWith(prefix) && 
                                     !className.substring(prefix.length).contains("$")
                if (isDirectNested) {
                    val loaded = classLoader.loadClass(className)
                    if (Node::class.java.isAssignableFrom(loaded)) {
                        nodeClasses.add(loaded)
                    }
                }
            }
        }

        if (nodeClasses.isEmpty()) {
            throw IllegalStateException("Script $path contains zero top-level classes extending Node")
        }
        if (nodeClasses.size > 1) {
            throw IllegalStateException("Script $path contains more than one top-level class extending Node: ${nodeClasses.map { it.simpleName }}")
        }

        @Suppress("UNCHECKED_CAST")
        val nodeKClass = nodeClasses[0].kotlin as KClass<out Node>
        compiledClasses[path] = nodeKClass
        reverseMapping[nodeKClass] = path
        return nodeKClass
    }

    private fun performCompilation(path: String, scriptContent: String, wrapperClasses: List<String>): Map<String, ByteArray> {
        val host = BasicJvmScriptingHost()
        val prependedContent = "package scripts\n\n$scriptContent"
        val source = prependedContent.toScriptSource(path)

        val config = ScriptCompilationConfiguration(NEngineScriptCompilationConfiguration) {
            defaultImports(wrapperClasses.map { "$it.*" })
            jvm {
                dependenciesFromCurrentContext(wholeClasspath = true)
                val classpathStr = System.getProperty("java.class.path") ?: ""
                val systemClasspath = classpathStr
                    .split(File.pathSeparator)
                    .map { File(it) }
                    .filter { it.exists() }
                updateClasspath(systemClasspath + classesDir)
            }
        }


        val result = runBlocking { host.compiler(source, config) }
        val errors = result.reports.filter { it.severity == ScriptDiagnostic.Severity.ERROR }
        if (errors.isNotEmpty()) {
            val message = errors.joinToString("\n") { "[${it.severity}] ${it.sourcePath ?: path}:${it.location?.start?.line ?: "?"}:${it.location?.start?.col ?: "?"}: ${it.message}" }
            throw IllegalStateException("Compilation failed for script $path:\n$message")
        }

        val compiled = result.valueOrNull() as? KJvmCompiledScript
            ?: throw IllegalStateException("Compilation returned null or unexpected compiled script type for $path")

        val compiledModuleField = KJvmCompiledScript::class.java.getDeclaredField("compiledModule")
        compiledModuleField.isAccessible = true
        val module = compiledModuleField.get(compiled)
            ?: throw IllegalStateException("KJvmCompiledScript compiledModule was null for $path")

        val getCompilerOutputFilesMethod = module.javaClass.getDeclaredMethod("getCompilerOutputFiles")
        getCompilerOutputFilesMethod.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val files = getCompilerOutputFilesMethod.invoke(module) as? Map<String, ByteArray>
            ?: throw IllegalStateException("Failed to retrieve compiler output files for $path")

        return files
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadFromCache(file: File): Map<String, ByteArray> {
        return ObjectInputStream(FileInputStream(file)).use {
            it.readObject() as Map<String, ByteArray>
        }
    }

    private fun saveToCache(file: File, compiledFiles: Map<String, ByteArray>) {
        file.parentFile.mkdirs()
        val tempFile = File.createTempFile("compile-cache-", ".tmp", file.parentFile)
        try {
            ObjectOutputStream(FileOutputStream(tempFile)).use {
                it.writeObject(compiledFiles)
            }
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }
}
