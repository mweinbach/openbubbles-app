package app.openbubbles.nativeapp.ui.login

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private fun kotlinx.coroutines.test.TestScope.model(
        handle: LoginHandle,
    ): LoginViewModel = LoginViewModel(
        scope = this,
        handle = handle,
        workerDispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun `trusted-device 2FA completes registration`() = runTest {
        val model = model(FakeLoginHandle())
        advanceUntilIdle()

        model.submitCredentials("person@icloud.com", "password")
        advanceUntilIdle()
        assertIs<LoginScreen.DeviceCode>(model.screen.value)

        model.submitCode("123456")
        advanceUntilIdle()
        val done = assertIs<LoginScreen.Done>(model.screen.value)
        assertEquals("person@icloud.com", done.username)
    }

    @Test
    fun `Apple may complete login without requesting 2FA`() = runTest {
        val handle = FakeLoginHandle().apply { nextState = LoginUiState.LoggedIn }
        val model = model(handle)
        advanceUntilIdle()

        model.submitCredentials("person@icloud.com", "password")
        advanceUntilIdle()

        val done = assertIs<LoginScreen.Done>(model.screen.value)
        assertEquals("person@icloud.com", done.username)
    }

    @Test
    fun `rejected trusted-device code stays on code entry`() = runTest {
        val model = model(FakeLoginHandle())
        model.submitCredentials("person@icloud.com", "password")
        advanceUntilIdle()

        model.submitCode(FakeLoginHandle.REJECT_CODE)
        advanceUntilIdle()

        val screen = assertIs<LoginScreen.DeviceCode>(model.screen.value)
        assertNotNull(screen.error)
    }

    @Test
    fun `SMS fallback chooses phone and completes registration`() = runTest {
        val model = model(FakeLoginHandle())
        model.submitCredentials("person@icloud.com", "password")
        advanceUntilIdle()

        model.requestSms()
        advanceUntilIdle()
        val chooser = assertIs<LoginScreen.SmsPhoneChooser>(model.screen.value)
        assertEquals(2, chooser.options.size)

        model.pickPhone(chooser.options.first().id)
        advanceUntilIdle()
        assertIs<LoginScreen.SmsCode>(model.screen.value)

        model.submitSmsCode("654321")
        advanceUntilIdle()
        assertIs<LoginScreen.Done>(model.screen.value)
    }

    @Test
    fun `registration block is surfaced after successful 2FA`() = runTest {
        val model = model(FakeLoginHandle(blockRegistration = true))
        model.submitCredentials("person@icloud.com", "password")
        advanceUntilIdle()
        model.submitCode("123456")
        advanceUntilIdle()

        val blocked = assertIs<LoginScreen.Blocked>(model.screen.value)
        assertEquals("Registration paused", blocked.title)
    }

    @Test
    fun `failed login can retry with the same credentials`() = runTest {
        val handle = FakeLoginHandle(shouldFail = true)
        val model = model(handle)

        model.submitCredentials("person@icloud.com", "password")
        advanceUntilIdle()
        assertNotNull(assertIs<LoginScreen.Form>(model.screen.value).error)

        handle.shouldFail = false
        model.retry()
        advanceUntilIdle()
        assertIs<LoginScreen.DeviceCode>(model.screen.value)
    }
}
