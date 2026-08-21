package app.openbubbles.nativeapp.update

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import app.openbubbles.nativeapp.ui.settings.NoteBlock
import app.openbubbles.nativeapp.ui.settings.parseUpdateNotes

/**
 * Release-note markdown for the "update ready" notification.
 *
 * The feed ships one `## vX.Y.Z` section of assets/changelog/changelog.md and
 * [parseUpdateNotes] owns that grammar. Notifications render framework
 * `Spanned` text, not Compose, so the parsed blocks are re-laid-out as a
 * plain string plus span requests: [layoutChangelogForNotification] is pure
 * and unit-tested, and [changelogNotificationText] is the thin `android.text`
 * application of its output.
 */

/** Expanded-notification budget, matching the old raw-notes cap. */
private const val MAX_BIG_TEXT_CHARS = 1000

internal enum class ChangelogSpanKind { Bold, Italic, Mono, Heading, Link }

internal data class ChangelogSpan(
    val start: Int,
    val end: Int,
    val kind: ChangelogSpanKind,
    val url: String? = null,
)

internal data class ChangelogLayout(val text: String, val spans: List<ChangelogSpan>)

/**
 * Lays the notes out as newline-separated lines — headings bare, bullets
 * prefixed like the Settings sheet ("•", nested "–") — recording which
 * regions carry which emphasis. Once [maxChars] is reached the next block is
 * replaced by an ellipsis instead of being sliced mid-markup.
 */
internal fun layoutChangelogForNotification(
    markdown: String,
    maxChars: Int = MAX_BIG_TEXT_CHARS,
): ChangelogLayout {
    val text = StringBuilder()
    val spans = mutableListOf<ChangelogSpan>()
    for (block in parseUpdateNotes(markdown)) {
        val (line, lineSpans) = renderBlock(block)
        if (text.isNotEmpty() && text.length + 1 + line.length > maxChars) {
            text.append('\n').append('…')
            break
        }
        if (text.isNotEmpty()) text.append('\n')
        val offset = text.length
        text.append(line)
        spans += lineSpans.map { it.copy(start = it.start + offset, end = it.end + offset) }
    }
    return ChangelogLayout(text.toString(), spans)
}

/** Applies the layout's span requests as framework spans for notifications. */
internal fun changelogNotificationText(markdown: String): CharSequence {
    val layout = layoutChangelogForNotification(markdown)
    if (layout.text.isEmpty()) return ""
    val out = SpannableStringBuilder(layout.text)
    for (span in layout.spans) {
        when (span.kind) {
            ChangelogSpanKind.Bold ->
                out.setSpan(StyleSpan(Typeface.BOLD), span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            ChangelogSpanKind.Italic ->
                out.setSpan(StyleSpan(Typeface.ITALIC), span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            ChangelogSpanKind.Mono ->
                out.setSpan(TypefaceSpan("monospace"), span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            ChangelogSpanKind.Heading ->
                out.setSpan(RelativeSizeSpan(1.15f), span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            ChangelogSpanKind.Link ->
                out.setSpan(URLSpan(span.url), span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    return out
}

/**
 * Collapsed-line summary: the first content line with inline markup stripped,
 * ellipsized at [maxChars]. Headings are skipped so "### v9" never shows.
 */
internal fun changelogSummary(markdown: String, maxChars: Int = 80): String {
    val first = parseUpdateNotes(markdown)
        .firstOrNull { it !is NoteBlock.Heading }
        ?.text?.text
        ?: return ""
    val flat = first.replace(WHITESPACE_RUNS, " ").trim()
    if (flat.isEmpty()) return ""
    return if (flat.length <= maxChars) flat else flat.take(maxChars - 1).trimEnd() + "…"
}

private val WHITESPACE_RUNS = Regex("\\s+")

private fun renderBlock(block: NoteBlock): Pair<String, List<ChangelogSpan>> {
    val text = StringBuilder()
    val spans = mutableListOf<ChangelogSpan>()
    when (block) {
        is NoteBlock.Heading -> {
            text.append(block.text.text)
            spans += ChangelogSpan(0, text.length, ChangelogSpanKind.Bold)
            spans += ChangelogSpan(0, text.length, ChangelogSpanKind.Heading)
        }
        is NoteBlock.Bullet -> text.append(bulletPrefix(block.level))
        is NoteBlock.Paragraph -> Unit
    }
    if (block !is NoteBlock.Heading) copyInline(text, spans, block.text)
    return text.toString() to spans
}

private fun bulletPrefix(level: Int): String = when (level) {
    0 -> "•  "
    else -> "    ".repeat(level) + "–  "
}

private fun copyInline(out: StringBuilder, spans: MutableList<ChangelogSpan>, source: AnnotatedString) {
    val offset = out.length
    out.append(source.text)
    for (range in source.spanStyles) {
        val style = range.item
        if (style.fontWeight == FontWeight.Bold) {
            spans += ChangelogSpan(offset + range.start, offset + range.end, ChangelogSpanKind.Bold)
        }
        if (style.fontStyle == FontStyle.Italic) {
            spans += ChangelogSpan(offset + range.start, offset + range.end, ChangelogSpanKind.Italic)
        }
        if (style.fontFamily == FontFamily.Monospace) {
            spans += ChangelogSpan(offset + range.start, offset + range.end, ChangelogSpanKind.Mono)
        }
    }
    for (link in source.getLinkAnnotations(0, source.length)) {
        val url = link.item as? LinkAnnotation.Url ?: continue
        spans += ChangelogSpan(offset + link.start, offset + link.end, ChangelogSpanKind.Link, url.url)
    }
}
