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
}
