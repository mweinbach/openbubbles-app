package app.openbubbles.nativeapp.ui.chatinfo

import app.openbubbles.core.contacts.RawContact
import app.openbubbles.nativeapp.ui.findmy.FmFriendUi
import app.openbubbles.nativeapp.ui.findmy.FmPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContactDetailsTest {

    @Test
    fun `saved contact supplies name phones and emails`() {
        val details = resolveContactDetails(
            handleAddress = "tel:+15551234567",
            fallbackName = "+15551234567",
            contacts = listOf(
                RawContact(
                    id = "c1",
                    displayName = "Alice Example",
                    firstName = "Alice",
                    lastName = "Example",
                    avatarPath = "/avatars/alice.png",
                    addresses = listOf("+1 (555) 123-4567", "Alice@iCloud.com"),
                ),
            ),
        )

        assertEquals("Alice Example", details.displayName)
        assertEquals("/avatars/alice.png", details.avatarPath)
        assertEquals(listOf("+1 (555) 123-4567"), details.phones)
        assertEquals(listOf("Alice@iCloud.com"), details.emails)
    }

    @Test
    fun `unknown handle falls back to the conversation name`() {
        val details = resolveContactDetails(
            handleAddress = "mailto:friend@icloud.com",
            fallbackName = "Friend",
            contacts = emptyList(),
        )

        assertEquals("Friend", details.displayName)
        assertEquals(listOf("friend@icloud.com"), details.emails)
        assertTrue(details.phones.isEmpty())
        assertNull(details.avatarPath)
    }

    @Test
    fun `find my matches a friend by any contact address`() {
        val friends = listOf(
            FmFriendUi(
                id = "f1",
                name = "Mom",
                address = "mailto:mom@icloud.com",
                location = FmPoint(1.0, 2.0, 12.0, 1_700_000_000_000),
            ),
        )

        assertEquals("Mom", matchFriendLocation(listOf("+1555", "mom@icloud.com"), friends)?.name)
        assertNull(matchFriendLocation(listOf("stranger@icloud.com"), friends))
    }

    @Test
    fun `location states cover missing share unavailable and errors`() {
        val located = FmFriendUi(
            id = "f1",
            name = "Mom",
            address = "mom@icloud.com",
            location = FmPoint(1.0, 2.0),
        )
        val sharing = located.copy(location = null)

        assertIs<ContactLocationUi.Unavailable>(
            contactLocationFromFriends(listOf("mom@icloud.com"), listOf(located), available = false),
        )
        assertIs<ContactLocationUi.Failed>(
            contactLocationFromFriends(
                listOf("mom@icloud.com"),
                emptyList(),
                available = true,
                errorMessage = "offline",
            ),
        )
        assertIs<ContactLocationUi.NotSharing>(
            contactLocationFromFriends(listOf("other@icloud.com"), listOf(located), available = true),
        )
        assertIs<ContactLocationUi.NoFix>(
            contactLocationFromFriends(listOf("mom@icloud.com"), listOf(sharing), available = true),
        )
        assertIs<ContactLocationUi.Located>(
            contactLocationFromFriends(listOf("mom@icloud.com"), listOf(located), available = true),
        )
    }

    @Test
    fun `freshness and accuracy stay readable`() {
        val now = 1_700_000_000_000
        assertEquals("just now", locationFreshness(now - 10_000, now))
        assertEquals("8 min ago", locationFreshness(now - 8 * 60_000, now))
        assertEquals("3 h ago", locationFreshness(now - 3 * 3_600_000, now))
        assertEquals("18 m", locationAccuracy(18.2))
        assertEquals("1.2 km", locationAccuracy(1200.0))
        assertFalse(addressesMatch("mom@icloud.com", "dad@icloud.com"))
        assertTrue(addressesMatch("tel:+15551234567", "+1 (555) 123-4567"))
    }

    @Test
    fun `direct chats show the contact card even with no participant rows`() {
        assertTrue(shouldShowDirectContactCard(isGroup = false))
        assertFalse(shouldShowDirectContactCard(isGroup = true))
        assertFalse(shouldShowDirectContactCard(isGroup = null))
        assertEquals("+17033092799", directContactAddress("+17033092799", emptyList()))
        assertEquals("friend@icloud.com", directContactAddress(null, listOf("friend@icloud.com")))
        assertEquals("", directContactAddress(null, emptyList()))
    }

    @Test
    fun `merged 1-1 handles appear as phone and email on the card`() {
        val details = resolveContactDetails(
            handleAddress = "+17033092799",
            fallbackName = "Mark Linsangan",
            contacts = emptyList(),
        )
        val merged = mergeContactAddresses(
            details,
            listOf("+17033092799", "mark@icloud.com"),
        )
        assertEquals("Mark Linsangan", merged.displayName)
        assertEquals(listOf("+17033092799"), merged.phones)
        assertEquals(listOf("mark@icloud.com"), merged.emails)
    }
}
