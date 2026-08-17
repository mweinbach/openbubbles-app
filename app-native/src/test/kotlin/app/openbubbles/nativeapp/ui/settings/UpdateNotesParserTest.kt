package app.openbubbles.nativeapp.ui.settings

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UpdateNotesParserTest {

    @Test
    fun `headings and bullets and paragraphs keep their order`() {
        val blocks = parseUpdateNotes(
            """
            ### Enhancements

            First paragraph.

            - one
            - two

            ### Fixes

            - three
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                NoteBlock.Heading::class,
                NoteBlock.Paragraph::class,
                NoteBlock.Bullet::class,
                NoteBlock.Bullet::class,
                NoteBlock.Heading::class,
                NoteBlock.Bullet::class,
            ),
            blocks.map { it::class },
        )
        assertEquals(3, (blocks[0] as NoteBlock.Heading).level)
        assertEquals("Fixes", (blocks[4] as NoteBlock.Heading).text.text)
    }

    @Test
    fun `four space indents become nested bullets`() {
        val blocks = parseUpdateNotes("- parent\n    - child\n        - grandchild")
        assertEquals(
            listOf(0, 1, 2),
            blocks.filterIsInstance<NoteBlock.Bullet>().map { it.level },
        )
    }

    @Test
    fun `soft line breaks join into one paragraph`() {
        val blocks = parseUpdateNotes("first line\nsecond line\n\nnext paragraph")
        assertEquals(2, blocks.size)
        assertEquals("first line second line", (blocks[0] as NoteBlock.Paragraph).text.text)
        assertEquals("next paragraph", (blocks[1] as NoteBlock.Paragraph).text.text)
    }

    @Test
    fun `bold and italic produce weighted spans`() {
        val text = parseInlineMarkdown("a **bold** and *italic* word")
        assertEquals("a bold and italic word", text.text)
        val bold = text.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertEquals("bold", text.text.substring(bold.start, bold.end))
        val italic = text.spanStyles.single { it.item.fontStyle == FontStyle.Italic }
        assertEquals("italic", text.text.substring(italic.start, italic.end))
    }

    @Test
    fun `code spans switch to monospace`() {
        val text = parseInlineMarkdown("set `version_code` now")
        val code = text.spanStyles.single { it.item.fontFamily == FontFamily.Monospace }
        assertEquals("version_code", text.text.substring(code.start, code.end))
    }

    @Test
    fun `underscores inside words stay literal`() {
        val text = parseInlineMarkdown("the field version_code_here stays")
        assertEquals("the field version_code_here stays", text.text)
        assertTrue(text.spanStyles.none { it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun `unmatched markers stay literal`() {
        val text = parseInlineMarkdown("2 * 3 and a ** stray")
        assertEquals("2 * 3 and a ** stray", text.text)
    }

    @Test
    fun `links carry a url annotation`() {
        val text = parseInlineMarkdown("see the [release page](https://example.com/r) now")
        assertEquals("see the release page now", text.text)
        val links = text.getLinkAnnotations(0, text.length)
        assertEquals(1, links.size)
        val link = assertIs<LinkAnnotation.Url>(links.single().item)
        assertEquals("https://example.com/r", link.url)
        assertEquals(
            "release page",
            text.text.substring(links.single().start, links.single().end),
        )
    }

    @Test
    fun `the v2 changelog section parses end to end`() {
        // A trimmed copy of the real ## v2.0.0 body from
        // assets/changelog/changelog.md — the exact shape the feed ships.
        val notes = """
            ### Native client

            - The app is rebuilt as a native Kotlin + Rust client: no Dart/Flutter runtime,
              same application id, in-place upgrade over the previous client.
            - Direct Apple messaging without a Mac server.

            ### Self-updating

            - In-app updates published through GitHub Releases.
        """.trimIndent()
        val blocks = parseUpdateNotes(notes)
        assertEquals(2, blocks.filterIsInstance<NoteBlock.Heading>().size)
        assertEquals(3, blocks.filterIsInstance<NoteBlock.Bullet>().size)
        // Continuation lines fold into their bullet.
        val first = blocks.filterIsInstance<NoteBlock.Bullet>().first()
        assertTrue(first.text.text.endsWith("client."))
    }
}
