package app.openbubbles.core.contacts

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ContactMergePolicyTest {

    private fun icloud(
        id: String = "icloud:/card/a.vcf",
        name: String? = "Jane Doe",
        addresses: List<String> = listOf("+15551234567"),
        first: String? = null,
        last: String? = null,
    ) = RawContact(id, name, first, last, null, addresses)

    private fun device(
        id: String = "7",
        name: String? = "Jane Doe",
        addresses: List<String> = listOf("+15551234567"),
    ) = RawContact(id, name, null, null, null, addresses)

    private fun plan(
        icloud: List<RawContact>,
        device: List<RawContact> = emptyList(),
        ours: Set<String> = emptySet(),
        decisions: Map<String, ConflictDecision> = emptyMap(),
    ) = ContactMergePolicy.plan(icloud, device, ours, decisions)

    @Test
    fun `no device match inserts under our account`() {
        val result = plan(listOf(icloud()))

        val action = result.actions.single()
        assertIs<MergeAction.Insert>(action)
        assertEquals("icloud:/card/a.vcf", action.contact.id)
        assertTrue(result.deletions.isEmpty())
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun `existing raw contact under our account updates in place`() {
        val result = plan(
            icloud = listOf(icloud()),
            // A disagreeing other-account contact must not demote an
            // already-owned card back into a conflict.
            device = listOf(device(name = "Janet D", addresses = listOf("+15551234567"))),
            ours = setOf("icloud:/card/a.vcf"),
        )

        assertIs<MergeAction.Update>(result.actions.single())
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun `other account with a different name is a conflict`() {
        val result = plan(
            icloud = listOf(icloud(name = "Jane Doe")),
            device = listOf(device(id = "9", name = "Janey", addresses = listOf("555-123-4567"))),
        )

        val action = result.actions.single()
        assertIs<MergeAction.AwaitDecision>(action)
        assertEquals("Jane Doe", action.conflict.icloudName)
        assertEquals("Janey", action.conflict.deviceName)
        assertEquals(listOf("+15551234567"), action.conflict.icloudNumbers)
        assertEquals(listOf("555-123-4567"), action.conflict.deviceNumbers)
        assertEquals("icloud:/card/a.vcf", action.conflict.icloudId)
    }

    @Test
    fun `other account with equivalent data inserts silently`() {
        val result = plan(
            icloud = listOf(icloud(name = "Jane Doe", addresses = listOf("+1 (555) 123-4567"))),
            device = listOf(device(name = "jane doe", addresses = listOf("5551234567"))),
        )

        assertIs<MergeAction.Insert>(result.actions.single())
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun `diverging number sets are a conflict`() {
        val result = plan(
            icloud = listOf(icloud(addresses = listOf("+15551234567"))),
            device = listOf(
                device(name = "Jane Doe", addresses = listOf("+15551234567", "+15559990000")),
            ),
        )

        assertIs<MergeAction.AwaitDecision>(result.actions.single())
    }

    @Test
    fun `an email-only card never raises a number conflict`() {
        val result = plan(
            icloud = listOf(icloud(addresses = listOf("jane@icloud.com"))),
            device = listOf(device(name = "Jane Doe", addresses = listOf("jane@icloud.com", "+15550001111"))),
        )

        assertIs<MergeAction.Insert>(result.actions.single())
    }

    @Test
    fun `a nameless device contact never raises a name conflict`() {
        val result = plan(
            icloud = listOf(icloud(name = "Jane Doe")),
            device = listOf(device(name = null)),
        )

        assertIs<MergeAction.Insert>(result.actions.single())
    }

    @Test
    fun `structured name stands in when the card has no display name`() {
        val result = plan(
            icloud = listOf(icloud(name = null, first = "Jane", last = "Doe")),
            device = listOf(device(name = "Janey")),
        )

        val action = result.actions.single()
        assertIs<MergeAction.AwaitDecision>(action)
        assertEquals("Jane Doe", action.conflict.icloudName)
    }

    @Test
    fun `remembered keep-phone decision suppresses the conflict`() {
        val result = plan(
            icloud = listOf(icloud(name = "Jane Doe")),
            device = listOf(device(name = "Janey")),
            decisions = mapOf("icloud:/card/a.vcf" to ConflictDecision.KEEP_PHONE),
        )

        assertIs<MergeAction.Skip>(result.actions.single())
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun `remembered use-icloud decision inserts despite the disagreement`() {
        val result = plan(
            icloud = listOf(icloud(name = "Jane Doe")),
            device = listOf(device(name = "Janey")),
            decisions = mapOf("icloud:/card/a.vcf" to ConflictDecision.USE_ICLOUD),
        )

        assertIs<MergeAction.Insert>(result.actions.single())
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun `vanished cards propagate as deletions of our raw contacts`() {
        val result = plan(
            icloud = listOf(icloud(id = "icloud:/card/kept.vcf")),
            ours = setOf("icloud:/card/kept.vcf", "icloud:/card/gone.vcf", "icloud:/card/gone2.vcf"),
        )

        assertEquals(listOf("icloud:/card/gone.vcf", "icloud:/card/gone2.vcf"), result.deletions)
        assertIs<MergeAction.Update>(result.actions.single())
    }
}
