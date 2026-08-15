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
}
