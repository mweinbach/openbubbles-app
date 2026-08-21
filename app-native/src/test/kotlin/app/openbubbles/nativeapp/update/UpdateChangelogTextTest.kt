package app.openbubbles.nativeapp.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateChangelogTextTest {

    @Test
    fun `blocks become newline separated lines with bullet prefixes`() {
        val layout = layoutChangelogForNotification("### Fixes\n\n- one\n- two\n\nParagraph.")
        assertEquals("Fixes\n•  one\n•  two\nParagraph.", layout.text)
    }

    @Test
    fun `headings are bold and enlarged across the whole line`() {
        val layout = layoutChangelogForNotification("### Fixes and more")
        assertEquals("Fixes and more", layout.text)
        val bold = layout.spans.filter { it.kind == ChangelogSpanKind.Bold }
        assertEquals(1, bold.size)
        assertEquals(0, bold.single().start)
        assertEquals(layout.text.length, bold.single().end)
        assertTrue(layout.spans.any { it.kind == ChangelogSpanKind.Heading })
    }

    @Test
    fun `inline markup becomes span regions on plain text`() {
        val layout = layoutChangelogForNotification("- adds **dark** mode and `version_code`")
        assertEquals("•  adds dark mode and version_code", layout.text)
        val bold = layout.spans.single { it.kind == ChangelogSpanKind.Bold }
        assertEquals("dark", layout.text.substring(bold.start, bold.end))
        val mono = layout.spans.single { it.kind == ChangelogSpanKind.Mono }
        assertEquals("version_code", layout.text.substring(mono.start, mono.end))
    }

    @Test
    fun `links keep their url`() {
        val layout = layoutChangelogForNotification("see [the notes](https://example.com/r)")
        assertEquals("see the notes", layout.text)
        val link = layout.spans.single { it.kind == ChangelogSpanKind.Link }
        assertEquals("https://example.com/r", link.url)
        assertEquals("the notes", layout.text.substring(link.start, link.end))
    }

    @Test
    fun `nested bullets indent with dashes`() {
        val layout = layoutChangelogForNotification("- parent\n    - child")
        assertEquals("•  parent\n    –  child", layout.text)
    }

    @Test
    fun `overflow ellipsizes at a block boundary`() {
        val notes = "- short\n\n- ${"x".repeat(50)}\n\n- tail"
        val layout = layoutChangelogForNotification(notes, maxChars = 60)
        assertTrue(layout.text.startsWith("•  short"))
        assertTrue(layout.text.endsWith("…"))
        assertFalse(layout.text.contains("tail"))
        assertTrue(layout.spans.all { it.end <= layout.text.length })
    }

    @Test
    fun `blank notes stay empty`() {
        assertEquals("", layoutChangelogForNotification("   \n").text)
        assertEquals("", changelogSummary("   "))
    }

    @Test
    fun `summary strips markup and skips headings`() {
        assertEquals("Dark mode is here", changelogSummary("### v9\n\n**Dark** mode is here"))
    }

    @Test
    fun `summary ellipsizes at the budget`() {
        val long = changelogSummary("- ${"word ".repeat(40)}")
        assertEquals(80, long.length)
        assertTrue(long.endsWith("…"))
    }
}
