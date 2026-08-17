package app.openbubbles.nativeapp.data

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ICloudContactsTest {

    @Test
    fun `parses names and normalized contact address schemes`() {
        val parsed = ICloudVCardParser.parse(
            """
                BEGIN:VCARD
                VERSION:4.0
                FN:Jane Doe
                N:Doe;Jane;;;
                EMAIL;VALUE=uri:mailto:Jane@Example.com
                TEL;VALUE=uri:tel:+1-555-123-4567
                END:VCARD
            """.trimIndent(),
        )

        assertEquals("Jane Doe", parsed.displayName)
        assertEquals("Jane", parsed.firstName)
        assertEquals("Doe", parsed.lastName)
        assertEquals(listOf("Jane@Example.com", "+1-555-123-4567"), parsed.addresses)
    }

    @Test
    fun `unfolds and decodes text and inline photos`() {
        val parsed = ICloudVCardParser.parse(
            "BEGIN:VCARD\r\n" +
                "VERSION:3.0\r\n" +
                "FN;ENCODING=QUOTED-PRINTABLE:Jos=C3=A9 Appleseed\r\n" +
                "EMAIL:long.address@exam\r\n ple.com\r\n" +
                "PHOTO;ENCODING=b:SGVsbG8=\r\n" +
                "END:VCARD\r\n",
        )

        assertEquals("José Appleseed", parsed.displayName)
        assertEquals(listOf("long.address@example.com"), parsed.addresses)
        assertContentEquals("Hello".toByteArray(), parsed.photo)
    }

    @Test
    fun `parses data URI and iCloud PHOTO URI values`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) +
            ByteArray(12) { 1 }
        val encoded = java.util.Base64.getEncoder().encodeToString(jpeg)
        val data = ICloudVCardParser.parse(
            """
                BEGIN:VCARD
                VERSION:4.0
                FN:Data Photo
                EMAIL:data@example.com
                PHOTO:data:image/jpeg;base64,$encoded
                END:VCARD
            """.trimIndent(),
        )
        assertContentEquals(jpeg, data.photo)
        assertEquals(null, data.photoUri)

        val uri = ICloudVCardParser.parse(
            """
                BEGIN:VCARD
                VERSION:3.0
                FN:Uri Photo
                EMAIL:uri@example.com
                PHOTO;X-ABCROP-RECTANGLE=ABClipRect_1&0&0&640&640&0,1;VALUE=uri:https://p48-contacts.icloud.com:443/123/carddavhome/card/Photo/~CN~abc
                END:VCARD
            """.trimIndent(),
        )
        assertEquals(null, uri.photo)
        assertEquals(
            "https://p48-contacts.icloud.com:443/123/carddavhome/card/Photo/~CN~abc",
            uri.photoUri,
        )
    }

    @Test
    fun `resolveContactPhoto prefers inline bytes then downloads URI`() {
        val inline = ParsedVCard("A", "A", null, listOf("a@example.com"), "img".toByteArray(), "https://example.com/a")
        assertContentEquals("img".toByteArray(), resolveContactPhoto(inline) { error("should not download") })

        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(12) { 1 }
        val remote = ParsedVCard("B", "B", null, listOf("b@example.com"), null, "https://p01-contacts.icloud.com/photo")
        var requested: String? = null
        val downloaded = resolveContactPhoto(remote) { uri ->
            requested = uri
            jpeg
        }
        assertEquals("https://p01-contacts.icloud.com/photo", requested)
        assertContentEquals(jpeg, downloaded)

        val html = resolveContactPhoto(remote) { "<html>login</html>".toByteArray() }
        assertEquals(null, html)
    }

    @Test
    fun `stale photo cache version forces a full CardDAV recrawl`() {
        assertEquals(
            null to null,
            cardDavCursorForPhotoCache("ctag-1", "token-1", storedPhotoVersion = 1),
        )
        assertEquals(
            "ctag-1" to "token-1",
            cardDavCursorForPhotoCache("ctag-1", "token-1", storedPhotoVersion = ICLOUD_PHOTO_CACHE_VERSION),
        )
    }

    @Test
    fun `contact photos are only persisted when the bytes are an image`() {
        val directory = java.nio.file.Files.createTempDirectory("ob-contact-photo").toFile()
        try {
            val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(12) { 1 }
            assertEquals(jpeg.toList(), writeContactPhoto(directory, "ok", jpeg)?.readBytes()?.toList())
            assertEquals(null, writeContactPhoto(directory, "html", "<html>nope</html>".toByteArray()))
            assertEquals(null, writeContactPhoto(directory, "empty", null))
        } finally {
            directory.deleteRecursively()
        }
    }
}
