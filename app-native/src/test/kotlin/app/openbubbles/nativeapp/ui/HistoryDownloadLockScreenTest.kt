package app.openbubbles.nativeapp.ui

import app.openbubbles.core.sync.SyncPhase
import app.openbubbles.core.sync.SyncProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryDownloadLockScreenTest {

    @Test
    fun everyPhaseNamesWhatIsBeingDownloaded() {
        assertEquals("Getting ready…", historyDownloadStatusLine(null))
        assertEquals(
            "Getting ready…",
            historyDownloadStatusLine(SyncProgress(SyncPhase.IDLE)),
        )
        assertEquals(
            "Checking your iCloud account…",
            historyDownloadStatusLine(SyncProgress(SyncPhase.CHECKING)),
        )
        assertEquals(
            "Downloading conversations — 12 so far",
            historyDownloadStatusLine(SyncProgress(SyncPhase.CHATS, chatsDone = 12u)),
        )
        assertEquals(
            "Downloading messages — 3400 so far",
            historyDownloadStatusLine(SyncProgress(SyncPhase.MESSAGES, messagesDone = 3400u)),
        )
        assertEquals(
            "Downloading photos and files — 88 so far",
            historyDownloadStatusLine(SyncProgress(SyncPhase.ATTACHMENTS, attachmentsDone = 88u)),
        )
    }

    @Test
    fun terminalPhasesNeverLeaveTheLineBlank() {
        SyncPhase.entries.forEach { phase ->
            assertTrue(
                historyDownloadStatusLine(SyncProgress(phase)).isNotBlank(),
                "blank status line for $phase",
            )
        }
    }

    @Test
    fun zeroCountLegsDoNotReadAsStalled() {
        // A deletion / already-synced leg advances the cursor without adding
        // rows; showing "0 so far" reads as frozen, so the count is dropped.
        assertEquals(
            "Downloading messages…",
            historyDownloadStatusLine(SyncProgress(SyncPhase.MESSAGES, messagesDone = 0u)),
        )
        assertEquals(
            "Downloading conversations…",
            historyDownloadStatusLine(SyncProgress(SyncPhase.CHATS, chatsDone = 0u)),
        )
        assertEquals(
            "Downloading photos and files…",
            historyDownloadStatusLine(SyncProgress(SyncPhase.ATTACHMENTS, attachmentsDone = 0u)),
        )
    }

    @Test
    fun autoRetryBackoffGrowsAndCaps() {
        assertEquals(2_000L, historyRetryBackoffMs(0))
        assertEquals(4_000L, historyRetryBackoffMs(1))
        assertEquals(8_000L, historyRetryBackoffMs(2))
        assertEquals(16_000L, historyRetryBackoffMs(3))
        // Capped at 30s and clamped so a large attempt never overflows.
        assertEquals(30_000L, historyRetryBackoffMs(4))
        assertEquals(30_000L, historyRetryBackoffMs(99))
    }
}
