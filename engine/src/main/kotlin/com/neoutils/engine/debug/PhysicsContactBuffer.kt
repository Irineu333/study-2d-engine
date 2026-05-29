package com.neoutils.engine.debug

import com.neoutils.engine.math.Vec2

/** A single resolved contact captured during `PhysicsSystem.step`. */
data class ContactRecord(val point: Vec2, val normal: Vec2)

/**
 * Per-`SceneTree` buffer of the contacts the impulse solver resolved during
 * the most recent physics step. Runtime-only (never `@Serializable`, never
 * shared across trees), reached by `PhysicsSystem.step` via `tree.debug` and
 * read by `ContactGizmoWidget.drawDebug` in the same frame.
 *
 * [recording] gates the capture: it mirrors `ContactGizmoWidget.enabled`.
 * When `false`, the step records nothing and pays no per-contact cost; when
 * `true`, the step clears the buffer at the start and appends one
 * [ContactRecord] per resolved contact.
 */
class PhysicsContactBuffer {

    /** Driven by `ContactGizmoWidget.enabled`; consulted by `PhysicsSystem.step`. */
    var recording: Boolean = false

    private val _records: MutableList<ContactRecord> = mutableListOf()

    val records: List<ContactRecord> get() = _records

    fun clear() {
        _records.clear()
    }

    fun append(point: Vec2, normal: Vec2) {
        _records += ContactRecord(point, normal)
    }
}
