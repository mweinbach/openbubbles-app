package app.openbubbles.nativeapp.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Order model: sanitizing, stepping, and the persisted form. */
class TopLevelSurfaceOrderTest {
    @Test
    fun `default order offers all four surfaces starting at messages`() {
        assertEquals(
            listOf(
                TopLevelSurface.MESSAGES,
                TopLevelSurface.PHOTOS,
                TopLevelSurface.PASSWORDS,
                TopLevelSurface.FIND_MY,
            ),
            TopLevelSurfaceOrder.Default.surfaces,
        )
        assertEquals(TopLevelSurface.MESSAGES, TopLevelSurfaceOrder.Default.defaultSurface)
    }

    @Test
    fun `duplicates collapse to their first position`() {
        val order = TopLevelSurfaceOrder.of(
            listOf(
                TopLevelSurface.PHOTOS,
                TopLevelSurface.MESSAGES,
                TopLevelSurface.PHOTOS,
            ),
        )
        assertEquals(
            listOf(TopLevelSurface.PHOTOS, TopLevelSurface.MESSAGES),
            order.surfaces,
        )
    }

    @Test
    fun `messages is reinstated when an order tries to drop it`() {
        val order = TopLevelSurfaceOrder.of(
            surfaces = listOf(TopLevelSurface.PHOTOS, TopLevelSurface.FIND_MY),
            defaultSurface = TopLevelSurface.PHOTOS,
        )
        assertEquals(TopLevelSurface.MESSAGES, order.surfaces.first())
        assertEquals(TopLevelSurface.PHOTOS, order.defaultSurface)
    }

    @Test
    fun `an empty order falls back to messages alone`() {
        val order = TopLevelSurfaceOrder.of(emptyList())
        assertEquals(listOf(TopLevelSurface.MESSAGES), order.surfaces)
        assertEquals(TopLevelSurface.MESSAGES, order.defaultSurface)
    }

    @Test
    fun `a default that is not offered falls back to the first surface`() {
        val order = TopLevelSurfaceOrder.of(
            surfaces = listOf(TopLevelSurface.MESSAGES, TopLevelSurface.PHOTOS),
            defaultSurface = TopLevelSurface.PASSWORDS,
        )
        assertEquals(TopLevelSurface.MESSAGES, order.defaultSurface)
    }

    @Test
    fun `stepping walks one position at a time in both directions`() {
        val order = TopLevelSurfaceOrder.Default
        assertEquals(
            TopLevelSurface.PHOTOS,
            order.step(TopLevelSurface.MESSAGES, SurfaceStep.Forward),
        )
        assertEquals(
            TopLevelSurface.PASSWORDS,
            order.step(TopLevelSurface.PHOTOS, SurfaceStep.Forward),
        )
        assertEquals(
            TopLevelSurface.PHOTOS,
            order.step(TopLevelSurface.PASSWORDS, SurfaceStep.Backward),
        )
    }

    @Test
    fun `the ends of the strip clamp instead of wrapping`() {
        val order = TopLevelSurfaceOrder.Default
        assertNull(order.step(TopLevelSurface.MESSAGES, SurfaceStep.Backward))
        assertNull(order.step(TopLevelSurface.FIND_MY, SurfaceStep.Forward))
    }

    @Test
    fun `stepping from no surface counts from the default`() {
        val order = TopLevelSurfaceOrder.of(
            surfaces = TopLevelSurface.entries,
            defaultSurface = TopLevelSurface.PHOTOS,
        )
        assertEquals(TopLevelSurface.PASSWORDS, order.step(null, SurfaceStep.Forward))
        assertEquals(TopLevelSurface.MESSAGES, order.step(null, SurfaceStep.Backward))
    }

    @Test
    fun `stepping from a surface the order no longer offers counts from the default`() {
        val order = TopLevelSurfaceOrder.of(
            surfaces = listOf(TopLevelSurface.MESSAGES, TopLevelSurface.FIND_MY),
            defaultSurface = TopLevelSurface.MESSAGES,
        )
        assertEquals(
            TopLevelSurface.FIND_MY,
            order.step(TopLevelSurface.PHOTOS, SurfaceStep.Forward),
        )
    }

    @Test
    fun `every custom order and default round-trips through the codec`() {
        val orders = TopLevelSurface.entries.flatMap { default ->
            listOf(
                TopLevelSurfaceOrder.of(TopLevelSurface.entries.reversed(), default),
                TopLevelSurfaceOrder.of(
                    listOf(TopLevelSurface.MESSAGES, TopLevelSurface.PASSWORDS),
                    default,
                ),
            )
        }
        orders.forEach { order ->
            assertEquals(order, TopLevelSurfaceOrderCodec.decode(TopLevelSurfaceOrderCodec.encode(order)))
        }
    }

    @Test
    fun `unreadable preferences decode to the default order`() {
        val corrupt = listOf(
            null,
            "",
            "messages,photos",
            "1|messages",
            "9|messages,photos|messages",
            "one|messages,photos|messages",
            "1|nothing,here|nothing",
        )
        corrupt.forEach { raw ->
            assertSame(
                TopLevelSurfaceOrder.Default,
                TopLevelSurfaceOrderCodec.decode(raw),
                "decoded $raw",
            )
        }
    }

    @Test
    fun `an unknown surface id falls back to the canonical order`() {
        val order = TopLevelSurfaceOrderCodec.decode("1|messages,shortcuts,findmy|findmy")
        assertSame(TopLevelSurfaceOrder.Default, order)
    }

    @Test
    fun `persisted ids stay stable`() {
        assertEquals(
            listOf("messages", "photos", "passwords", "findmy"),
            TopLevelSurface.entries.map { it.id },
        )
        assertEquals(
            "1|messages,photos,passwords,findmy|messages",
            TopLevelSurfaceOrderCodec.encode(TopLevelSurfaceOrder.Default),
        )
    }
}

/** The stack edit a switch performs. */
class TopLevelSurfaceSwitchTest {
    private val messagesRoot = SurfaceStackEntry.Root(TopLevelSurface.MESSAGES)
    private val openChat = SurfaceStackEntry.Child(TopLevelSurface.MESSAGES)
    private val photosRoot = SurfaceStackEntry.Root(TopLevelSurface.PHOTOS)
    private val passwordsRoot = SurfaceStackEntry.Root(TopLevelSurface.PASSWORDS)
    private val vaultItem = SurfaceStackEntry.Child(TopLevelSurface.PASSWORDS)

    @Test
    fun `the current surface follows the top entry`() {
        assertEquals(
            TopLevelSurface.MESSAGES,
            TopLevelSurfaceSwitch.currentSurface(listOf(messagesRoot, openChat)),
        )
        assertEquals(
            TopLevelSurface.PASSWORDS,
            TopLevelSurfaceSwitch.currentSurface(listOf(messagesRoot, passwordsRoot, vaultItem)),
        )
    }

    @Test
    fun `a destination no surface owns reports no current surface`() {
        assertNull(
            TopLevelSurfaceSwitch.currentSurface(
                listOf(messagesRoot, SurfaceStackEntry.Unowned),
            ),
        )
    }

    @Test
    fun `switching from the root surface pushes exactly one entry`() {
        assertEquals(
            SurfaceSwitchPlan.Replace(keepCount = 1, push = TopLevelSurface.PHOTOS),
            TopLevelSurfaceSwitch.plan(listOf(messagesRoot), TopLevelSurface.PHOTOS),
        )
    }

    @Test
    fun `switching between two non-root surfaces does not stack them`() {
        val plan = TopLevelSurfaceSwitch.plan(
            listOf(messagesRoot, photosRoot),
            TopLevelSurface.PASSWORDS,
        )
        assertEquals(SurfaceSwitchPlan.Replace(keepCount = 1, push = TopLevelSurface.PASSWORDS), plan)
    }

    @Test
    fun `selecting the surface already showing changes nothing`() {
        assertEquals(
            SurfaceSwitchPlan.None,
            TopLevelSurfaceSwitch.plan(listOf(messagesRoot), TopLevelSurface.MESSAGES),
        )
        assertEquals(
            SurfaceSwitchPlan.None,
            TopLevelSurfaceSwitch.plan(listOf(messagesRoot, photosRoot), TopLevelSurface.PHOTOS),
        )
        assertEquals(
            SurfaceSwitchPlan.None,
            TopLevelSurfaceSwitch.plan(
                listOf(messagesRoot, SurfaceStackEntry.Unowned, photosRoot),
                TopLevelSurface.PHOTOS,
            ),
        )
    }

    @Test
    fun `returning to messages from a surface pops back to the list`() {
        assertEquals(
            SurfaceSwitchPlan.Replace(keepCount = 1, push = null),
            TopLevelSurfaceSwitch.plan(listOf(messagesRoot, photosRoot), TopLevelSurface.MESSAGES),
        )
    }

    @Test
    fun `an open conversation is replaced instead of left beside the new surface`() {
        assertEquals(
            SurfaceSwitchPlan.Replace(keepCount = 1, push = TopLevelSurface.FIND_MY),
            TopLevelSurfaceSwitch.plan(listOf(messagesRoot, openChat), TopLevelSurface.FIND_MY),
        )
        assertEquals(
            SurfaceSwitchPlan.Replace(keepCount = 1, push = null),
            TopLevelSurfaceSwitch.plan(listOf(messagesRoot, openChat), TopLevelSurface.MESSAGES),
        )
    }

    @Test
    fun `a nested surface page is dropped when its surface is left`() {
        assertEquals(
            SurfaceSwitchPlan.Replace(keepCount = 1, push = TopLevelSurface.PHOTOS),
            TopLevelSurfaceSwitch.plan(
                listOf(messagesRoot, passwordsRoot, vaultItem),
                TopLevelSurface.PHOTOS,
            ),
        )
    }

    @Test
    fun `re-selecting the current surface from one of its pages returns to its root`() {
        assertEquals(
            SurfaceSwitchPlan.Replace(keepCount = 1, push = TopLevelSurface.PASSWORDS),
            TopLevelSurfaceSwitch.plan(
                listOf(messagesRoot, passwordsRoot, vaultItem),
                TopLevelSurface.PASSWORDS,
            ),
        )
    }

    @Test
    fun `switching away from settings drops it`() {
        assertEquals(
            SurfaceSwitchPlan.Replace(keepCount = 1, push = TopLevelSurface.PHOTOS),
            TopLevelSurfaceSwitch.plan(
                listOf(messagesRoot, SurfaceStackEntry.Unowned),
                TopLevelSurface.PHOTOS,
            ),
        )
    }

    @Test
    fun `a standalone passwords task keeps its own root underneath`() {
        assertEquals(
            SurfaceSwitchPlan.Replace(keepCount = 1, push = TopLevelSurface.MESSAGES),
            TopLevelSurfaceSwitch.plan(listOf(passwordsRoot), TopLevelSurface.MESSAGES),
        )
        assertEquals(
            SurfaceSwitchPlan.None,
            TopLevelSurfaceSwitch.plan(listOf(passwordsRoot), TopLevelSurface.PASSWORDS),
        )
        assertEquals(
            SurfaceSwitchPlan.Replace(keepCount = 1, push = null),
            TopLevelSurfaceSwitch.plan(listOf(passwordsRoot, vaultItem), TopLevelSurface.PASSWORDS),
        )
    }

    @Test
    fun `every switch adds at most one entry from every reachable stack`() {
        val stacks = listOf(
            listOf(messagesRoot),
            listOf(messagesRoot, openChat),
            listOf(messagesRoot, openChat, SurfaceStackEntry.Child(TopLevelSurface.MESSAGES)),
            listOf(messagesRoot, photosRoot),
            listOf(messagesRoot, passwordsRoot, vaultItem),
            listOf(messagesRoot, SurfaceStackEntry.Unowned),
            listOf(passwordsRoot),
        )
        stacks.forEach { stack ->
            TopLevelSurface.entries.forEach { target ->
                val plan = TopLevelSurfaceSwitch.plan(stack, target)
                if (plan is SurfaceSwitchPlan.Replace) {
                    val result = stack.take(plan.keepCount) +
                        listOfNotNull(plan.push?.let(SurfaceStackEntry::Root))
                    assertTrue(result.size <= 2, "$stack -> $target produced $result")
                    assertEquals(
                        target,
                        TopLevelSurfaceSwitch.currentSurface(result),
                        "$stack -> $target",
                    )
                    assertFalse(
                        TopLevelSurfaceSwitch.plan(result, target) is SurfaceSwitchPlan.Replace,
                        "$stack -> $target is not settled",
                    )
                }
            }
        }
    }
}
