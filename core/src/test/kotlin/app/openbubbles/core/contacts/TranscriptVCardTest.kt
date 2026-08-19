package app.openbubbles.core.contacts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TranscriptVCardTest {
    @Test
    fun `parses name phone and email`() {
        val card = TranscriptVCardParser.parse(
            """
            BEGIN:VCARD
            VERSION:3.0
            FN:Ada Lovelace
            N:Lovelace;Ada;;;
            TEL;TYPE=CELL:+15555550100
            EMAIL:ada@example.com
            ORG:Analytical Engine
            END:VCARD
            """.trimIndent(),
        )
        assertEquals("Ada Lovelace", card?.displayName)
        assertEquals(listOf("+15555550100"), card?.phones)
        assertEquals(listOf("ada@example.com"), card?.emails)
        assertEquals("Analytical Engine", card?.organization)
    }

    @Test
    fun `rejects empty payloads`() {
        assertNull(TranscriptVCardParser.parse(""))
        assertNull(TranscriptVCardParser.parse("not a card"))
    }
}
