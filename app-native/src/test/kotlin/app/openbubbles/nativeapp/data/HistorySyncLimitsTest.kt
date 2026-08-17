package app.openbubbles.nativeapp.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uniffi.rust_lib_bluebubbles.UCloudMessage

class HistorySyncLimitsTest {
    private val nowMillis = 1_800_000_000_000L

    @Test
    fun `all history accepts messages regardless of age`() {
        assertTrue(
            shouldIncludeHistoryMessage(
                message = message(time = 1L),
                window = HistorySyncWindow.ALL_HISTORY,
                nowMillis = nowMillis,
                alreadyLocal = false,
            ),
        )
    }

    @Test
    fun `window accepts recent messages and rejects older messages`() {
        val cutoff = requireNotNull(
            HistorySyncWindow.LAST_30_DAYS.cutoffAppleNanoseconds(nowMillis),
        )

        assertTrue(
            shouldIncludeHistoryMessage(
                message = message(time = cutoff),
                window = HistorySyncWindow.LAST_30_DAYS,
                nowMillis = nowMillis,
                alreadyLocal = false,
            ),
        )
        assertFalse(
            shouldIncludeHistoryMessage(
                message = message(time = cutoff - 1L),
                window = HistorySyncWindow.LAST_30_DAYS,
                nowMillis = nowMillis,
                alreadyLocal = false,
            ),
        )
    }

    @Test
    fun `already local and transcript background records bypass the cutoff`() {
        assertTrue(
            shouldIncludeHistoryMessage(
                message = message(time = 1L),
                window = HistorySyncWindow.LAST_30_DAYS,
                nowMillis = nowMillis,
                alreadyLocal = true,
            ),
        )
        assertTrue(
            shouldIncludeHistoryMessage(
                message = message(time = 1L, msgType = 138L),
                window = HistorySyncWindow.LAST_30_DAYS,
                nowMillis = nowMillis,
                alreadyLocal = false,
            ),
        )
    }

    private fun message(time: Long, msgType: Long = 0L) = UCloudMessage(
        guid = "message-$time-$msgType",
        chatId = "iMessage;-;+15551234567",
        sender = "tel:+15551234567",
        time = time,
        msgType = msgType,
        error = 0L,
        service = "iMessage",
        flagsBits = 0L,
        text = "history",
        subject = null,
        hasAttachments = false,
        attachmentGuids = emptyList(),
        balloonBundleId = null,
        linkJson = null,
        hasPayloadData = false,
        transcriptBackground = null,
        summaryInfoJson = null,
        effect = null,
        dateReadNs = null,
        dateDeliveredNs = null,
        associatedMessageType = null,
        associatedMessageGuid = null,
        threadOriginatorGuid = null,
        threadOriginatorPart = null,
        associatedMessageEmoji = null,
    )
}
