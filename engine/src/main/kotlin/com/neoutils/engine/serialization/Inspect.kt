package com.neoutils.engine.serialization

/**
 * Marks a property as part of the node's serialized contract and visible to
 * the future editor. The companion convention enforced in `CLAUDE.md` is:
 * every `var` on a `@Serializable` node must carry either `@Inspect` (kept
 * in the scene file and shown to the designer) or `kotlinx.serialization`'s
 * `@Transient` (runtime state, never persisted).
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Inspect(
    val displayName: String = "",
)
