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

    private fun contactNames(vararg entries: Pair<String, String>): (String) -> String? {
        val names = entries.associate { (address, name) ->
            ContactSync.normalizeAddress(address) to name
        }
        return { address -> names[ContactSync.normalizeAddress(address)] }
    }
}
