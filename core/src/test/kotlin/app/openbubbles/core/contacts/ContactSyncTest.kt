package app.openbubbles.core.contacts

import app.openbubbles.db.ContactV2
import app.openbubbles.db.Handle
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ContactSync] tests: native-id upsert (avatar preservation), phone/email
 * handle matching with country-code variants, and handle → contact display
 * resolution.
 */
class ContactSyncTest {

    private lateinit var store: BoxStore
    private lateinit var testDir: File
    private lateinit var sync: ContactSync

    @Before
    fun setUp() {
        testDir = java.nio.file.Files.createTempDirectory("ob-contacts-test").toFile()
        store = MyObjectBox.builder().directory(testDir).build()
        sync = ContactSync(store)
    }

    @After
    fun tearDown() {
        store.close()
        testDir.deleteRecursively()
    }

    private fun seedHandle(address: String, service: String = "iMessage", formatted: String? = null): Handle {
        val handle = Handle().apply {
            this.address = address
            this.service = service
            uniqueAddressAndService = "$address/$service"
            formattedAddress = formatted
        }
        store.boxFor(Handle::class.java).put(handle)
        if (handle.originalROWID == null) {
            handle.originalROWID = handle.id
            store.boxFor(Handle::class.java).put(handle)
        }
        return handle
    }

    private fun contactByNativeId(id: String): ContactV2? =
        store.boxFor(ContactV2::class.java).all.firstOrNull { it.nativeContactId == id }

    private fun raw(
        id: String,
        displayName: String? = "Display $id",
        firstName: String? = null,
        lastName: String? = null,
        avatarPath: String? = null,
        addresses: List<String> = emptyList(),
    ) = RawContact(id, displayName, firstName, lastName, avatarPath, addresses)

    // ------------------------------------------------------------------
    // Upsert
    // ------------------------------------------------------------------

    @Test
    fun `upsert creates rows and updates them by native contact id`() {
        sync.upsertContacts(listOf(raw("c1", displayName = "Alice Smith", addresses = listOf("Alice@ICLOUD.com"))))

        val created = contactByNativeId("c1")
        assertNotNull(created)
        assertEquals("Alice Smith", created.displayName)
        assertTrue(created.isNative)
        assertEquals(listOf("alice@icloud.com"), created.addresses)
        assertEquals(1, store.boxFor(ContactV2::class.java).count())

        sync.upsertContacts(listOf(raw("c1", displayName = "Alice Smith-Jones", addresses = listOf("alice@icloud.com"))))
        assertEquals(1, store.boxFor(ContactV2::class.java).count())
        assertEquals("Alice Smith-Jones", contactByNativeId("c1")?.displayName)
    }

    @Test
    fun `avatar path survives a sync without one and is replaced when provided`() {
        sync.upsertContacts(listOf(raw("c2", avatarPath = "/avatars/c2.png")))
        assertEquals("/avatars/c2.png", contactByNativeId("c2")?.avatarPath)

        // Platform reports no avatar (e.g. contact never had one exported):
        // keep the previously synced path.
        sync.upsertContacts(listOf(raw("c2", avatarPath = null)))
        assertEquals("/avatars/c2.png", contactByNativeId("c2")?.avatarPath)

        sync.upsertContacts(listOf(raw("c2", avatarPath = "/avatars/c2-new.png")))
        assertEquals("/avatars/c2-new.png", contactByNativeId("c2")?.avatarPath)
    }

    // ------------------------------------------------------------------
    // Handle matching
    // ------------------------------------------------------------------

    @Test
    fun `handles matched by email and phone variants`() {
        val phoneHandle = seedHandle("+15551234567")
        val emailHandle = seedHandle("Friend@Icloud.com")
        seedHandle("stranger@icloud.com")

        sync.upsertContacts(
            listOf(
                raw("c3", displayName = "Friend", addresses = listOf("(555) 123-4567", "friend@icloud.com")),
            ),
        )

        val contact = contactByNativeId("c3")
        assertNotNull(contact)
        val linked = contact.handles.map { it.address }.toSet()
        assertTrue("+15551234567" in linked, "phone handle should match via digit normalization, got $linked")
        assertTrue("Friend@Icloud.com" in linked, "email handle should match case-insensitively, got $linked")
        assertTrue("stranger@icloud.com" !in linked)
    }

    @Test
    fun `formatted handle addresses participate in matching`() {
        val handle = seedHandle("5559876543", formatted = "+1 (555) 987-6543")

        sync.upsertContacts(listOf(raw("c4", addresses = listOf("+15559876543"))))

        val contact = contactByNativeId("c4")
        assertNotNull(contact)
        assertTrue(contact.handles.any { it.id == handle.id })
    }

    @Test
    fun `relink connects contacts imported before history creates handles`() {
        sync.upsertContacts(
            listOf(raw("icloud:early", displayName = "Early Contact", addresses = listOf("late@icloud.com"))),
        )
        assertTrue(contactByNativeId("icloud:early")!!.handles.isEmpty())

        val lateHandle = seedHandle("mailto:Late@iCloud.com")
        val result = sync.relinkContacts()

        assertEquals(1, result.changedContacts)
        assertEquals(1, result.linkedContacts)
        assertEquals(1, result.linkedHandles)
        assertTrue(contactByNativeId("icloud:early")!!.handles.any { it.id == lateHandle.id })
        assertEquals("Early Contact", sync.displayInfoFor(lateHandle).name)
    }

    @Test
    fun `removing an address unlinks its handle`() {
        val phoneHandle = seedHandle("+15551234567")
        seedHandle("friend@icloud.com")

        sync.upsertContacts(
            listOf(raw("c5", addresses = listOf("(555) 123-4567", "friend@icloud.com"))),
        )
        assertTrue(contactByNativeId("c5")!!.handles.any { it.id == phoneHandle.id })

        // Re-sync with the phone number dropped from the platform contact.
        sync.upsertContacts(listOf(raw("c5", addresses = listOf("friend@icloud.com"))))
        val handles = contactByNativeId("c5")!!.handles.map { it.id }
        assertTrue(phoneHandle.id !in handles)
        assertEquals(1, handles.size)
    }

    @Test
    fun `contactsForHandles resolves handle ids to contacts`() {
        val h1 = seedHandle("one@icloud.com")
        val h2 = seedHandle("two@icloud.com")
        seedHandle("nobody@icloud.com")

        sync.upsertContacts(listOf(raw("c6", addresses = listOf("one@icloud.com", "two@icloud.com"))))

        val resolved = sync.contactsForHandles()
        val contact = contactByNativeId("c6")
        assertNotNull(contact)
        assertEquals(contact.id, resolved[h1.id]?.id)
        assertEquals(contact.id, resolved[h2.id]?.id)
        assertNull(resolved[store.boxFor(Handle::class.java).all.first { it.address == "nobody@icloud.com" }.id])
    }

    @Test
    fun `remove contacts applies tombstones without touching other sources`() {
        val removedHandle = seedHandle("removed@icloud.com")
        val keptHandle = seedHandle("kept@icloud.com")
        sync.upsertContacts(
            listOf(
                raw("icloud:removed", addresses = listOf("removed@icloud.com")),
                raw("android:kept", addresses = listOf("kept@icloud.com")),
            ),
        )

        assertEquals(1, sync.removeContacts(listOf("icloud:removed", "icloud:missing")))
        assertNull(contactByNativeId("icloud:removed"))
        assertNotNull(contactByNativeId("android:kept"))
        assertNull(sync.contactsForHandles()[removedHandle.id])
        assertEquals("android:kept", sync.contactsForHandles()[keptHandle.id]?.nativeContactId)
    }

    // ------------------------------------------------------------------
    // Display info
    // ------------------------------------------------------------------

    @Test
    fun `displayInfoFor prefers structured contact name and avatar`() {
        seedHandle("jane@icloud.com")
        sync.upsertContacts(
            listOf(raw("c7", displayName = "Jane Doe", firstName = "Jane", lastName = "Doe", avatarPath = "/av/jane.png", addresses = listOf("jane@icloud.com"))),
        )
        val handle = store.boxFor(Handle::class.java).all.first { it.address == "jane@icloud.com" }

        val (name, avatar) = sync.displayInfoFor(handle)
        assertEquals("Jane Doe", name) // first + last computed name
        assertEquals("/av/jane.png", avatar)
    }

    @Test
    fun `iCloud contact wins and native contact becomes fallback`() {
        val handle = seedHandle("friend@icloud.com")
        sync.upsertContacts(
            listOf(
                raw(
                    "android:friend",
                    displayName = "Android Friend",
                    avatarPath = "content://android/friend",
                    addresses = listOf("friend@icloud.com"),
                ),
                raw(
                    "icloud:friend",
                    displayName = "iOS Friend",
                    avatarPath = "/avatars/icloud-friend.img",
                    addresses = listOf("friend@icloud.com"),
                ),
            ),
        )

        assertEquals("icloud:friend", sync.contactsForHandles()[handle.id]?.nativeContactId)
        assertEquals("iOS Friend", sync.displayInfoFor(handle).name)
        assertEquals("/avatars/icloud-friend.img", sync.displayInfoFor(handle).avatar)
        assertEquals("iOS Friend", sync.displayInfoByHandleId()[handle.id]?.name)
        assertEquals(listOf("icloud:friend"), sync.preferredContacts().map { it.id })

        sync.removeContacts(listOf("icloud:friend"))

        assertEquals("android:friend", sync.contactsForHandles()[handle.id]?.nativeContactId)
        assertEquals("Android Friend", sync.displayInfoFor(handle).name)
        assertEquals("content://android/friend", sync.displayInfoFor(handle).avatar)
        assertTrue(sync.preferredContacts(includeNativeContacts = false).isEmpty())
    }

    @Test
    fun `nickname wins over structured name`() {
        seedHandle("bob@icloud.com")
        sync.upsertContacts(
            listOf(
                raw("c8", displayName = "Bob Robert Barker", firstName = "Robert", lastName = "Barker", addresses = listOf("bob@icloud.com")),
            ),
        )
        val row = contactByNativeId("c8")!!
        row.nickname = "Bobby"
        store.boxFor(ContactV2::class.java).put(row)

        val handle = store.boxFor(Handle::class.java).all.first { it.address == "bob@icloud.com" }
        assertEquals("Bobby", sync.displayInfoFor(handle).name)
    }

    @Test
    fun `displayInfoFor falls back to formatted then raw address`() {
        val formatted = seedHandle("+15550000000", formatted = "+1 (555) 000-0000")
        val (name, avatar) = sync.displayInfoFor(formatted)
        assertEquals("+1 (555) 000-0000", name)
        assertNull(avatar)

        val bare = seedHandle("ghost@icloud.com")
        assertEquals("ghost@icloud.com", sync.displayInfoFor(bare).name)
    }

    @Test
    fun `business handles display as Business`() {
        val biz = seedHandle("urn:biz:xzy123")
        assertEquals("Business", sync.displayInfoFor(biz).name)
    }

    // ------------------------------------------------------------------
    // Normalization helpers
    // ------------------------------------------------------------------

    @Test
    fun `phone variant generation covers country code shapes`() {
        val fromPlus = ContactSync.phoneNumberVariants("+11234567890")
        assertTrue("1234567890" in fromPlus)
        assertTrue("11234567890" in fromPlus)

        val fromBare = ContactSync.phoneNumberVariants("1234567890")
        assertTrue("+1234567890" in fromBare)
        assertTrue("234567890" in fromBare)
        assertTrue("+234567890" in fromBare)
    }

    @Test
    fun `normalization removes rust address schemes case insensitively`() {
        assertEquals("friend@icloud.com", ContactSync.normalizeAddress("MAILTO:Friend@iCloud.com"))
        assertEquals("+15551234567", ContactSync.normalizeAddress("TEL:+1 (555) 123-4567"))
    }
}
