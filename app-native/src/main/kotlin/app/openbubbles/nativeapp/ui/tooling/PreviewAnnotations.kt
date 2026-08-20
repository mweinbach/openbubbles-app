package app.openbubbles.nativeapp.ui.tooling

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview

@Preview(
    name = "Light",
    group = "Theme",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Preview(
    name = "Dark",
    group = "Theme",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
annotation class LightDarkPreviews

@Preview(name = "Phone", group = "Form factors", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable", group = "Form factors", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet", group = "Form factors", device = Devices.TABLET, showBackground = true)
@Preview(name = "Desktop", group = "Form factors", device = Devices.DESKTOP, showBackground = true)
annotation class FormFactorPreviews
