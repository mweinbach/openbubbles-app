package app.openbubbles.nativeapp.data

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RichLinkPreviewTest {
    @Test
    fun `parses Apple metadata and embedded image`() {
        val preview = parseRichLinkPreview(
            metadataJson = """
                {
                  "data": {
                    "originalURL": {"${'$'}class":"NSURL","NS.base":"${'$'}null","NS.relative":"https://example.com/story"},
                    "URL": {"${'$'}class":"NSURL","NS.base":"${'$'}null","NS.relative":"https://example.com/story"},
                    "title": "Example story",
                    "summary": "A useful summary",
                    "image": {"${'$'}class":"RichLinkImageAttachmentSubstitute","MIMEType":"image/png","richLinkImageAttachmentSubstituteIndex":0}
                  },
                  "attachments": [[137,80,78,71]]
                }
            """.trimIndent(),
            messageText = "https://example.com/story",
        )

        requireNotNull(preview)
        assertEquals("https://example.com/story", preview.url)
        assertEquals("example.com", preview.displayHost)
        assertEquals("Example story", preview.title)
        assertEquals("A useful summary", preview.summary)
        assertEquals("image/png", preview.imageMime)
        assertContentEquals(byteArrayOf(137.toByte(), 80, 78, 71), preview.imageBytes)
    }

    @Test
    fun `plain message URL produces clickable fallback`() {
        val preview = parseRichLinkPreview(null, "See https://www.example.com/path?q=1.")

        requireNotNull(preview)
        assertEquals("https://www.example.com/path?q=1", preview.url)
        assertEquals("example.com", preview.displayHost)
        assertNull(preview.title)
        assertNull(preview.imageBytes)
    }

    @Test
    fun `non web text has no preview`() {
        assertNull(parseRichLinkPreview(null, "No links here"))
    }

    /**
     * Previews are re-parsed (fresh byte arrays) on every transcript
     * emission; structural equality is what lets the identical-frame
     * deduplication and Compose skipping work for link bubbles.
     */
    @Test
    fun `equal content with distinct byte array instances compares equal`() {
        fun preview() = RichLinkPreview(
            url = "https://example.com/story",
            displayHost = "example.com",
            title = "Example story",
            summary = "A useful summary",
            imageBytes = byteArrayOf(137.toByte(), 80, 78, 71),
            imageMime = "image/png",
            iconBytes = byteArrayOf(1, 2, 3),
            iconMime = "image/x-icon",
        )

        assertEquals(preview(), preview())
        assertEquals(preview().hashCode(), preview().hashCode())
    }

    @Test
    fun `mixed text drops the preview URL and keeps the caption`() {
        assertEquals(
            "Check this out",
            displayTextForRichLink(
                "Check this out https://www.example.com/path?q=1",
                "https://www.example.com/path?q=1",
            ),
        )
        assertEquals(
            "before after",
            displayTextForRichLink(
                "before https://example.com/story after",
                "https://example.com/story/",
            ),
        )
    }

    @Test
    fun `url-only text collapses to empty so the card can stand alone`() {
        assertEquals(
            "",
            displayTextForRichLink(
                "https://www.nps.gov/yose/index.htm",
                "https://www.nps.gov/yose/index.htm",
            ),
        )
    }

    @Test
    fun `unrelated text is left alone when the URL is only in metadata`() {
        assertEquals(
            "look at this",
            displayTextForRichLink("look at this", "https://example.com/story"),
        )
    }
}
