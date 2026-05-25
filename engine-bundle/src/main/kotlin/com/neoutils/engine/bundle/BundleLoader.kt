package com.neoutils.engine.bundle

import com.neoutils.engine.bundle.script.BundleSource
import com.neoutils.engine.bundle.script.ClasspathBundleSource
import com.neoutils.engine.bundle.script.DirectoryBundleSource
import com.neoutils.engine.bundle.script.PropCoercion
import com.neoutils.engine.bundle.script.Script
import com.neoutils.engine.bundle.script.ScriptHost
import com.neoutils.engine.scene.Node
import com.neoutils.engine.serialization.NodeEntry
import com.neoutils.engine.serialization.NodeRegistry
import com.neoutils.engine.serialization.SceneFile
import com.neoutils.engine.serialization.SceneLoader
import com.neoutils.engine.serialization.ScriptAttachment
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.reflect.KClass

/**
 * Loads a scene bundle (a folder containing `scene.json`, an optional
 * `scripts/` directory and a reserved `assets/` directory) and returns the
 * detached root [Node]. The caller wraps the result in `SceneTree(root = ...)`
 * to make the tree live. Two entry points cover the deployment scenarios:
 * [fromResources] resolves a bundle baked into the JVM classpath, and
 * [fromPath] resolves a bundle that lives on the filesystem (the editor
 * scenario).
 *
 * Scripts are discovered via the `NodeEntry.script` field and dispatched to
 * the [ScriptHost] passed via the `scripting` parameter. The legacy
 * `.nengine.kts` path was removed in E8 of `add-python-scripting`.
 */
object BundleLoader {

    private const val SCENE_FILE_NAME = "scene.json"

    /**
     * Loads a scene bundle from the JVM classpath.
     *
     * @param name Bundle name (folder on the classpath containing `scene.json`).
     * @param types Optional list of game-specific [Node] subclasses to register
     *   before parsing the scene.
     * @param scripting Optional [ScriptHost] used to load scripts referenced by
     *   the bundle. Pass `null` for script-less bundles. **Reuse the same
     *   instance across multiple loads** when possible — constructing a host
     *   (e.g. `PythonScriptHost.create()`) typically incurs significant cost
     *   (such as GraalPy `Context` boot), and the host is safe to share.
     *   When the bundle references at least one `script` but `scripting` is
     *   `null`, the loader fails fast naming the offending path.
     */
    fun fromResources(
        name: String,
        types: List<KClass<out Node>> = emptyList(),
        scripting: ScriptHost? = null,
    ): Node {
        val resource = this::class.java.classLoader.getResource("$name/$SCENE_FILE_NAME")
            ?: throw IllegalArgumentException(
                "Bundle '$name' not found on classpath (missing $name/$SCENE_FILE_NAME)"
            )
        val sceneJson = resource.readText()
        return load(
            bundleSource = ClasspathBundleSource(bundleRoot = name),
            sceneJsonText = sceneJson,
            types = types,
            scripting = scripting,
        )
    }

    /**
     * Loads a scene bundle from a filesystem directory (editor scenario).
     *
     * @param bundleDir Directory containing `scene.json`.
     * @param types Optional list of game-specific [Node] subclasses to register
     *   before parsing the scene.
     * @param scripting Optional [ScriptHost] used to load scripts referenced by
     *   the bundle. Pass `null` for script-less bundles. **Reuse the same
     *   instance across multiple loads** when possible — constructing a host
     *   (e.g. `PythonScriptHost.create()`) typically incurs significant cost
     *   (such as GraalPy `Context` boot), and the host is safe to share.
     *   When the bundle references at least one `script` but `scripting` is
     *   `null`, the loader fails fast naming the offending path.
     */
    fun fromPath(
        bundleDir: File,
        types: List<KClass<out Node>> = emptyList(),
        scripting: ScriptHost? = null,
    ): Node {
        if (!bundleDir.isDirectory) {
            throw IllegalArgumentException(
                "Bundle directory not found: ${bundleDir.absolutePath}"
            )
        }
        val sceneFile = File(bundleDir, SCENE_FILE_NAME)
        if (!sceneFile.isFile) {
            throw IllegalArgumentException(
                "Bundle at ${bundleDir.absolutePath} is missing $SCENE_FILE_NAME"
            )
        }
        return load(
            bundleSource = DirectoryBundleSource(bundleDir = bundleDir),
            sceneJsonText = sceneFile.readText(),
            types = types,
            scripting = scripting,
        )
    }

    private fun load(
        bundleSource: BundleSource,
        sceneJsonText: String,
        types: List<KClass<out Node>>,
        scripting: ScriptHost?,
    ): Node {
        NodeRegistry.registerEngineTypes()

        for (klass in types) {
            NodeRegistry.register(klass, buildNoArgFactory(klass))
        }

        val sceneFile = Json.decodeFromString(SceneFile.serializer(), sceneJsonText)

        val scriptPaths = collectScriptPaths(sceneFile.root)
        val scripts = loadScripts(scriptPaths, scripting, bundleSource)

        return SceneLoader.load(sceneJsonText) { node, scriptPath ->
            val script = scripts[scriptPath]
                ?: error("Script '$scriptPath' was collected but not loaded — this is a bug")
            val host = scripting
                ?: error("Script '$scriptPath' was loaded without a ScriptHost — this is a bug")
            val instance = host.attach(node, script)
            ScriptAttachment(
                instance = instance,
                exportNames = script.exports.map { it.name }.toSet(),
                applyExport = { name, jsonEl ->
                    val export = script.exports.first { it.name == name }
                    val value = PropCoercion.coerce(jsonEl, export.type, export.nullable)
                    instance.setExport(name, value)
                },
            )
        }
    }

    private fun loadScripts(
        scriptPaths: Set<String>,
        host: ScriptHost?,
        bundle: BundleSource,
    ): Map<String, Script> {
        if (scriptPaths.isEmpty()) return emptyMap()
        if (host == null) {
            val firstPath = scriptPaths.first()
            error(
                "Bundle references script '$firstPath' but no ScriptHost was provided. " +
                    "Pass `scripting = PythonScriptHost.create()` (or another ScriptHost) to BundleLoader."
            )
        }
        return scriptPaths.associateWith { path ->
            if (!path.endsWith(host.extension)) {
                error(
                    "Bundle references script '$path' but the provided ScriptHost handles " +
                        "'${host.extension}'. Bundle and ScriptHost extensions must match."
                )
            }
            host.load(path, bundle)
        }
    }

    private fun collectScriptPaths(entry: NodeEntry): Set<String> {
        val result = linkedSetOf<String>()
        walkScripts(entry, result)
        return result
    }

    private fun walkScripts(entry: NodeEntry, out: MutableSet<String>) {
        entry.script?.let { out.add(it) }
        for (child in entry.children) walkScripts(child, out)
    }

    private fun buildNoArgFactory(klass: KClass<out Node>): () -> Node {
        val constructor = try {
            klass.java.getDeclaredConstructor()
        } catch (e: NoSuchMethodException) {
            throw IllegalArgumentException(
                "Node type ${klass.qualifiedName ?: klass} has no accessible no-args constructor",
                e
            )
        }
        constructor.isAccessible = true
        return { constructor.newInstance() as Node }
    }
}
