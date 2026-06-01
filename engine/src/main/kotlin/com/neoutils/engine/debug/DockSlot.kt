package com.neoutils.engine.debug

/**
 * Anchor a [ScreenDebugWidget] declares so the [DebugDock] can place it. The
 * dock stacks every enabled widget sharing a slot from that corner/center
 * inward, so a slot can hold any number of widgets without collision.
 *
 * A widget's slot is no longer fixed: its `defaultSlot` is the value declared
 * here, but the user can drag it into any other slot ([ScreenDebugWidget.currentSlot]).
 */
enum class DockSlot {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP_CENTER,
    BOTTOM_CENTER;

    /** Top-edge slots stack downward from the top margin. */
    val isTop: Boolean
        get() = this == TOP_LEFT || this == TOP_RIGHT || this == TOP_CENTER

    /** Bottom-edge slots stack upward from the bottom margin. */
    val isBottom: Boolean get() = !isTop
}
