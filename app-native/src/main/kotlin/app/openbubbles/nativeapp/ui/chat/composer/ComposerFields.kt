package app.openbubbles.nativeapp.ui.chat.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

data class MentionCandidate(val displayName: String, val handle: String)

data class MentionQuery(val start: Int, val end: Int, val query: String)

internal fun activeMentionQuery(value: TextFieldValue): MentionQuery? {
    if (!value.selection.collapsed) return null
    val cursor = value.selection.start.coerceIn(0, value.text.length)
    val start = value.text.lastIndexOf('@', cursor - 1)
    if (start < 0) return null
    val token = value.text.substring(start + 1, cursor)
    if (token.any { it.isWhitespace() || it == '@' }) return null
    return MentionQuery(start, cursor, token)
}

internal fun matchingMentionCandidates(
    query: String,
    candidates: List<MentionCandidate>,
): List<MentionCandidate> = candidates
    .filter { candidate ->
        query.isBlank() || candidate.displayName.contains(query, ignoreCase = true) ||
            candidate.handle.contains(query, ignoreCase = true)
    }
    .distinctBy { it.handle.lowercase() }
    .take(8)

@Composable
fun ComposerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    candidates: List<MentionCandidate>,
    onMentionSelected: (start: Int, end: Int, candidate: MentionCandidate) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    var dismissedQueryStart by remember { mutableStateOf<Int?>(null) }
    var selectedIndex by remember { mutableStateOf(0) }
    LaunchedEffect(value) {
        if (fieldValue.text != value) fieldValue = TextFieldValue(value, TextRange(value.length))
    }
    val query = activeMentionQuery(fieldValue)?.takeUnless { it.start == dismissedQueryStart }
    val matches = query?.let { matchingMentionCandidates(it.query, candidates) }.orEmpty()
    Column(modifier = modifier) {
        if (matches.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(matches, key = { it.handle }) { candidate ->
                    AssistChip(
                        onClick = {
                            val active = activeMentionQuery(fieldValue) ?: return@AssistChip
                            val replacement = "@${candidate.displayName} "
                            fieldValue = fieldValue.copy(
                                text = fieldValue.text.replaceRange(active.start, active.end, replacement),
                                selection = TextRange(active.start + replacement.length),
                            )
                            dismissedQueryStart = null
                            onMentionSelected(active.start, active.end, candidate)
                        },
                        label = { Text("${candidate.displayName} · ${candidate.handle}") },
                    )
                }
            }
        }
        BasicTextField(
            value = fieldValue,
            onValueChange = { next ->
                fieldValue = next
                dismissedQueryStart = null
                selectedIndex = 0
                onValueChange(next.text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || matches.isEmpty()) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Escape -> {
                            dismissedQueryStart = query?.start
                            true
                        }
                        Key.DirectionDown -> {
                            selectedIndex = (selectedIndex + 1).coerceAtMost(matches.lastIndex)
                            true
                        }
                        Key.DirectionUp -> {
                            selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                            true
                        }
                        Key.Tab, Key.Enter -> {
                            val active = query ?: return@onPreviewKeyEvent false
                            val candidate = matches[selectedIndex]
                            val replacement = "@${candidate.displayName} "
                            fieldValue = fieldValue.copy(
                                text = fieldValue.text.replaceRange(active.start, active.end, replacement),
                                selection = TextRange(active.start + replacement.length),
                            )
                            onMentionSelected(active.start, active.end, candidate)
                            true
                        }
                        else -> false
                    }
                }
                .semantics { contentDescription = "Message input" },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            maxLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    if (fieldValue.text.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
fun SubjectField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            Text("Subject", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).padding(start = 10.dp).semantics { contentDescription = "Subject" },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
            )
        }
    }
}
