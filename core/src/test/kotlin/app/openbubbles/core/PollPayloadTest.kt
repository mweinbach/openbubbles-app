package app.openbubbles.core

import app.openbubbles.core.model.InteractivePayload
import app.openbubbles.core.model.InteractivePayloadParser
import app.openbubbles.core.model.PollPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PollPayloadTest {
    @Test
    fun `vote json round-trips through the balloon parser`() {
        val json = PollPayload.voteItemJson("a", "mailto:me@example.com")
        val payload = InteractivePayloadParser.parse(
            bundleId = PollPayload.POLLS_BUNDLE_ID,
            payloadJson = PollPayload.appJson(
                bundleId = PollPayload.POLLS_BUNDLE_ID,
                appName = PollPayload.POLLS_APP_NAME,
                url = PollPayload.dataUrl(
                    """{"item":{"title":"Lunch?","orderedPollOptions":[{"optionIdentifier":"a","text":"Tacos"},{"optionIdentifier":"b","text":"Pizza"}],"votes":[{"voteOptionIdentifier":"a","participantHandle":"mailto:me@example.com"}]},"version":1}""",
                ),
            ),
        )
        val poll = assertIs<InteractivePayload.Poll>(payload)
        assertEquals("Lunch?", poll.question)
        assertEquals(listOf(1, 0), poll.options.map { it.voteCount })
        assertTrue(json.contains("voteOptionIdentifier"))
        assertTrue(json.contains("mailto:me@example.com"))
    }

    @Test
    fun `create json includes titled options`() {
        val json = PollPayload.createItemJson("Lunch?", listOf("Tacos", "Pizza"))
        val payload = InteractivePayloadParser.parse(
            bundleId = PollPayload.POLLS_BUNDLE_ID,
            payloadJson = PollPayload.appJson(
                bundleId = PollPayload.POLLS_BUNDLE_ID,
                appName = PollPayload.POLLS_APP_NAME,
                url = PollPayload.dataUrl(json),
                ldText = "Lunch?",
            ),
        )
        val poll = assertIs<InteractivePayload.Poll>(payload)
        assertEquals("Lunch?", poll.question)
        assertEquals(listOf("Tacos", "Pizza"), poll.options.map { it.text })
        assertEquals(listOf(0, 0), poll.options.map { it.voteCount })
    }
}
