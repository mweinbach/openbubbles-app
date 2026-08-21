package app.openbubbles.nativeapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Minimal renderer for the project's release notes. The feed
 * (Update Ledger appcast → `UpdateManifest.notes`) carries the body of one
 * `## vX.Y.Z` section of assets/changelog/changelog.md, so the input is a
 * constrained markdown: `#`-headings, `-` bullets (4-space nesting),
 * paragraphs, `**bold**`, `*italic*`, `` `code` `` and `[text](url)` links.
 * Anything outside that subset renders literally rather than disappearing.
 */

internal sealed interface NoteBlock {
    /** Inline-rendered body; every block kind carries one. */
    val text: AnnotatedString

    data class Heading(val level: Int, override val text: AnnotatedString) : NoteBlock
    data class Bullet(val level: Int, override val text: AnnotatedString) : NoteBlock
    data class Paragraph(override val text: AnnotatedString) : NoteBlock
}

/** Inline span styles the parser applies; themed by the composable caller. */
internal data class NoteInlineStyles(
    val bold: SpanStyle = SpanStyle(fontWeight = FontWeight.Bold),
    val italic: SpanStyle = SpanStyle(fontStyle = FontStyle.Italic),
    val code: SpanStyle = SpanStyle(fontFamily = FontFamily.Monospace),
    val link: SpanStyle = SpanStyle(textDecoration = TextDecoration.Underline),
) {
    companion object {
        val Default = NoteInlineStyles()
    }
}

private val headingPattern = Regex("^(#{1,6})\\s+(.*)$")
private val bulletPattern = Regex("^(\\s*)(?:[-*+]|•)\\s+(.*)$")

/**
 * Block-level parse: headings, bullets, blank-line-separated paragraphs.
 * A plain line directly under a bullet folds into it (CommonMark's lazy
 * continuation) — the changelog wraps long bullets across lines this way.
 */
internal fun parseUpdateNotes(
    markdown: String,
    styles: NoteInlineStyles = NoteInlineStyles.Default,
): List<NoteBlock> {
    val blocks = mutableListOf<NoteBlock>()
    val paragraph = StringBuilder()
    val bullet = StringBuilder()
    var bulletLevel = 0

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += NoteBlock.Paragraph(parseInlineMarkdown(paragraph.toString(), styles))
            paragraph.clear()
        }
    }

    fun flushBullet() {
        if (bullet.isNotEmpty()) {
            blocks += NoteBlock.Bullet(bulletLevel, parseInlineMarkdown(bullet.toString(), styles))
            bullet.clear()
        }
    }

    fun flushBlocks() {
        flushParagraph()
        flushBullet()
    }

    for (rawLine in markdown.lines()) {
        val line = rawLine.trimEnd()
        when {
            line.isBlank() -> flushBlocks()
            headingPattern.matches(line.trimStart()) -> {
                flushBlocks()
                val match = headingPattern.find(line.trimStart())!!
                blocks += NoteBlock.Heading(
                    level = match.groupValues[1].length,
                    text = parseInlineMarkdown(match.groupValues[2].trim(), styles),
                )
            }
            bulletPattern.matches(line) -> {
                flushBlocks()
                val match = bulletPattern.find(line)!!
                // The changelog nests with 4-space indents; round up so a
                // stray 2-space indent still reads as a child bullet.
                bulletLevel = ((match.groupValues[1].length + 3) / 4).coerceIn(0, 3)
                bullet.append(match.groupValues[2].trim())
            }
            else -> {
                val buffer = if (bullet.isNotEmpty()) bullet else paragraph
                if (buffer.isNotEmpty()) buffer.append(' ')
                buffer.append(line.trim())
            }
        }
    }
    flushBlocks()
    return blocks
}

/**
 * Inline parse: `` `code` ``, `**bold**` / `__bold__`, `*italic*` / `_italic_`,
 * `[text](url)`. Underscore emphasis only counts at a word boundary so
 * identifiers like `version_code` stay literal. Unmatched markers are kept
 * as plain text.
 */
internal fun parseInlineMarkdown(
    text: String,
    styles: NoteInlineStyles = NoteInlineStyles.Default,
): AnnotatedString = buildAnnotatedString {
    appendInline(text, 0, text.length, styles)
}

private fun AnnotatedString.Builder.appendInline(
    text: String,
    start: Int,
    end: Int,
    styles: NoteInlineStyles,
) {
    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) {
            append(plain.toString())
            plain.clear()
        }
    }

    var i = start
    while (i < end) {
        when (val c = text[i]) {
            '`' -> {
                val close = text.indexOf('`', i + 1)
                if (close > i && close < end) {
                    flush()
                    withStyle(styles.code) { append(text.substring(i + 1, close)) }
                    i = close + 1
                } else {
                    plain.append(c)
                    i++
                }
            }
            '*', '_' -> {
                val marker = if (i + 1 < end && text[i + 1] == c) "$c$c" else "$c"
                // `_`/`__` inside a word (snake_case) is never emphasis.
                val midWord = c == '_' && i > start && text[i - 1].isLetterOrDigit()
                // Flanking: the opener must be followed by a non-space and
                // the closer preceded by one, so "2 * 3" stays literal.
                val opens = !midWord && i + marker.length < end &&
                    !text[i + marker.length].isWhitespace()
                var close = -1
                if (opens) {
                    var scan = i + marker.length
                    while (true) {
                        val found = text.indexOf(marker, scan)
                        if (found < 0 || found >= end) break
                        if (found > i + marker.length && !text[found - 1].isWhitespace()) {
                            close = found
                            break
                        }
                        scan = found + 1
                    }
                }
                if (close >= 0) {
                    flush()
                    val style = if (marker.length == 2) styles.bold else styles.italic
                    withStyle(style) { appendInline(text, i + marker.length, close, styles) }
                    i = close + marker.length
                } else {
                    plain.append(c)
                    i++
                }
            }
            '[' -> {
                val closeBracket = text.indexOf("](", i + 1)
                val closeParen = if (closeBracket > 0) text.indexOf(')', closeBracket + 2) else -1
                if (closeBracket in (i + 1) until end && closeParen >= closeBracket + 2 && closeParen < end) {
                    flush()
                    val url = text.substring(closeBracket + 2, closeParen)
                    withLink(LinkAnnotation.Url(url)) {
                        withStyle(styles.link) { appendInline(text, i + 1, closeBracket, styles) }
                    }
                    i = closeParen + 1
                } else {
                    plain.append(c)
                    i++
                }
            }
            else -> {
                plain.append(c)
                i++
            }
        }
    }
    flush()
}

/** Rendered release notes: themed headings, indented bullets, paragraphs. */
@Composable
internal fun UpdateNotes(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val styles = remember(colorScheme) {
        NoteInlineStyles(
            code = SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = colorScheme.surfaceContainerHighest,
            ),
            link = SpanStyle(
                color = colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            ),
        )
    }
    val blocks = remember(markdown, styles) { parseUpdateNotes(markdown, styles) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is NoteBlock.Heading -> Text(
                    text = block.text,
                    style = if (block.level <= 2) {
                        MaterialTheme.typography.titleMediumEmphasized
                    } else {
                        MaterialTheme.typography.titleSmallEmphasized
                    },
                    modifier = Modifier.padding(top = 6.dp),
                )
                is NoteBlock.Bullet -> Row(
                    modifier = Modifier.padding(start = (block.level * 16).dp),
                ) {
                    Text(
                        text = if (block.level == 0) "•" else "–",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
                is NoteBlock.Paragraph -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
