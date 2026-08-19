package app.openbubbles.nativeapp

import android.content.Intent
import app.openbubbles.nativeapp.ui.Routes
import kotlin.test.Test
import kotlin.test.assertEquals

class MainLaunchActionTest {

    private fun decide(
        action: String? = null,
        dataString: String? = null,
        mimeType: String? = null,
        extraText: String? = null,
        streams: List<String> = emptyList(),
        chatGuid: String? = null,
        initialRoute: String? = null,
        standaloneTask: Boolean = false,
    ) = decideMainLaunchAction(
        action = action,
        dataString = dataString,
        mimeType = mimeType,
        extraText = extraText,
        streams = streams,
        chatGuid = chatGuid,
        initialRoute = initialRoute,
        standaloneTask = standaloneTask,
    )

    @Test
    fun `plain launcher tap decides nothing`() {
        assertEquals(MainLaunchAction.None, decide(action = Intent.ACTION_MAIN))
    }

    @Test
    fun `initial route extra opens that route`() {
        assertEquals(
            MainLaunchAction.OpenRoute(Routes.PASSWORDS, standaloneTask = false),
            decide(action = Intent.ACTION_MAIN, initialRoute = Routes.PASSWORDS),
        )
    }

    @Test
    fun `standalone launcher opens the route with back-exit semantics`() {
        assertEquals(
            MainLaunchAction.OpenRoute(Routes.PASSWORDS, standaloneTask = true),
            decide(action = Intent.ACTION_MAIN, initialRoute = Routes.PASSWORDS, standaloneTask = true),
        )
    }

    @Test
    fun `notification chat guid beats an initial route`() {
        assertEquals(
            MainLaunchAction.OpenChat("iMessage;-;chat123"),
            decide(chatGuid = "iMessage;-;chat123", initialRoute = Routes.PASSWORDS, standaloneTask = true),
        )
    }

    @Test
    fun `share payload beats compose and route`() {
        assertEquals(
            MainLaunchAction.Share(IncomingShareRequest(text = "shared text", mimeType = "text/plain")),
            decide(
                action = Intent.ACTION_SEND,
                mimeType = "text/plain",
                extraText = "shared text",
                initialRoute = Routes.PASSWORDS,
            ),
        )
    }

    @Test
    fun `sms uri decides compose`() {
        assertEquals(
            MainLaunchAction.Compose(SmsComposeRequest(recipients = listOf("+15551234567"), body = null)),
            decide(action = Intent.ACTION_SENDTO, dataString = "smsto:+15551234567"),
        )
    }

    @Test
    fun `blank guid and blank route fall through to nothing`() {
        assertEquals(MainLaunchAction.None, decide(chatGuid = " ", initialRoute = ""))
    }

    @Test
    fun `route requests compare equal across warm redelivery`() {
        val cold = decide(action = Intent.ACTION_MAIN, initialRoute = Routes.PASSWORDS, standaloneTask = true)
        val warm = decide(action = Intent.ACTION_MAIN, initialRoute = Routes.PASSWORDS, standaloneTask = true)
        assertEquals(cold, warm)
    }
}
