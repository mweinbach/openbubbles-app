package app.openbubbles.nativeapp.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ICloudKeychainRecoverySecurityTest {
    @Test
    fun `account scope normalizes Apple IDs without persisting their plaintext`() {
        val canonical = recoveryCodeAccountScope("person@example.com")

        assertEquals(canonical, recoveryCodeAccountScope("  PERSON@Example.COM  "))
        assertEquals(64, canonical?.length)
        assertFalse(canonical.orEmpty().contains("person"))
        assertNull(recoveryCodeAccountScope(null))
        assertNull(recoveryCodeAccountScope("  "))
    }

    @Test
    fun `recovery ciphertext is never offered to another Apple account`() {
        val first = recoveryCodeAccountScope("first@example.com")
        val second = recoveryCodeAccountScope("second@example.com")

        assertNotEquals(first, second)
        assertTrue(recoveryCodeBelongsToAccount(first, first, "encrypted"))
        assertFalse(recoveryCodeBelongsToAccount(second, first, "encrypted"))
        assertFalse(recoveryCodeBelongsToAccount(null, first, "encrypted"))
        assertFalse(recoveryCodeBelongsToAccount(first, null, "encrypted"))
        assertFalse(recoveryCodeBelongsToAccount(first, first, null))
        assertFalse(recoveryCodeBelongsToAccount(first, first, ""))
    }

    @Test
    fun `recovery plaintext is cryptographically bound to its account scope`() {
        val first = checkNotNull(recoveryCodeAccountScope("first@example.com"))
        val second = checkNotNull(recoveryCodeAccountScope("second@example.com"))
        val payload = encodeAccountScopedRecoveryCode(first, "012345")

        assertEquals("012345", decodeAccountScopedRecoveryCode(first, payload))
        assertNull(decodeAccountScopedRecoveryCode(second, payload))
        assertNull(decodeAccountScopedRecoveryCode(first, "$first:not-a-code".encodeToByteArray()))
        assertNull(decodeAccountScopedRecoveryCode(first, "012345".encodeToByteArray()))
    }

    @Test
    fun `invalid recovery codes and unscoped records cannot be encrypted`() {
        val scope = checkNotNull(recoveryCodeAccountScope("person@example.com"))

        assertFailsWith<IllegalArgumentException> { encodeAccountScopedRecoveryCode(scope, "12345") }
        assertFailsWith<IllegalArgumentException> { encodeAccountScopedRecoveryCode(scope, "1234567") }
        assertFailsWith<IllegalArgumentException> { encodeAccountScopedRecoveryCode(scope, "12345x") }
        assertFailsWith<IllegalArgumentException> { encodeAccountScopedRecoveryCode("", "123456") }
    }
}
