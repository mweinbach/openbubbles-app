package com.bluebubbles.messaging.services.rustpush

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidNativeKeystoreSecurityTest {
    @Test
    fun `user authenticated keys fail closed without a secure device credential`() {
        assertFalse(userAuthenticatedKeyCreationAllowed(requiresUserAuthentication = true, deviceSecure = false))
        assertTrue(userAuthenticatedKeyCreationAllowed(requiresUserAuthentication = true, deviceSecure = true))
        assertTrue(userAuthenticatedKeyCreationAllowed(requiresUserAuthentication = false, deviceSecure = false))
    }

    @Test
    fun `unsecured devices cannot use even previously unlocked recovery masters`() {
        assertEquals(
            RecoveryMasterAccessPolicy.DENY,
            recoveryMasterAccessPolicy(
                deviceSecure = false,
                keyRequiresAuthentication = false,
                keystoreLocked = false,
            ),
        )
        assertEquals(
            RecoveryMasterAccessPolicy.DENY,
            recoveryMasterAccessPolicy(
                deviceSecure = false,
                keyRequiresAuthentication = true,
                keystoreLocked = true,
            ),
        )
    }

    @Test
    fun `downgraded recovery masters rotate before either locked or unlocked reuse`() {
        assertEquals(
            RecoveryMasterAccessPolicy.ROTATE_UNAUTHENTICATED,
            recoveryMasterAccessPolicy(
                deviceSecure = true,
                keyRequiresAuthentication = false,
                keystoreLocked = true,
            ),
        )
        assertEquals(
            RecoveryMasterAccessPolicy.ROTATE_UNAUTHENTICATED,
            recoveryMasterAccessPolicy(
                deviceSecure = true,
                keyRequiresAuthentication = false,
                keystoreLocked = false,
            ),
        )
    }

    @Test
    fun `protected recovery masters prompt only while the Rust keystore is locked`() {
        assertEquals(
            RecoveryMasterAccessPolicy.REQUIRE_AUTHENTICATION,
            recoveryMasterAccessPolicy(
                deviceSecure = true,
                keyRequiresAuthentication = true,
                keystoreLocked = true,
            ),
        )
        assertEquals(
            RecoveryMasterAccessPolicy.ALREADY_AUTHENTICATED,
            recoveryMasterAccessPolicy(
                deviceSecure = true,
                keyRequiresAuthentication = true,
                keystoreLocked = false,
            ),
        )
    }
}
