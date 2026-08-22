package app.openbubbles.nativeapp.credentials

import java.io.ByteArrayOutputStream
import java.security.PrivateKey
import java.security.Signature
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val androidAppOrigin = Regex("android:apk-key-hash:[A-Za-z0-9_-]{43}")
private const val SHA_256_DIGEST_BYTES = 32

/** Browsers prove an HTTPS origin; verified native apps use their signing-certificate origin. */
internal fun passkeyRequestMatchesSelection(
    site: String,
    rpId: String,
    origin: String,
    browserOrigin: Boolean,
): Boolean {
    val selectedHost = canonicalRpHost(site) ?: return false
    val requestedHost = canonicalRpHost(rpId) ?: return false
    if (selectedHost != requestedHost) return false
    return if (browserOrigin) {
        originMatchesRpId(origin, requestedHost)
    } else {
        androidAppOrigin.matches(origin)
    }
}

/** Jetpack accepts this override only for a privileged, origin-populated browser request. */
internal fun passkeyClientDataHashValid(hash: ByteArray?, browserOrigin: Boolean): Boolean =
    hash == null || (browserOrigin && hash.size == SHA_256_DIGEST_BYTES)

internal data class PasskeyAssertion(
    val clientDataJson: ByteArray,
    val clientDataHash: ByteArray,
    val authenticatorData: ByteArray,
    val signature: ByteArray,
)

/** Builds precisely the bytes returned to Credential Manager and signed by the Apple passkey. */
internal fun createPasskeyAssertion(
    rpId: String,
    challenge: String,
    origin: String,
    packageName: String,
    providedClientDataHash: ByteArray?,
    privateKey: PrivateKey,
): PasskeyAssertion {
    require(providedClientDataHash == null || providedClientDataHash.size == SHA_256_DIGEST_BYTES) {
        "A supplied WebAuthn client data hash must contain exactly 32 bytes"
    }
    val clientDataJson = if (providedClientDataHash != null) {
        "{}".toByteArray(Charsets.UTF_8)
    } else {
        buildJsonObject {
            put("type", "webauthn.get")
            put("challenge", challenge)
            put("origin", origin)
            if (androidAppOrigin.matches(origin)) {
                put("androidPackageName", packageName)
            }
        }.toString().toByteArray(Charsets.UTF_8)
    }
    val clientDataHash = providedClientDataHash ?: sha256(clientDataJson)
    val authenticatorData = ByteArrayOutputStream().apply {
        write(sha256(rpId.toByteArray(Charsets.UTF_8)))
        // User presence, user verification, backup eligibility, and backup state.
        write(byteArrayOf((0x01 or 0x04 or 0x08 or 0x10).toByte()))
        write(byteArrayOf(0, 0, 0, 0))
    }.toByteArray()
    val signature = Signature.getInstance("SHA256withECDSA").apply {
        initSign(privateKey)
        update(authenticatorData)
        update(clientDataHash)
    }.sign()
    return PasskeyAssertion(clientDataJson, clientDataHash, authenticatorData, signature)
}
