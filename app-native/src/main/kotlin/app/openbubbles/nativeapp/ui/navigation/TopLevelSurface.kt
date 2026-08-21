package app.openbubbles.nativeapp.ui.navigation

/**
 * The four peer product surfaces reachable from the header switcher.
 *
 * These are not new destinations: each maps onto a typed Navigation3 key that
 * already exists in the root back stack. [id] is the persistence form, so it
 * must stay stable even if the enum is reordered or a label changes.
 */
enum class TopLevelSurface(val id: String, val label: String) {
    MESSAGES("messages", "Messages"),
    PHOTOS("photos", "Photos"),
    PASSWORDS("passwords", "Passwords"),
    FIND_MY("findmy", "Find My"),
    ;

    companion object {
        /** The surface every fallback lands on; it can never be removed from an order. */
        val Fallback: TopLevelSurface = MESSAGES

        fun fromId(id: String?): TopLevelSurface? = entries.firstOrNull { it.id == id }
    }
}

/** Which neighbour a committed header swipe (or arrow key) moves to. */
enum class SurfaceStep { Forward, Backward }

/**
 * Persisted switcher configuration: which surfaces are offered, in which
 * order, and which one a fresh launch opens.
 *
 * Construct through [of] (or the codec) rather than the constructor so every
 * instance is already sanitized: unique, supported, non-empty, containing
 * [TopLevelSurface.Fallback], and with a [defaultSurface] that is actually in
 * the list.
 */
@ConsistentCopyVisibility
data class TopLevelSurfaceOrder private constructor(
    val surfaces: List<TopLevelSurface>,
    val defaultSurface: TopLevelSurface,
) {
    fun indexOf(surface: TopLevelSurface): Int = surfaces.indexOf(surface)

    /**
     * The neighbour of [current] in [step]'s direction, or null at the end of
     * the strip. Ends clamp instead of wrapping: a switcher is a fixed strip of
     * visible peers, and wrapping from the last surface back to the first reads
     * as a random jump when the indicator is on screen.
     */
    fun step(current: TopLevelSurface?, step: SurfaceStep): TopLevelSurface? {
        val from = surfaces.indexOf(current ?: defaultSurface).takeIf { it >= 0 }
            ?: surfaces.indexOf(defaultSurface)
        val target = when (step) {
            SurfaceStep.Forward -> from + 1
            SurfaceStep.Backward -> from - 1
        }
        return surfaces.getOrNull(target)
    }

    companion object {
        val Default: TopLevelSurfaceOrder = of(TopLevelSurface.entries, TopLevelSurface.Fallback)

        /**
         * Sanitizes any candidate order. Duplicates collapse to their first
         * position, [TopLevelSurface.Fallback] is reinstated when missing so
         * the recovery surface is always reachable, and a default that is not
         * offered falls back to the first surface in the list.
         */
        fun of(
            surfaces: Iterable<TopLevelSurface>,
            defaultSurface: TopLevelSurface = TopLevelSurface.Fallback,
        ): TopLevelSurfaceOrder {
            val unique = surfaces.distinct().ifEmpty { listOf(TopLevelSurface.Fallback) }
            val complete = if (TopLevelSurface.Fallback in unique) {
                unique
            } else {
                listOf(TopLevelSurface.Fallback) + unique
            }
            return TopLevelSurfaceOrder(
                surfaces = complete,
                defaultSurface = defaultSurface.takeIf { it in complete } ?: complete.first(),
            )
        }
    }
}

/**
 * Text form of a [TopLevelSurfaceOrder], versioned so a future shape change can
 * be recognized instead of misread. Unknown versions, unknown surface ids, and
 * malformed text all decode to [TopLevelSurfaceOrder.Default] rather than
 * throwing: a corrupt preference must never keep the app off its surfaces.
 */
object TopLevelSurfaceOrderCodec {
    const val VERSION: Int = 1

    fun encode(order: TopLevelSurfaceOrder): String = listOf(
        VERSION.toString(),
        order.surfaces.joinToString(",") { it.id },
        order.defaultSurface.id,
    ).joinToString("|")

    fun decode(raw: String?): TopLevelSurfaceOrder {
        val parts = raw?.split('|') ?: return TopLevelSurfaceOrder.Default
        if (parts.size != 3) return TopLevelSurfaceOrder.Default
        if (parts[0].toIntOrNull() != VERSION) return TopLevelSurfaceOrder.Default
        val surfaces = parts[1].split(',').mapNotNull(TopLevelSurface::fromId)
        if (surfaces.isEmpty()) return TopLevelSurfaceOrder.Default
        return TopLevelSurfaceOrder.of(
            surfaces = surfaces,
            defaultSurface = TopLevelSurface.fromId(parts[2]) ?: surfaces.first(),
        )
    }
}

/**
 * How one root back-stack entry relates to the switcher.
 *
 * The switcher reasons over this projection instead of over typed navigation
 * keys so the policy stays a pure function of stack shape, testable without a
 * composition.
 */
sealed interface SurfaceStackEntry {
    /** The surface this entry belongs to, or null when no surface owns it. */
    val surface: TopLevelSurface?

    /** The root page of a surface (chat list, Photos, Passwords list, Find My). */
    data class Root(override val surface: TopLevelSurface) : SurfaceStackEntry

    /** A page owned by a surface (a conversation, a vault item, a chat's bookmarks). */
    data class Child(override val surface: TopLevelSurface) : SurfaceStackEntry

    /** A destination no surface owns: Settings, login, the share picker. */
    data object Unowned : SurfaceStackEntry {
        override val surface: TopLevelSurface? = null
    }
}

/**
 * The stack edit a surface switch performs.
 *
 * [None] means the request is already satisfied, so no navigation happens and
 * no duplicate entry is pushed. [Replace] keeps [keepCount] entries from the
 * bottom of the stack and then pushes [push] when it is non-null, which is at
 * most one new entry per switch.
 */
sealed interface SurfaceSwitchPlan {
    data object None : SurfaceSwitchPlan

    data class Replace(val keepCount: Int, val push: TopLevelSurface?) : SurfaceSwitchPlan
}

/**
 * Deterministic peer-surface switching over the single root back stack.
 *
 * The chosen policy is **reset to the target surface's root**, not restore a
 * per-surface stack: switchers with hidden per-tab history make "back" mean
 * something different on every tab, and a surface's nested pages here are
 * either transient (a vault item behind authentication) or belong beside the
 * conversation list. So a switch keeps the stack root, drops everything above
 * it, and pushes the target root when the target is not the stack root itself.
 * Back from a switched-to surface therefore always lands on the root surface,
 * and no detail pane survives beside an unrelated overlay.
 */
object TopLevelSurfaceSwitch {
    /** The surface the user is looking at, or null on a destination no surface owns. */
    fun currentSurface(entries: List<SurfaceStackEntry>): TopLevelSurface? =
        entries.lastOrNull()?.surface

    fun plan(entries: List<SurfaceStackEntry>, target: TopLevelSurface): SurfaceSwitchPlan {
        if (entries.isEmpty()) return SurfaceSwitchPlan.Replace(keepCount = 0, push = target)
        val rootSurface = (entries.first() as? SurfaceStackEntry.Root)?.surface
        if (target == rootSurface) {
            return if (entries.size == 1) {
                SurfaceSwitchPlan.None
            } else {
                SurfaceSwitchPlan.Replace(keepCount = 1, push = null)
            }
        }
        val alreadySettled = entries.size == 2 && entries[1] == SurfaceStackEntry.Root(target)
        return if (alreadySettled) {
            SurfaceSwitchPlan.None
        } else {
            SurfaceSwitchPlan.Replace(keepCount = 1, push = target)
        }
    }
}
