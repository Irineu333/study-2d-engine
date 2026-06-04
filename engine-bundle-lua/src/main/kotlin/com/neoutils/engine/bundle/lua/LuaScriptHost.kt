package com.neoutils.engine.bundle.lua

import com.neoutils.engine.bundle.script.BundleSource
import com.neoutils.engine.bundle.script.ExportedProperty
import com.neoutils.engine.bundle.script.Script
import com.neoutils.engine.bundle.script.ScriptHost
import com.neoutils.engine.bundle.script.ScriptInstance
import com.neoutils.engine.bundle.script.SignalDeclaration
import com.neoutils.engine.input.Key
import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.Area2D
import com.neoutils.engine.physics.CharacterBody2D
import com.neoutils.engine.physics.CircleShape2D
import com.neoutils.engine.physics.CollisionObject2D
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.PhysicsBody2D
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.physics.RigidBody2D
import com.neoutils.engine.physics.Shape2D
import com.neoutils.engine.physics.StaticBody2D
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.Camera2D
import com.neoutils.engine.scene.CanvasLayer
import com.neoutils.engine.scene.Control
import com.neoutils.engine.scene.LayoutPreset
import com.neoutils.engine.scene.MouseFilter
import com.neoutils.engine.scene.Panel
import com.neoutils.engine.scene.Circle2D
import com.neoutils.engine.scene.ColorRect
import com.neoutils.engine.scene.Label
import com.neoutils.engine.scene.Line2D
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.scene.Polygon2D
import com.neoutils.engine.scene.Timer
import com.neoutils.engine.serialization.NodeRef
import com.neoutils.engine.serialization.NodeRegistry
import com.neoutils.engine.serialization.Signal
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.CoerceLuaToJava
import org.luaj.vm2.lib.jse.JsePlatform
import kotlin.reflect.KClass

class LuaScriptHost internal constructor(internal val globals: Globals) : ScriptHost {

    override val extension: String = ".lua"

    // Per-path cached chunk table (the result of executing the .lua file once
    // at load time). Reused for each `attach` to construct fresh instances.
    private val chunkCache = mutableMapOf<String, LuaTable>()

    // Holds the BundleSource of the most recent `load` call so the
    // `package.searchers` bridge can satisfy `require "scripts.utils"`
    // against the same bundle the loader is operating on.
    internal var activeBundle: BundleSource? = null

    // Indexes the instance LuaTable by host Node so `nengine.script_of(node)`
    // can return the wrapper for cross-script communication.
    private val instanceByNode = mutableMapOf<Node, LuaTable>()

    // Cache of `_ScriptlessProxy`-equivalent tables for Nodes without an
    // attached script: returned by `script_of(scriptlessNode)` so callers can
    // still reach Kotlin-declared `Signal<*>` fields uniformly.
    private val bareWrapperByNode = mutableMapOf<Node, LuaTable>()

    // Cache of (node, fieldName) -> signal proxy. Required so that
    // `disconnect(handler)` can locate the same proxy instance that
    // `connect(handler)` produced.
    private val signalProxyCache = mutableMapOf<Pair<Node, String>, LuaTable>()

    init {
        installNengine()
        installBundleSearchers()
    }

    // ---------------------------------------------------------------- ScriptHost

    override fun load(path: String, bundle: BundleSource): Script {
        activeBundle = bundle
        val source = bundle.read(path)
        val chunk = try {
            globals.load(source, path, globals)
        } catch (err: LuaError) {
            throw LuaScriptCompileException(path, err)
        }
        val returned: LuaValue = chunk.call()
        if (!returned.istable()) {
            throw LuaScriptContractException(
                "Script '$path' must `return { ... }` a table; got ${returned.typename()}"
            )
        }
        val table = returned.checktable()
        val extendsValue = table.get("extends")
        if (!extendsValue.isstring()) {
            throw LuaScriptContractException(
                "Script '$path' is missing a string 'extends' field (got ${extendsValue.typename()})"
            )
        }
        val extendsName = extendsValue.tojstring()
        val extendsType = NodeRegistry.findBySimpleName(extendsName)
            ?: throw LuaScriptContractException(
                "Script '$path' declares 'extends \"$extendsName\"' but '$extendsName' is not registered in NodeRegistry"
            )
        val exports = buildExports(table, path)
        val signals = buildSignals(table, path, exports)
        chunkCache[path] = table
        return LuaScriptData(path, extendsType, exports, signals)
    }

    private fun buildExports(table: LuaTable, path: String): List<ExportedProperty> {
        val exportsValue = table.get("exports")
        if (exportsValue.isnil()) return emptyList()
        if (!exportsValue.istable()) {
            throw LuaScriptContractException(
                "Script '$path': 'exports' must be a table (got ${exportsValue.typename()})"
            )
        }
        val exportsTable = exportsValue.checktable()
        val result = mutableListOf<ExportedProperty>()
        var k: LuaValue = LuaValue.NIL
        while (true) {
            val n: Varargs = exportsTable.next(k)
            val key = n.arg1()
            if (key.isnil()) break
            k = key
            if (!key.isstring()) continue
            val name = key.tojstring()
            val descriptor = n.arg(2)
            if (!descriptor.istable()) {
                throw LuaScriptContractException(
                    "Script '$path': export '$name' descriptor must be a table { type = ..., default = ... }"
                )
            }
            val tdescriptor = descriptor.checktable()
            val typeStr = tdescriptor.get("type")
            if (!typeStr.isstring()) {
                throw LuaScriptContractException(
                    "Script '$path': export '$name' is missing 'type' string"
                )
            }
            val type = kotlinTypeFor(typeStr.tojstring(), path, name)
            val defaultRaw = tdescriptor.get("default")
            val default = if (defaultRaw.isnil()) null else luaValueToKotlin(defaultRaw, type)
            result += ExportedProperty(name, type, default, nullable = false)
        }
        return result
    }

    private fun buildSignals(
        table: LuaTable,
        path: String,
        exports: List<ExportedProperty>,
    ): Map<String, SignalDeclaration> {
        val signalsValue = table.get("signals")
        if (signalsValue.isnil()) return emptyMap()
        if (!signalsValue.istable()) {
            throw LuaScriptContractException(
                "Script '$path': 'signals' must be a table (got ${signalsValue.typename()})"
            )
        }
        val signalsTable = signalsValue.checktable()
        val exportNames = exports.map { it.name }.toSet()
        val result = linkedMapOf<String, SignalDeclaration>()
        var k: LuaValue = LuaValue.NIL
        while (true) {
            val n: Varargs = signalsTable.next(k)
            val key = n.arg1()
            if (key.isnil()) break
            k = key
            if (!key.isstring()) continue
            val name = key.tojstring()
            if (name in exportNames) {
                throw LuaScriptContractException(
                    "Script '$path': name '$name' is declared in both exports and signals"
                )
            }
            result[name] = SignalDeclaration(name)
        }
        return result
    }

    override fun attach(node: Node, script: Script): ScriptInstance {
        val chunk = chunkCache[script.path]
            ?: error("Script not loaded before attach: ${script.path}")
        val instance = cloneChunkTable(chunk)
        // Marker so `unwrapNode` can recover the host Node when scripts pass
        // `self` (or `self.node`) into Kotlin APIs like `NodeRef:resolve`.
        instance.rawset(NODE_MARKER_KEY, CoerceJavaToLua.coerce(node))
        val signals = linkedMapOf<String, Signal<*>>()
        for (decl in script.signals.values) {
            val sig = Signal<Any?>()
            signals[decl.name] = sig
            instance.rawset(decl.name, CoerceJavaToLua.coerce(sig))
        }
        for (export in script.exports) {
            instance.rawset(export.name, kotlinToLua(export.default))
        }
        val metatable = LuaTable()
        metatable.set("__index", InstanceIndex(this, node))
        metatable.set("__newindex", InstanceNewIndex(this, node))
        instance.setmetatable(metatable)
        instanceByNode[node] = instance
        return LuaScriptInstance(this, instance, node, script, signals)
    }

    /**
     * Returns a fresh LuaTable cloning every (key, value) pair of [chunk] but
     * without inheriting its metatable. Hooks (`_ready`, `_process`, ...)
     * are functions; they are copied by reference and remain shared across
     * instances — that is the intended Godot-style "method on class" behavior.
     */
    private fun cloneChunkTable(chunk: LuaTable): LuaTable {
        val out = LuaTable()
        var k: LuaValue = LuaValue.NIL
        while (true) {
            val n: Varargs = chunk.next(k)
            val key = n.arg1()
            if (key.isnil()) break
            k = key
            out.rawset(key, n.arg(2))
        }
        return out
    }

    internal fun lookupInstanceTable(node: Node): LuaTable? = instanceByNode[node]

    /**
     * Wraps any Kotlin value into a `LuaValue`. Nodes go through the bare
     * wrapper so that scripts get the metatable-driven `__index/__newindex`
     * behavior uniformly (LuaJ's default `JavaInstance` cannot set Kotlin
     * properties — `self._status.text = "..."` would fail without this).
     * Non-Node values fall through to the standalone [kotlinToLua].
     */
    // Cache per-object so repeated `host.toLua(sameObject)` returns the same
    // Lua handle (matters for value identity in user code and for memoizing
    // bean wrapper construction).
    private val beanWrapperCache = java.util.IdentityHashMap<Any, LuaTable>()

    internal fun toLua(value: Any?): LuaValue {
        if (value == null) return LuaValue.NIL
        if (value is LuaValue) return value
        if (value is Node) {
            instanceByNode[value]?.let { return it }
            return bareWrapperFor(value)
        }
        // Primitives and strings go straight to LuaValue.
        when (value) {
            is Boolean -> return LuaValue.valueOf(value)
            is Int -> return LuaValue.valueOf(value)
            is Long -> return LuaValue.valueOf(value.toDouble())
            is Float -> return LuaValue.valueOf(value.toDouble())
            is Double -> return LuaValue.valueOf(value)
            is String -> return LuaValue.valueOf(value)
        }
        // Enums: pass-through as JavaInstance — comparing `key == Key.W` and
        // forwarding into Kotlin APIs both rely on the plain JavaInstance.
        if (value is Enum<*>) return CoerceJavaToLua.coerce(value)
        // Engine and gameplay objects (Vec2, Color, SceneTree, Renderer, ...)
        // go through the bean wrapper so scripts can do `vec.x` and
        // `label.text = "..."` uniformly. Cached by identity.
        val pkg = value::class.java.`package`?.name ?: ""
        if (pkg.startsWith("com.neoutils.engine")) {
            beanWrapperCache[value]?.let { return it }
            val wrapper = LuaBeanWrapper.create(this, value)
            beanWrapperCache[value] = wrapper
            return wrapper
        }
        return CoerceJavaToLua.coerce(value)
    }

    internal fun bareWrapperFor(node: Node): LuaTable {
        return bareWrapperByNode.getOrPut(node) {
            val table = LuaTable()
            // Marker key consulted by `unwrapNode` to extract the host Node
            // from a bare-wrapper LuaTable without recursing into __index.
            table.rawset(NODE_MARKER_KEY, CoerceJavaToLua.coerce(node))
            val metatable = LuaTable()
            metatable.set("__index", InstanceIndex(this, node))
            metatable.set("__newindex", InstanceNewIndex(this, node))
            table.setmetatable(metatable)
            table
        }
    }

    /**
     * Recovers the host [Node] from a Lua value that may be:
     *  - a `LuaTable` produced by `bareWrapperFor` / `attach` (carries
     *    [NODE_MARKER_KEY] storing the underlying Node as userdata);
     *  - a `JavaInstance` userdata wrapping a Node directly;
     *  - `nil` (returns null).
     */
    internal fun unwrapNode(value: LuaValue): Node? {
        if (value.isnil()) return null
        if (value.istable()) {
            val marker = (value as LuaTable).rawget(NODE_MARKER_KEY)
            if (!marker.isnil() && marker.isuserdata()) {
                return marker.touserdata() as? Node
            }
            // Instance tables don't carry the marker (they live in
            // instanceByNode); reverse-lookup.
            for ((node, table) in instanceByNode) {
                if (table === value) return node
            }
        }
        if (value.isuserdata()) {
            return value.touserdata() as? Node
        }
        return null
    }


    internal fun signalProxyFor(node: Node, name: String, signal: Signal<Any?>, isUnit: Boolean): LuaTable {
        return signalProxyCache.getOrPut(node to name) {
            buildSignalProxy(signal, isUnit)
        }
    }

    private fun buildSignalProxy(signal: Signal<Any?>, isUnit: Boolean): LuaTable {
        val proxy = LuaTable()
        val handlerMap = mutableMapOf<LuaValue, (Any?) -> Unit>()
        proxy.set("connect", object : TwoArgFunction() {
            override fun call(self: LuaValue, fn: LuaValue): LuaValue {
                if (!fn.isfunction()) throw LuaError("Signal connect: expected function, got ${fn.typename()}")
                val handler: (Any?) -> Unit = { value ->
                    if (isUnit) (fn as LuaFunction).call()
                    else (fn as LuaFunction).call(kotlinToLua(value))
                }
                handlerMap[fn] = handler
                signal.connect(handler)
                return LuaValue.NIL
            }
        })
        proxy.set("disconnect", object : TwoArgFunction() {
            override fun call(self: LuaValue, fn: LuaValue): LuaValue {
                val handler = handlerMap.remove(fn) ?: return LuaValue.NIL
                signal.disconnect(handler)
                return LuaValue.NIL
            }
        })
        proxy.set("emit", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val payload: Any? = if (isUnit) Unit else luaValueToAny(args.arg(2))
                signal.emit(payload)
                return LuaValue.NIL
            }
        })
        return proxy
    }

    // ---------------------------------------------------------------- nengine table

    private fun installNengine() {
        val nengine = LuaTable()

        val host = this
        nengine.set("Vec2", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val x = args.checkdouble(1).toFloat()
                val y = args.checkdouble(2).toFloat()
                return host.toLua(Vec2(x, y))
            }
        })
        nengine.set("Color", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val r = args.checkdouble(1).toFloat()
                val g = args.checkdouble(2).toFloat()
                val b = args.checkdouble(3).toFloat()
                val a = if (args.narg() >= 4) args.checkdouble(4).toFloat() else 1f
                return host.toLua(Color(r, g, b, a))
            }
        })
        nengine.set("Rect", object : TwoArgFunction() {
            override fun call(origin: LuaValue, size: LuaValue): LuaValue =
                host.toLua(Rect(luaToVec2(origin), luaToVec2(size)))
        })
        nengine.set("Transform", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val position = if (args.narg() >= 1 && !args.arg(1).isnil()) luaToVec2(args.arg(1)) else Vec2.ZERO
                val scale = if (args.narg() >= 2 && !args.arg(2).isnil()) luaToVec2(args.arg(2)) else Vec2.ONE
                val rotation = if (args.narg() >= 3) args.checkdouble(3).toFloat() else 0f
                return host.toLua(Transform(position, scale, rotation))
            }
        })
        nengine.set("NodeRef", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val path = if (arg.isnil()) "" else arg.tojstring()
                val ref = NodeRef<Node>(path)
                val luaRef = LuaTable()
                luaRef.set("path", LuaValue.valueOf(path))
                luaRef.set("resolve", object : TwoArgFunction() {
                    override fun call(self: LuaValue, from: LuaValue): LuaValue {
                        val fromNode = unwrapNode(from)
                            ?: throw LuaError("NodeRef:resolve expects a node-like argument")
                        val resolved = ref.resolve(fromNode) ?: return LuaValue.NIL
                        return toLua(resolved)
                    }
                })
                return luaRef
            }
        })
        nengine.set("signal", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs = CoerceJavaToLua.coerce(Signal<Any?>())
        })
        nengine.set("script_of", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                val node = unwrapNode(arg)
                    ?: throw LuaError("nengine.script_of expects a Node argument")
                return toLua(node)
            }
        })

        // Enums
        val keyTable = LuaTable()
        for (k in Key.entries) keyTable.set(k.name, CoerceJavaToLua.coerce(k))
        nengine.set("Key", keyTable)
        val mouseTable = LuaTable()
        for (m in MouseButton.entries) mouseTable.set(m.name, CoerceJavaToLua.coerce(m))
        nengine.set("MouseButton", mouseTable)
        val mouseFilterTable = LuaTable()
        for (f in MouseFilter.entries) mouseFilterTable.set(f.name, CoerceJavaToLua.coerce(f))
        nengine.set("MouseFilter", mouseFilterTable)
        val presetTable = LuaTable()
        for (p in LayoutPreset.entries) presetTable.set(p.name, CoerceJavaToLua.coerce(p))
        nengine.set("LayoutPreset", presetTable)

        // Node types (zero-arg constructors via CoerceJavaToLua of the Class object).
        // Scripts mostly reference these as the `extends` string, but the
        // bindings are exposed so future scripts can do `nengine.Vec2`
        // alongside `nengine.Node2D` etc., and so the LuaCATS stubs map.
        fun put(name: String, klass: Class<*>) {
            nengine.set(name, CoerceJavaToLua.coerce(klass))
        }
        put("Node", Node::class.java)
        put("Node2D", Node2D::class.java)
        put("Camera2D", Camera2D::class.java)
        put("Label", Label::class.java)
        put("CanvasLayer", CanvasLayer::class.java)
        put("Control", Control::class.java)
        put("Panel", Panel::class.java)
        put("Button", Button::class.java)
        put("ColorRect", ColorRect::class.java)
        put("Circle2D", Circle2D::class.java)
        put("Line2D", Line2D::class.java)
        put("Polygon2D", Polygon2D::class.java)
        put("Area2D", Area2D::class.java)
        put("StaticBody2D", StaticBody2D::class.java)
        put("CharacterBody2D", CharacterBody2D::class.java)
        put("RigidBody2D", RigidBody2D::class.java)
        put("CollisionShape2D", CollisionShape2D::class.java)
        put("CollisionObject2D", CollisionObject2D::class.java)
        put("PhysicsBody2D", PhysicsBody2D::class.java)
        put("Shape2D", Shape2D::class.java)
        put("RectangleShape2D", RectangleShape2D::class.java)
        put("CircleShape2D", CircleShape2D::class.java)
        put("Timer", Timer::class.java)

        globals.set("nengine", nengine)

        // Sandbox: drop default `_G` symbols that would let scripts reach
        // outside the bundle. We keep stdlibs (string, math, table, os,
        // package, etc.) but remove dofile/loadfile/load (raw bytecode
        // ingestion) and require's bypass paths.
        for (name in arrayOf("dofile", "loadfile")) {
            globals.set(name, LuaValue.NIL)
        }
    }

    // ---------------------------------------------------------------- require / package.searchers

    private fun installBundleSearchers() {
        val pkg = globals.get("package")
        if (pkg.isnil() || !pkg.istable()) return
        val pkgTable = pkg.checktable()
        val searchersField = if (!pkgTable.get("searchers").isnil()) "searchers" else "loaders"
        val original = pkgTable.get(searchersField)
        val preload = if (original.istable()) original.checktable().get(1) else LuaValue.NIL
        val replacement = LuaTable()
        if (!preload.isnil()) replacement.set(1, preload)
        replacement.set(2, BundleSearcher(this))
        pkgTable.set(searchersField, replacement)
        // Wipe path/cpath so the C-loader and file-loader (if still present
        // anywhere) cannot match.
        pkgTable.set("path", LuaValue.valueOf(""))
        pkgTable.set("cpath", LuaValue.valueOf(""))
    }

    private class BundleSearcher(private val host: LuaScriptHost) : OneArgFunction() {
        override fun call(modname: LuaValue): LuaValue {
            val bundle = host.activeBundle
                ?: return LuaValue.valueOf("\n\tno active bundle for require '${modname.tojstring()}'")
            val resourcePath = modname.tojstring().replace('.', '/') + ".lua"
            if (!bundle.exists(resourcePath)) {
                return LuaValue.valueOf("\n\tno module '${modname.tojstring()}' in bundle ($resourcePath)")
            }
            val source = bundle.read(resourcePath)
            return try {
                host.globals.load(source, resourcePath, host.globals)
            } catch (err: LuaError) {
                LuaValue.valueOf("\n\tcompile error in '$resourcePath': ${err.message}")
            }
        }
    }

    companion object {
        fun create(): LuaScriptHost {
            val globals = JsePlatform.standardGlobals()
            return LuaScriptHost(globals)
        }

        // Non-string-content marker key consulted by `unwrapNode`. Stored
        // via rawset on every wrapper LuaTable so the host Node can be
        // recovered without going through __index recursion.
        internal val NODE_MARKER_KEY: LuaValue = LuaValue.valueOf("__nengine_node__")
    }
}

// ----------------------------------------------------------------------------
// Instance metatable handlers
// ----------------------------------------------------------------------------

internal class InstanceIndex(
    private val host: LuaScriptHost,
    private val node: Node,
) : TwoArgFunction() {
    override fun call(table: LuaValue, key: LuaValue): LuaValue {
        val name = key.tojstring()
        // Convenience: expose the underlying Node (wrapped) so scripts can
        // call `NodeRef:resolve(self.node)` and reach Kotlin APIs uniformly.
        // We return `table` itself when accessing from an instance table so
        // `self.node` is identity for already-wrapped values; otherwise
        // surface the bare wrapper.
        if (name == "node" || name == "_node") return host.toLua(node)
        // Reflective Signal<*> field discovery — done first so that
        // `Timer.timeout`, `Area2D.area_entered`, etc., resolve before
        // generic property reflection.
        LuaReflect.signalProperty(node, name)?.let { (signal, isUnit) ->
            return host.signalProxyFor(node, name, signal, isUnit)
        }
        // Node2D ergonomic properties.
        if (node is Node2D) {
            when (name) {
                "position" -> return CoerceJavaToLua.coerce(node.position)
                "rotation" -> return LuaValue.valueOf(node.rotation.toDouble())
                "scale" -> return CoerceJavaToLua.coerce(node.scale)
                "transform" -> return CoerceJavaToLua.coerce(node.transform)
            }
        }
        // Generic property lookup via reflection.
        LuaReflect.readProperty(node, name)?.let { return host.toLua(it) }
        // Method lookup — return a Lua-callable that forwards to the Kotlin
        // method, dropping the receiver `self` Lua passes for the colon-call
        // syntax `self:world()`.
        LuaReflect.method(node, name)?.let { method ->
            return object : VarArgFunction() {
                override fun invoke(args: Varargs): Varargs {
                    // args.arg1() is `self` (the Lua instance table); skip it.
                    val javaArgs = Array(args.narg() - 1) { i -> luaValueToAny(args.arg(i + 2)) }
                    val result = try {
                        method.invoke(node, *javaArgs)
                    } catch (err: java.lang.reflect.InvocationTargetException) {
                        val cause = err.targetException
                        if (cause is RuntimeException) throw cause else throw err
                    }
                    return host.toLua(result)
                }
            }
        }
        return LuaValue.NIL
    }
}

internal class InstanceNewIndex(
    @Suppress("unused") private val host: LuaScriptHost,
    private val node: Node,
) : ThreeArgFunction() {
    override fun call(table: LuaValue, key: LuaValue, value: LuaValue): LuaValue {
        val name = key.tojstring()
        if (node is Node2D) {
            when (name) {
                "position" -> { node.position = luaToVec2(value); return LuaValue.NIL }
                "rotation" -> { node.rotation = value.checkdouble().toFloat(); return LuaValue.NIL }
                "scale" -> { node.scale = luaToVec2(value); return LuaValue.NIL }
                "transform" -> {
                    val any = luaValueToAny(value) as? Transform
                        ?: throw LuaError("transform expected Transform, got ${value.typename()}")
                    node.transform = any
                    return LuaValue.NIL
                }
            }
        }
        // Write-through to a Kotlin property if one exists with a setter;
        // otherwise rawset to the instance table (private script state).
        if (LuaReflect.writeProperty(node, name, luaValueToAny(value))) {
            return LuaValue.NIL
        }
        (table as LuaTable).rawset(key, value)
        return LuaValue.NIL
    }
}

// ----------------------------------------------------------------------------
// Exceptions
// ----------------------------------------------------------------------------

class LuaScriptContractException(message: String) : RuntimeException(message)
class LuaScriptCompileException(path: String, cause: LuaError) :
    RuntimeException("Failed to compile Lua script '$path': ${cause.message}", cause)

// ----------------------------------------------------------------------------
// Data
// ----------------------------------------------------------------------------

internal data class LuaScriptData(
    override val path: String,
    override val extendsType: KClass<out Node>,
    override val exports: List<ExportedProperty>,
    override val signals: Map<String, SignalDeclaration>,
) : Script

// ----------------------------------------------------------------------------
// Type coercion helpers
// ----------------------------------------------------------------------------

internal fun kotlinTypeFor(name: String, scriptPath: String, exportName: String): KClass<*> = when (name) {
    "float" -> Float::class
    "int" -> Int::class
    "bool" -> Boolean::class
    "string" -> String::class
    "Vec2" -> Vec2::class
    "Color" -> Color::class
    "Rect" -> Rect::class
    "NodeRef" -> NodeRef::class
    "Key" -> Key::class
    else -> throw LuaScriptContractException(
        "Script '$scriptPath': export '$exportName' has unsupported type '$name'"
    )
}

internal fun luaValueToKotlin(value: LuaValue, type: KClass<*>): Any? {
    if (value.isnil()) return null
    return when (type) {
        Float::class -> value.todouble().toFloat()
        Int::class -> value.toint()
        Boolean::class -> value.toboolean()
        String::class -> value.tojstring()
        else -> luaValueToAny(value)
    }
}

internal fun luaValueToAny(value: LuaValue): Any? {
    if (value.isnil()) return null
    if (value.isboolean()) return value.toboolean()
    if (value.isint()) return value.toint()
    if (value.isnumber()) return value.todouble()
    if (value.isstring() && !value.isnumber()) return value.tojstring()
    if (value.isuserdata()) return value.touserdata()
    if (value.istable()) {
        // Bean wrappers, bare-node wrappers and script-instance tables all
        // store the original host object under NODE_MARKER_KEY (rawset, so we
        // can recover it here without re-entering __index).
        val marker = (value as LuaTable).rawget(LuaScriptHost.NODE_MARKER_KEY)
        if (!marker.isnil() && marker.isuserdata()) return marker.touserdata()
    }
    return value
}

internal fun luaToVec2(value: LuaValue): Vec2 {
    val any = luaValueToAny(value)
    if (any is Vec2) return any
    throw LuaError("expected Vec2, got ${value.typename()}")
}

internal fun kotlinToLua(value: Any?): LuaValue = when (value) {
    null -> LuaValue.NIL
    is LuaValue -> value
    is Boolean -> LuaValue.valueOf(value)
    is Int -> LuaValue.valueOf(value)
    is Long -> LuaValue.valueOf(value.toDouble())
    is Float -> LuaValue.valueOf(value.toDouble())
    is Double -> LuaValue.valueOf(value)
    is String -> LuaValue.valueOf(value)
    else -> CoerceJavaToLua.coerce(value)
}
