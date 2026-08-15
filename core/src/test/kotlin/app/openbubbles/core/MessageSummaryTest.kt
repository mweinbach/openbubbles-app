package app.openbubbles.core

import app.openbubbles.core.model.MessageSummaryPartList
import app.openbubbles.core.model.addMessageSummaryPart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageSummaryTest {

    @Test
    fun `creates legacy compatible edit summary`() {
        val summary = addMessageSummaryPart(null, MessageSummaryPartList.EDITED, 0uL)

        assertTrue(summary.contains("\"editedParts\":[0]"))
        assertTrue(summary.contains("\"retractedParts\":[]"))
    }

    @Test
    fun `preserves existing history and dedupes parts`() {
        val existing = """[{"retractedParts":[],"editedContent":{"0":[{"date":1}]},"editedParts":[1]}]"""
        val once = addMessageSummaryPart(existing, MessageSummaryPartList.EDITED, 2uL)
        val twice = addMessageSummaryPart(once, MessageSummaryPartList.EDITED, 2uL)

        assertTrue(twice.contains("\"editedContent\":{\"0\":[{\"date\":1}]}"))
        assertTrue(twice.contains("\"editedParts\":[1,2]"))
        assertEquals(twice, twice.replace("[1,2,2]", "[1,2]"))
    }

    @Test
    fun `updates compact retraction array from older data`() {
        val summary = addMessageSummaryPart("""[{"rp":[1]}]""", MessageSummaryPartList.RETRACTED, 3uL)

        assertEquals("""[{"rp":[1,3]}]""", summary)
    }
}
