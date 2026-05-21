package com.neoutils.engine.scripting

import com.neoutils.engine.scene.Node
import kotlin.reflect.KClass

interface ScriptHost {
    fun compile(path: String): KClass<out Node>
    fun factoryFor(path: String): () -> Node
    fun pathFor(klass: KClass<out Node>): String?
}

object ScriptHosts {
    private var currentHost: ScriptHost? = null

    fun register(host: ScriptHost) {
        currentHost = host
    }

    fun current(): ScriptHost? = currentHost

    internal fun clear() {
        currentHost = null
    }
}

