package app.openbubbles.nativeapp.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import app.openbubbles.nativeapp.ui.settings.NearbyICloudApprovalDialog
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "icloud-passwords-nearby", device = Devices.PHONE, showBackground = true)
@Preview(
    name = "icloud-passwords-nearby-dark",
    device = Devices.PHONE,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun ICloudPasswordsNearbyApprovalScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        NearbyICloudApprovalDialog(
            starting = false,
            completing = false,
            sessionActive = true,
            approvalCode = "123456",
            error = null,
            onApprovalCodeChange = {},
            onStart = {},
            onComplete = {},
            onDismiss = {},
        )
    }
}
