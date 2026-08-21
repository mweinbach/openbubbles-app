package app.openbubbles.nativeapp.ui.chat

import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageReactionUi
import app.openbubbles.nativeapp.data.MessageStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageReactionsTest {

    private val names = mapOf(
        "alex@icloud.com" to "Alex Chen",
        "mark@icloud.com" to "Mark Reed",
    )

    @Test
    fun `reactions group by emoji in first-reacted order`() {
        val groups = groupReactions(
            listOf(
                reaction("😂", "mark@icloud.com"),
                reaction("❤️", "alex@icloud.com"),
                mine("❤️"),
            ),
            names::get,
        )

        assertEquals(listOf("😂", "❤️"), groups.map { it.emoji })
        assertEquals(listOf("Mark Reed"), groups[0].reactors.map { it.name })
        assertEquals(listOf("Alex Chen", SelfReactorName), groups[1].reactors.map { it.name })
    }

    @Test
    fun `unresolved handles fall back to the raw address`() {
        val groups = groupReactions(listOf(reaction("👍", "+15551234567")))

        assertEquals("+15551234567", groups.single().reactors.single().name)
    }

    @Test
    fun `one sender never appears twice in a group`() {
        val groups = groupReactions(
            listOf(reaction("👍", "alex@icloud.com"), reaction("👍", "alex@icloud.com")),
            names::get,
        )

        assertEquals(listOf("Alex Chen"), groups.single().reactors.map { it.name })
    }

    @Test
    fun `my active reaction marks the picker selection`() {
        assertEquals("❤️", myReactionEmoji(listOf(reaction("👍", "alex@icloud.com"), mine("❤️"))))
        assertNull(myReactionEmoji(listOf(reaction("👍", "alex@icloud.com"))))
    }

    @Test
    fun `tapping the selected reaction removes it while another reaction enables`() {
        assertFalse(enableTappedReaction(selectedEmoji = "👍", tappedEmoji = "👍"))
        assertTrue(enableTappedReaction(selectedEmoji = "👍", tappedEmoji = "❤️"))
    }

    @Test
    fun `action sheet selection is scoped to the pressed part`() {
        val reactions = listOf(
            MessageReactionUi("❤️", null, true, targetPart = 0L),
            MessageReactionUi("👍", null, true, targetPart = 1L),
        )

        assertEquals("❤️", myReactionEmoji(reactionsForPart(reactions, 0L)))
        assertEquals("👍", myReactionEmoji(reactionsForPart(reactions, 1L)))
    }

    @Test
    fun `group labels name every reactor`() {
        val one = groupReactions(listOf(reaction("👍", "alex@icloud.com")), names::get).single()
        assertEquals("Alex Chen reacted 👍", reactionGroupLabel(one))

        val two = groupReactions(
            listOf(reaction("👍", "alex@icloud.com"), mine("👍")),
            names::get,
        ).single()
        assertEquals("Alex Chen and You reacted 👍", reactionGroupLabel(two))

        val three = groupReactions(
            listOf(reaction("👍", "alex@icloud.com"), reaction("👍", "mark@icloud.com"), mine("👍")),
            names::get,
        ).single()
        assertEquals("Alex Chen, Mark Reed, and You reacted 👍", reactionGroupLabel(three))
    }

    @Test
    fun `bubble summary falls back to the newest reaction alone`() {
        val summary = bubbleReactionSummary(message(reactionEmoji = "❤️"))

        assertEquals(listOf("❤️"), summary?.emojis)
        assertEquals("Reaction ❤️", summary?.label)
    }

    @Test
    fun `bubble summary collapses distinct emoji and counts them`() {
        val summary = bubbleReactionSummary(
            message(
                reactionEmoji = "😂",
                reactions = listOf(
                    reaction("❤️", "alex@icloud.com"),
                    mine("❤️"),
                    reaction("😂", "mark@icloud.com"),
                ),
            ),
        )

        assertEquals(listOf("❤️", "😂"), summary?.emojis)
        assertEquals("Reactions: ❤️ 2, 😂", summary?.label)
    }

    @Test
    fun `messages without reactions have no summary`() {
        assertNull(bubbleReactionSummary(message()))
    }

    private fun reaction(emoji: String, address: String) =
        MessageReactionUi(emoji = emoji, senderAddress = address, isFromMe = false)

    private fun mine(emoji: String) =
        MessageReactionUi(emoji = emoji, senderAddress = null, isFromMe = true)

    private fun message(
        reactionEmoji: String? = null,
        reactions: List<MessageReactionUi> = emptyList(),
    ) = MessageItem(
        id = 1L,
        text = "hello",
        isFromMe = false,
        date = 1L,
        status = MessageStatus.DELIVERED,
        isGroupEvent = false,
        reactionEmoji = reactionEmoji,
        reactions = reactions,
        guid = "msg-1",
    )
}
