package com.neoutils.engine.debug

import com.neoutils.engine.render.Renderer

/**
 * World-space gizmo that draws every contact the impulse solver resolved last
 * step: a filled marker at the contact `point` plus a short line along the
 * contact `normal`. The records come from `tree.debug.contacts`, populated by
 * `PhysicsSystem.step` while recording is on.
 *
 * [enabled] is the single control: enabling the widget turns contact
 * recording on (by flipping `tree.debug.contacts.recording`), disabling it
 * turns recording off — so the solver pays no recording cost outside debug.
 *
 * Lives under `WorldDebugContainer`, so the world pass of `SceneTree.render`
 * already applies the current `Camera2D` view transform — no manual
 * `pushTransform` here.
 */
class ContactGizmoWidget : WorldDebugWidget() {

    override val title: String = "Contacts"

    override var enabled: Boolean = false
        set(value) {
            field = value
            syncRecording(value)
        }

    init { name = "ContactGizmoWidget" }

    override fun onEnter() {
        super.onEnter()
        // The buffer becomes reachable only once attached; mirror current state.
        syncRecording(enabled)
    }

    private fun syncRecording(value: Boolean) {
        val buffer = tree?.debug?.contacts ?: return
        buffer.recording = value
    }

    override fun drawDebug(renderer: Renderer) {
        val owningTree = tree ?: return
        for (record in owningTree.debug.contacts.records) {
            renderer.drawCircle(record.point, MARKER_RADIUS, DEBUG_CONTACT_COLOR, filled = true)
            val tip = record.point + record.normal * NORMAL_LENGTH
            renderer.drawLine(record.point, tip, 1f, DEBUG_CONTACT_COLOR)
        }
    }

    companion object {
        private const val MARKER_RADIUS: Float = 3f
        private const val NORMAL_LENGTH: Float = 16f
    }
}
