package app.openbubbles.nativeapp.ui.chat

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.openbubbles.nativeapp.data.OutgoingMention
import app.openbubbles.nativeapp.data.outgoingMessageParts
import app.openbubbles.nativeapp.ui.chat.composer.MentionCandidate
import app.openbubbles.nativeapp.ui.chat.composer.activeMentionQuery
import app.openbubbles.nativeapp.ui.chat.composer.matchingMentionCandidates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import uniffi.rust_lib_bluebubbles.UPart

class MentionComposerTest {
    @Test
    fun activeQueryUsesTokenAtCursor() {
        assertEquals(
            "al",
            activeMentionQuery(TextFieldValue("hi @al", TextRange(6)))?.query,
        )
    }

    @Test
    fun matchingUsesNameAndHandle() {
        val candidates = listOf(
            MentionCandidate("Alex", "+15551234567"),
            MentionCandidate("Sam", "sam@icloud.com"),
        )
        assertEquals("Alex", matchingMentionCandidates("1555", candidates).single().displayName)
    }

    @Test
    fun outgoingPartsPreserveMentionHandle() {
        val parts = outgoingMessageParts(
            "Hi @Alex!",
            listOf(OutgoingMention(3, 8, "tel:+15551234567", "Alex")),
        )
        val mention = assertIs<UPart.Mention>(parts[1].part)
        assertEquals("tel:+15551234567", mention.mention)
        assertEquals("Alex", mention.text)
    }
}
