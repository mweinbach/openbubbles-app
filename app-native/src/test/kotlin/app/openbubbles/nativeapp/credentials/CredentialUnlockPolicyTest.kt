package app.openbubbles.nativeapp.credentials

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CredentialUnlockPolicyTest {
    @Test
    fun `lookup cancellation is never converted to an empty response`() {
        runBlocking {
            assertFailsWith<CancellationException> {
                runCatchingPreservingCancellation<Unit> { throw CancellationException("closed") }
            }
        }
    }
}
