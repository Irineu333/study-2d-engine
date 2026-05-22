package com.neoutils.engine.bundle.python

import com.neoutils.engine.bundle.script.*
import com.neoutils.engine.input.Key
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.BoxCollider
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.serialization.NodeRef
import com.neoutils.engine.serialization.NodeRegistry
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import kotlin.reflect.KClass

class PythonScriptHost private constructor(private val context: Context) : ScriptHost {

    override val extension = ".py"

    private val moduleCache = mutableMapOf<String, Value>()

    private val loadModuleFn: Value
    private val inspectFn: Value
    private val parseExtendsFn: Value
    private val createInstanceFn: Value

    init {
        val bindings = context.getBindings("python")
        bindings.putMember("Vec2", Vec2::class.java)
        bindings.putMember("Color", Color::class.java)
        bindings.putMember("Rect", Rect::class.java)
        bindings.putMember("NodeRef", NodeRef::class.java)
        bindings.putMember("Key", Key::class.java)
        bindings.putMember("BoxCollider", BoxCollider::class.java)
        bindings.putMember("Node2D", Node2D::class.java)

        val runtimeCode = PythonScriptHost::class.java.getResourceAsStream("/_nengine_runtime.py")
            ?.readBytes()?.toString(Charsets.UTF_8)
            ?: error("Missing _nengine_runtime.py classpath resource")

        context.eval(Source.newBuilder("python", runtimeCode, "_nengine_runtime.py").build())

        loadModuleFn = bindings.getMember("_nengine_load_module")
        inspectFn = bindings.getMember("_nengine_inspect")
        parseExtendsFn = bindings.getMember("_nengine_parse_extends")
        createInstanceFn = bindings.getMember("_nengine_create_instance")
    }

    override fun load(path: String, bundle: BundleSource): Script {
        val source = bundle.read(path)

        val typeName = parseExtendsFn.execute(source).let { result ->
            if (result.isNull) throw MissingExtendsDeclarationException(path)
            result.asString()
        }

        val extendsType = NodeRegistry.findBySimpleName(typeName)
            ?: throw UnknownExtendsTypeException(typeName, path)

        val exports = buildExports(source, path)

        val moduleNs = loadModuleFn.execute(source, path)
        moduleCache[path] = moduleNs

        return ScriptData(path, extendsType, exports)
    }

    private fun buildExports(source: String, path: String): List<ExportedProperty> {
        val exportsValue = inspectFn.execute(source)
        if (!exportsValue.hasArrayElements()) return emptyList()
        val result = mutableListOf<ExportedProperty>()
        for (i in 0 until exportsValue.arraySize) {
            val entry = exportsValue.getArrayElement(i)
            val name = entry.getMember("name").asString()
            val typeName = entry.getMember("type_name").asString()
            val nullable = entry.getMember("nullable").asBoolean()
            val defaultRaw = entry.getMember("default")
            val kotlinType = kotlinTypeFor(typeName)
            val default = if (defaultRaw.isNull) null else valueToKotlin(defaultRaw, kotlinType)
            result += ExportedProperty(name, kotlinType, default)
        }
        return result
    }

    private fun valueToKotlin(value: Value, type: KClass<*>): Any? {
        if (value.isNull) return null
        return when (type) {
            Float::class -> value.asDouble().toFloat()
            Int::class -> value.asInt()
            Boolean::class -> value.asBoolean()
            String::class -> value.asString()
            else -> if (value.isHostObject) value.asHostObject() else null
        }
    }

    override fun attach(node: Node, script: Script): ScriptInstance {
        val moduleNs = moduleCache[script.path]
            ?: error("Script not loaded before attach: ${script.path}")
        val instance = createInstanceFn.execute(node)
        return PythonScriptInstance(instance, moduleNs)
    }

    companion object {
        fun install() {
            val context = Context.newBuilder("python")
                .allowAllAccess(true)
                .allowExperimentalOptions(true)
                .option("python.PosixModuleBackend", "java")
                .build()
            ScriptHostRegistry.register(PythonScriptHost(context))
        }
    }
}

private data class ScriptData(
    override val path: String,
    override val extendsType: KClass<out Node>,
    override val exports: List<ExportedProperty>,
) : Script

private class PythonScriptInstance(
    private val instance: Value,
    private val moduleNs: Value,
) : ScriptInstance {

    override fun setExport(name: String, value: Any?) {
        instance.putMember(name, value)
    }

    override fun onEnter() {
        callHook("on_enter")
    }

    override fun onUpdate(dt: Float) {
        callHook("on_update", dt)
    }

    override fun onRender(renderer: Renderer) {
        callHook("on_render", renderer)
    }

    override fun onCollide(other: Node) {
        callHook("on_collide", other)
    }

    private fun callHook(name: String, vararg args: Any?) {
        if (!moduleNs.hasMember(name)) return
        val fn = moduleNs.getMember(name)
        if (fn.isNull || !fn.canExecute()) return
        fn.execute(instance, *args)
    }
}

// exception types ---------------------------------------------------------

class MissingExtendsDeclarationException(path: String) :
    RuntimeException("Script '$path' is missing an '# extends <NodeType>' declaration on its first non-empty line")

class UnknownExtendsTypeException(typeName: String, path: String) :
    RuntimeException("Script '$path' declares 'extends $typeName' but '$typeName' is not registered in NodeRegistry")

private fun kotlinTypeFor(name: String): KClass<*> = when (name) {
    "Int" -> Int::class
    "Float" -> Float::class
    "Boolean" -> Boolean::class
    "String" -> String::class
    "Vec2" -> Vec2::class
    "Color" -> Color::class
    "Rect" -> Rect::class
    "NodeRef" -> NodeRef::class
    "Key" -> Key::class
    else -> error("Unsupported export type name: $name")
}
