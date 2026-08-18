package app.openbubbles.nativeapp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import uniffi.rust_lib_bluebubbles.URegisterState

class AccountConnectionUiTest {

    @Test
    fun `registered live state has no recovery banner`() {
        assertNull(
            accountConnectionUiState(
                hasLiveState = true,
                registration = URegisterState.Registered(nextS = 3_600),
                lastError = null,
            ),
        )
    }

    @Test
    fun `two factor failure offers interactive verification`() {
        val state = accountConnectionUiState(
            hasLiveState = true,
            registration = URegisterState.Failed(
                retryWait = null,
                error = "Apple ID verification required. Complete two-factor authentication.",
            ),
            lastError = null,
        )

        assertNotNull(state)
        assertEquals("Verify your Apple ID", state.title)
        assertEquals(AccountConnectionAction.SignIn, state.action)
        assertTrue(state.supporting.contains("local messages are safe"))
        assertTrue(state.supporting.contains("delivery can stop"))
    }

    @Test
    fun `registering state stays visible even with restored push state`() {
        val state = accountConnectionUiState(
            hasLiveState = true,
            registration = URegisterState.Registering,
            lastError = null,
        )

        assertEquals("Reconnecting to iMessage", state?.title)
        assertTrue(state?.busy == true)
    }

    @Test
    fun `nonterminal registration failure offers retry`() {
        val state = accountConnectionUiState(
            hasLiveState = true,
            registration = URegisterState.Failed(retryWait = 300uL, error = "Temporary failure"),
            lastError = null,
        )

        assertEquals(AccountConnectionAction.Retry, state?.action)
        assertEquals(AccountConnectionTone.Error, state?.tone)
    }
}
