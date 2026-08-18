package app.openbubbles.nativeapp.ui.chatcreator

import app.openbubbles.core.contacts.RawContact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipientAddressTest {
    @Test
    fun `parses emails and stripped phones`() {
        val email = parseAddress(" Alex@icloud.com ")
        requireNotNull(email)
        assertTrue(email.isEmail)
        assertEquals("Alex@icloud.com", email.display)
        assertEquals("alex@icloud.com", keyOf(email))

        val phone = parseAddress("(555) 123-4567")
        requireNotNull(phone)
        assertEquals("5551234567", phone.display)
        assertEquals("5551234567", keyOf(phone))
    }

    @Test
    fun `rejects names and unparseable handles`() {
        assertNull(parseAddress("John"))
        assertNull(parseAddress("ext.12"))
        assertNull(parseAddress(""))
    }

    @Test
    fun `name search still lists a contact whose primary address is unparseable`() {
        val rows = buildRows(
            listOf(
                RawContact(
                    id = "1",
                    displayName = "John Smith",
                    firstName = "John",
                    lastName = "Smith",
                    avatarPath = null,
                    addresses = listOf("ext.12", "+15551234567"),
                ),
            ),
            query = "John",
        )
        assertEquals(1, rows.size)
        assertEquals("John Smith", rows.single().name)
        assertEquals("ext.12", rows.single().primaryRaw)
        assertEquals("ext.12", rows.single().matchedRaw)
        assertEquals(
            listOf("ext.12", "+15551234567"),
            recipientAddressesToTry(rows.single()),
        )
        assertEquals("+15551234567", recipientAddressesToTry(rows.single()).firstNotNullOf { parseAddress(it) }.display)
    }

    @Test
    fun `query match prefers the matching address over the primary`() {
        val rows = buildRows(
            listOf(
                RawContact(
                    id = "1",
                    displayName = "Alex Chen",
                    firstName = "Alex",
                    lastName = "Chen",
                    avatarPath = null,
                    addresses = listOf("alex@icloud.com", "+15559876543"),
                ),
            ),
            query = "555",
        )
        val row = rows.single()
        assertEquals("+15559876543", row.matchedRaw)
        assertEquals("alex@icloud.com", row.primaryRaw)
        assertEquals(
            listOf("+15559876543", "alex@icloud.com"),
            recipientAddressesToTry(row),
        )
    }

    @Test
    fun `selecting a contact tries matched then remaining addresses`() {
        val row = ContactRowUi(
            contactId = "1",
            name = "John",
            primaryRaw = "not-an-address",
            matchedRaw = "not-an-address",
            addresses = listOf("not-an-address", "john@icloud.com"),
            primaryKey = "not-an-address",
            subtitle = "not-an-address",
            avatarPath = null,
        )
        val tried = recipientAddressesToTry(row)
        assertEquals(listOf("not-an-address", "john@icloud.com"), tried)
        val parsed = tried.firstNotNullOf { parseAddress(it) }
        assertEquals("john@icloud.com", parsed.display)
    }
}
