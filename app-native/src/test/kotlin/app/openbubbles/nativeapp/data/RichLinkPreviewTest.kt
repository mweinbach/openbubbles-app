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
}
