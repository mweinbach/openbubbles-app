package app.openbubbles.nativeapp.data

import app.openbubbles.core.contacts.ContactSync
import app.openbubbles.db.Chat
import app.openbubbles.db.Handle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageNotificationIdentityTest {

    @Test
    fun `direct notification prefers saved contact over stored address title`() {
        val chat = Chat().apply {
            displayName = "+15551234567"
            handles.add(
                Handle().apply {
                    address = "+15551234567"
                    formattedAddress = "+1 (555) 123-4567"
                },
            )
        }

        val identity = resolveMessageNotificationIdentity(
            chat = chat,
            senderAddress = "tel:+15551234567",
            contactNameFor = contactNames("+15551234567" to "Alice"),
        )

        assertEquals("Alice", identity.title)
        assertEquals("Alice", identity.senderName)
        assertFalse(identity.isGroup)
    }

    @Test
    fun `group notification preserves title and resolves sender contact`() {
        val chat = Chat().apply {
            style = 43L
            displayName = "Family"
            handles.add(Handle().apply { address = "alice@icloud.com" })
            handles.add(Handle().apply { address = "bob@icloud.com" })
        }

        val identity = resolveMessageNotificationIdentity(
            chat = chat,
            senderAddress = "mailto:alice@icloud.com",
            contactNameFor = contactNames("alice@icloud.com" to "Alice"),
        )

        assertEquals("Family", identity.title)
        assertEquals("Alice", identity.senderName)
        assertTrue(identity.isGroup)
    }

    @Test
    fun `registered self handle is excluded from direct notification identity`() {
        val chat = Chat().apply {
            displayName = "friend@icloud.com"
            handles.add(Handle().apply { address = "me@icloud.com" })
            handles.add(Handle().apply { address = "friend@icloud.com" })
        }

        val identity = resolveMessageNotificationIdentity(
            chat = chat,
            myHandles = setOf("mailto:ME@icloud.com"),
            contactNameFor = contactNames("friend@icloud.com" to "Friend"),
        )

        assertEquals("Friend", identity.title)
        assertFalse(identity.isGroup)
    }

    @Test
    fun `direct history rows reuse the conversation title instead of the raw handle`() {
        val name = resolveNotificationSenderLabel(
            address = "+17033092799",
            formattedAddress = "+1 (703) 309-2799",
            isGroup = false,
            conversationTitle = "Mark Linsangan",
            contactNameFor = { null },
        )

        assertEquals("Mark Linsangan", name)
    }

    @Test
    fun `group history rows prefer the saved contact over the phone number`() {
        val name = resolveNotificationSenderLabel(
            address = "tel:+19199040410",
            formattedAddress = "+19199040410",
            isGroup = true,
            conversationTitle = "Bobby's Capital",
            contactNameFor = contactNames("+19199040410" to "Alice"),
        )

        assertEquals("Alice", name)
    }

    @Test
    fun `group history rows fall back to the formatted handle when no contact exists`() {
        val name = resolveNotificationSenderLabel(
            address = "+19199040410",
            formattedAddress = "+1 (919) 904-0410",
            isGroup = true,
            conversationTitle = "Bobby's Capital",
            contactNameFor = { null },
        )

        assertEquals("+1 (919) 904-0410", name)
    }

    @Test
    fun `direct history still resolves a contact when the conversation title is blank`() {
        val name = resolveNotificationSenderLabel(
            address = "tel:+15551234567",
            isGroup = false,
            conversationTitle = "  ",
            contactNameFor = contactNames("+15551234567" to "Alice"),
        )

        assertEquals("Alice", name)
    }

    @Test
    fun `group history rows strip tel prefixes when no formatted address exists`() {
        val name = resolveNotificationSenderLabel(
            address = "tel:+19199040410",
            isGroup = true,
            conversationTitle = "Bobby's Capital",
            contactNameFor = { null },
        )

        assertEquals("+19199040410", name)
    }

    private fun contactNames(vararg entries: Pair<String, String>): (String) -> String? {
        val names = entries.associate { (address, name) ->
            ContactSync.normalizeAddress(address) to name
        }
        return { address -> names[ContactSync.normalizeAddress(address)] }
    }
}
