package app.openbubbles.nativeapp.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import app.openbubbles.nativeapp.ui.passwords.PasswordsScreen
import app.openbubbles.nativeapp.ui.passwords.PasswordsUiState
import app.openbubbles.nativeapp.ui.passwords.VaultCategory
import app.openbubbles.nativeapp.ui.passwords.VaultGroupUi
import app.openbubbles.nativeapp.ui.passwords.VaultInviteUi
import app.openbubbles.nativeapp.ui.passwords.VaultItemDetailScreen
import app.openbubbles.nativeapp.ui.passwords.VaultItemDetailUiState
import app.openbubbles.nativeapp.ui.passwords.VaultItemUi
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import com.android.tools.screenshot.PreviewTest

/**
 * Vault fixtures: the sorted, sectioned list and one item's page with its secret
 * still hidden behind authentication.
 */
private const val FIXED_MODIFIED = 1_759_000_000_000L

private fun login(id: String, site: String, username: String) = VaultItemUi(
    id = id,
    category = VaultCategory.Passwords,
    title = site,
    username = username,
    modifiedAtMs = FIXED_MODIFIED,
)

private fun vaultState(query: String = ""): PasswordsUiState = PasswordsUiState(
    loading = false,
    inClique = true,
    query = query,
    items = listOf(
        login("1", "netflix.com", "person@example.com"),
        login("2", "Apple", "person@icloud.com"),
        login("3", "bank.example", "person"),
        login("4", "github.com", "octo@example.com"),
        login("5", "1password.example", "person@example.com"),
        VaultItemUi(id = "6", category = VaultCategory.Wifi, title = "Home network"),
    ),
    loadedCategories = VaultCategory.entries.toSet(),
    categoryCounts = mapOf(
        VaultCategory.Passwords to 5,
        VaultCategory.Passkeys to 2,
        VaultCategory.Codes to 1,
        VaultCategory.Wifi to 1,
        VaultCategory.Groups to 2,
    ),
    groups = listOf(VaultGroupUi(id = "g1", name = "Household", owner = true, memberCount = 3)),
    invites = listOf(VaultInviteUi(id = "i1", groupName = "Team", inviter = "lead@example.com")),
    groupsLoaded = true,
)

@PreviewTest
@Preview(name = "passwords", device = Devices.PHONE, showBackground = true)
@Preview(
    name = "passwords-dark",
    device = Devices.PHONE,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun PasswordsListScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        PasswordsScreen(
            uiState = vaultState(),
            onBack = {},
            onRefresh = {},
            onOpenICloudSettings = {},
            onCategory = {},
            onQuery = {},
            onSelect = {},
            onOpenGroup = {},
            onPrepareCreatePassword = {},
            onCreatePassword = { _, _, _, _ -> },
            onCreateGroup = {},
            onAcceptInvite = {},
            onDeclineInvite = {},
        )
    }
}

/** A search with no matches names what was searched for. */
@PreviewTest
@Preview(name = "passwords-no-matches", device = Devices.PHONE, showBackground = true)
@Composable
fun PasswordsNoMatchesScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        PasswordsScreen(
            uiState = vaultState(query = "airline"),
            onBack = {},
            onRefresh = {},
            onOpenICloudSettings = {},
            onCategory = {},
            onQuery = {},
            onSelect = {},
            onOpenGroup = {},
            onPrepareCreatePassword = {},
            onCreatePassword = { _, _, _, _ -> },
            onCreateGroup = {},
            onAcceptInvite = {},
            onDeclineInvite = {},
        )
    }
}

/** One item's page: the secret is still dots until authentication succeeds. */
@PreviewTest
@Preview(name = "password-detail", device = Devices.PHONE, showBackground = true)
@Composable
fun PasswordDetailScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        VaultItemDetailScreen(
            uiState = VaultItemDetailUiState(
                item = login("1", "github.com", "octo@example.com"),
                groupName = "Household",
            ),
            onBack = {},
            onRequestReveal = {},
            onRefreshCode = {},
            onCopy = {},
            onDelete = {},
            onAddTotp = {},
        )
    }
}

/** Authentication was cancelled: the page says so instead of doing nothing. */
@PreviewTest
@Preview(name = "password-detail-auth-failed", device = Devices.PHONE, showBackground = true)
@Composable
fun PasswordDetailAuthFailedScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        VaultItemDetailScreen(
            uiState = VaultItemDetailUiState(
                item = login("1", "github.com", "octo@example.com"),
                error = "Authentication was not completed",
            ),
            onBack = {},
            onRequestReveal = {},
            onRefreshCode = {},
            onCopy = {},
            onDelete = {},
            onAddTotp = {},
        )
    }
}
