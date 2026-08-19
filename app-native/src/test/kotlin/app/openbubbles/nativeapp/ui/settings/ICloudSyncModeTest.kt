package app.openbubbles.nativeapp.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class ICloudSyncModeTest {

    @Test
    fun `a running sync always offers stop, even mid-error or disconnected`() {
        assertEquals(
            ICloudSyncMode.Syncing,
            icloudSyncMode(
                connected = true,
                managerAvailable = true,
                inClique = true,
                cliqueError = null,
                syncing = true,
            ),
        )
        assertEquals(
            ICloudSyncMode.Syncing,
            icloudSyncMode(
                connected = true,
                managerAvailable = true,
                inClique = true,
                cliqueError = "keychain check failed",
                syncing = true,
            ),
        )
        assertEquals(
            ICloudSyncMode.Syncing,
            icloudSyncMode(
                connected = false,
                managerAvailable = true,
                inClique = null,
                cliqueError = null,
                syncing = true,
            ),
        )
    }

    @Test
    fun `disconnected offers no keychain judgment or action`() {
        assertEquals(
            ICloudSyncMode.NotConnected,
            icloudSyncMode(
                connected = false,
                managerAvailable = false,
                inClique = null,
                cliqueError = null,
                syncing = false,
            ),
        )
    }

    @Test
    fun `broken keychain client asks for repair`() {
        assertEquals(
            ICloudSyncMode.KeychainUnavailable,
            icloudSyncMode(
                connected = true,
                managerAvailable = false,
                inClique = null,
                cliqueError = null,
                syncing = false,
            ),
        )
        assertEquals(
            ICloudSyncMode.KeychainUnavailable,
            icloudSyncMode(
                connected = true,
                managerAvailable = true,
                inClique = false,
                cliqueError = "no iCloud Keychain on this state",
                syncing = false,
            ),
        )
    }

    @Test
    fun `membership decides between join and manual sync`() {
        assertEquals(
            ICloudSyncMode.NotJoined,
            icloudSyncMode(
                connected = true,
                managerAvailable = true,
                inClique = false,
                cliqueError = null,
                syncing = false,
            ),
        )
        assertEquals(
            ICloudSyncMode.Ready,
            icloudSyncMode(
                connected = true,
                managerAvailable = true,
                inClique = true,
                cliqueError = null,
                syncing = false,
            ),
        )
    }

    @Test
    fun `unresolved membership stays checking with no action`() {
        assertEquals(
            ICloudSyncMode.Checking,
            icloudSyncMode(
                connected = true,
                managerAvailable = true,
                inClique = null,
                cliqueError = null,
                syncing = false,
            ),
        )
    }
}
