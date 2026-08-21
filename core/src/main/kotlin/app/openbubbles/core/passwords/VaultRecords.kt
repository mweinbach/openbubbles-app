package app.openbubbles.core.passwords

import java.util.Base64
import uniffi.rust_lib_bluebubbles.UVaultItem
import uniffi.rust_lib_bluebubbles.UVaultItemKind

/** Name and display name carried by a passkey's CBOR user tag. */
data class VaultPasskeyUser(val name: String?, val displayName: String?)

/**
 * Decodes a passkey user tag. The CBOR reader lives with the platform
 * credential code, so the record mapping stays host-testable.
 */
fun interface VaultPasskeyUserDecoder {
    fun decode(tag: ByteArray): VaultPasskeyUser?

    companion object {
        val None = VaultPasskeyUserDecoder { null }
    }
}

fun UVaultItemKind.record(): VaultItemKind = when (this) {
    UVaultItemKind.PASSWORD -> VaultItemKind.Password
    UVaultItemKind.PASSKEY -> VaultItemKind.Passkey
    UVaultItemKind.CODE -> VaultItemKind.Code
    UVaultItemKind.WIFI -> VaultItemKind.Wifi
}

fun VaultItemKind.uniffi(): UVaultItemKind = when (this) {
    VaultItemKind.Password -> UVaultItemKind.PASSWORD
    VaultItemKind.Passkey -> UVaultItemKind.PASSKEY
    VaultItemKind.Code -> UVaultItemKind.CODE
    VaultItemKind.Wifi -> UVaultItemKind.WIFI
}

/** base64url without padding, matching the encoding WebAuthn requests use. */
fun vaultWebauthnCredentialId(bytes: ByteArray?): String? =
    bytes?.takeIf { it.isNotEmpty() }?.let { urlEncoder.encodeToString(it) }

/**
 * Projects one Rust vault listing row onto the durable record. Apple gives
 * passkeys no account name of their own, so the user tag supplies the label
 * the credential picker and the Passwords list both show.
 */
fun UVaultItem.record(decoder: VaultPasskeyUserDecoder = VaultPasskeyUserDecoder.None): VaultItemRecord {
    val kind = kind.record()
    val user = userTag?.takeIf { kind == VaultItemKind.Passkey && it.isNotEmpty() }
        ?.let { runCatching { decoder.decode(it) }.getOrNull() }
    return VaultItemRecord(
        id = id,
        kind = kind,
        site = title,
        title = title,
        username = username ?: user?.name ?: user?.displayName,
        displayName = user?.displayName ?: user?.name,
        webauthnCredentialId = vaultWebauthnCredentialId(credentialId),
        groupId = groupId,
        modifiedAtMs = modifiedAtMs.toLong().takeIf { it > 0 },
    )
}

private val urlEncoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
