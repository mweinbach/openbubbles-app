package app.openbubbles.nativeapp.credentials

import app.openbubbles.core.passwords.VaultPasskeyUser
import app.openbubbles.core.passwords.VaultPasskeyUserDecoder

/**
 * Reads the CBOR user tag Apple stores with a passkey so the credential picker
 * and the Passwords list can label it with the account rather than the site.
 * A malformed tag yields no label instead of failing the whole listing.
 */
fun vaultPasskeyUserDecoder(): VaultPasskeyUserDecoder = VaultPasskeyUserDecoder { tag ->
    runCatching {
        val user = decodeUserTag(tag)
        VaultPasskeyUser(name = user.name, displayName = user.displayName)
    }.getOrNull()
}
