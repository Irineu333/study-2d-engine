package com.neoutils.engine.serialization

import com.neoutils.engine.scene.Node
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

/**
 * Saves a scene tree to and loads it from a JSON document. The serialized
 * shape is `SceneFile` over `NodeEntry`: each node carries an identifier (the
 * value registered in [NodeRegistry] for its class), its `name`, a unified
 * `properties` map that holds both `@Inspect` properties of the Node and
 * exports of any attached script, and its children in order.
 *
 * Load returns the **root `Node`** detached and `isLive == false`. The root may
 * be any concrete `Node` subtype registered in `NodeRegistry`. The caller is
 * expected to wrap the result in `SceneTree(root = ...)` and call `start()`
 * when ready, so deferred setup (e.g. resource binding) has a chance to run
 * first.
 */
object SceneLoader {

    private const val NAME_PROPERTY = "name"
    private const val SUPPORTED_VERSION = 2

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun save(
        root: Node,
        serializeScriptExports: ((Node) -> Map<String, JsonElement>?)? = null,
    ): String {
        val rootEntry = nodeToEntry(root, serializeScriptExports)
        return json.encodeToString(
            SceneFile.serializer(),
            SceneFile(version = SUPPORTED_VERSION, root = rootEntry),
        )
    }

    fun load(
        text: String,
        attachScript: ((node: Node, scriptPath: String) -> ScriptAttachment?)? = null,
    ): Node {
        val file = json.decodeFromString(SceneFile.serializer(), text)
        if (file.version != SUPPORTED_VERSION) {
            error(
                "SceneFile version ${file.version} is not supported. " +
                    "This loader requires version $SUPPORTED_VERSION " +
                    "(field 'props' was merged into 'properties' in change " +
                    "godot-style-properties). Migrate manually."
            )
        }
        return entryToNode(file.root, attachScript, path = "/${file.root.name}")
    }

    private fun nodeToEntry(
        node: Node,
        serializeScriptExports: ((Node) -> Map<String, JsonElement>?)?,
    ): NodeEntry {
        val typeName = NodeRegistry.identifierFor(node::class)
            ?: node::class.qualifiedName
            ?: error("Node type has no qualified name (anonymous?): ${node::class}")
        val children = node.children.map { nodeToEntry(it, serializeScriptExports) }
        val merged = linkedMapOf<String, JsonElement>()
        merged.putAll(extractInspectProperties(node))
        if (node.scriptInstance != null) {
            val exports = serializeScriptExports?.invoke(node)
            if (exports != null) merged.putAll(exports)
        }
        return NodeEntry(
            type = typeName,
            name = node.name,
            properties = JsonObject(merged),
            children = children,
            script = node.scriptPath,
        )
    }

    private fun extractInspectProperties(node: Node): JsonObject {
        val out = linkedMapOf<String, JsonElement>()
        val klass = node::class
        for (property in klass.memberProperties) {
            property.findAnnotation<Inspect>() ?: continue
            if (property.name == NAME_PROPERTY) continue
            @Suppress("UNCHECKED_CAST")
            val getter = property as kotlin.reflect.KProperty1<Any, Any?>
            val value = getter.get(node)
            val serializer = json.serializersModule.serializer(property.returnType)
            out[property.name] = json.encodeToJsonElement(serializer, value)
        }
        return JsonObject(out)
    }

    private fun entryToNode(
        entry: NodeEntry,
        attachScript: ((node: Node, scriptPath: String) -> ScriptAttachment?)?,
        path: String,
    ): Node {
        val node = NodeRegistry.create(entry.type)
        node.name = entry.name
        val attachment: ScriptAttachment? = entry.script?.let { scriptPath ->
            if (attachScript == null) {
                error(
                    "Node '${entry.name}' (path '$path') references script " +
                        "'$scriptPath' but no attachScript host was provided to SceneLoader.load."
                )
            }
            val a = attachScript.invoke(node, scriptPath)
            node.scriptInstance = a?.instance
            node.scriptPath = scriptPath
            a
        }
        routeAndApplyProperties(node, attachment, entry.properties, path, entry.script)
        for (child in entry.children) {
            node.addChild(entryToNode(child, attachScript, "$path/${child.name}"))
        }
        return node
    }

    private fun routeAndApplyProperties(
        node: Node,
        attachment: ScriptAttachment?,
        properties: JsonObject,
        path: String,
        scriptPath: String?,
    ) {
        val klass = node::class
        val inspectMutables = linkedMapOf<String, KMutableProperty1<*, *>>()
        val inspectNames = linkedSetOf<String>()
        for (property in klass.memberProperties) {
            property.findAnnotation<Inspect>() ?: continue
            inspectNames.add(property.name)
            val mutable = property as? KMutableProperty1<*, *> ?: continue
            inspectMutables[property.name] = mutable
        }
        val exportNames: Set<String> = attachment?.exportNames ?: emptySet()

        for ((key, element) in properties) {
            val isInspect = key in inspectNames
            val isExport = key in exportNames
            when {
                isInspect && isExport -> {
                    error(
                        "Property '$key' on node '${node.name}' (path '$path') is declared " +
                            "both as @Inspect on ${klass.qualifiedName} and as an export in " +
                            "$scriptPath. Property names must be unique across Node and script."
                    )
                }
                isInspect -> {
                    if (key == NAME_PROPERTY) continue
                    val mutable = inspectMutables[key] ?: continue
                    val serializer = json.serializersModule.serializer(mutable.returnType)
                    val value = try {
                        json.decodeFromJsonElement(serializer, element)
                    } catch (e: Exception) {
                        val targetClass = mutable.returnType.classifier as? kotlin.reflect.KClass<*>
                        val javaClass = targetClass?.java
                        if (javaClass != null && javaClass.isEnum) {
                            @Suppress("UNCHECKED_CAST")
                            val entries = (javaClass.enumConstants as Array<Enum<*>>)
                            val raw = (element as? kotlinx.serialization.json.JsonPrimitive)?.content ?: element.toString()
                            error(
                                "Cannot apply property '$key' on node '${node.name}' (path '$path'): " +
                                    "value '$raw' is not a valid ${javaClass.simpleName} " +
                                    "(valid: ${entries.joinToString(", ") { it.name }})"
                            )
                        }
                        error(
                            "Cannot apply property '$key' on node '${node.name}' (path '$path') " +
                                "to ${mutable.returnType}: ${e.message}"
                        )
                    }
                    @Suppress("UNCHECKED_CAST")
                    (mutable as KMutableProperty1<Any, Any?>).set(node, value)
                }
                isExport -> {
                    try {
                        attachment!!.applyExport(key, element)
                    } catch (e: Exception) {
                        error(
                            "Cannot apply export '$key' on node '${node.name}' (path '$path'): " +
                                "${e.message}"
                        )
                    }
                }
                else -> {
                    val inspectMsg = inspectNames.sorted().toString()
                    val scriptMsg = if (attachment != null && scriptPath != null) {
                        "Candidates from exports in $scriptPath: " +
                            exportNames.sorted().toString() + "."
                    } else {
                        "No script attached."
                    }
                    error(
                        "Unknown property '$key' on node '${node.name}' (path '$path'). " +
                            "Candidates from @Inspect on ${klass.qualifiedName}: $inspectMsg. " +
                            scriptMsg
                    )
                }
            }
        }
    }
}
