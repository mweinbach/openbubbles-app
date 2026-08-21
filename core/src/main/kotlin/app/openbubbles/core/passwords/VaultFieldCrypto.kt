package app.openbubbles.core.passwords

import java.security.GeneralSecurityException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keys for the durable vault catalog. Android supplies AndroidKeyStore-backed
 * keys whose material never enters the app process; the host tests supply
 * plain JCE keys so the envelope format itself is provable off-device.
 */
interface VaultCatalogKeys {
    /** AES key used for AES/GCM row payloads. */
    fun dataKey(): SecretKey

    /** HMAC key used for the deterministic site lookup index. */
    fun indexKey(): SecretKey
}

/** Thrown when a stored row cannot be read back, so the caller can rebuild the catalog. */
class VaultCatalogUnreadable(message: String, cause: Throwable? = null) :
    GeneralSecurityException(message, cause)

/**
 * Field-level protection for cached vault metadata.
 *
 * The catalog holds no secrets, but the set of sites and account names is
 * still an inventory of the user's logins, so it is sealed at rest and looked
 * up through a keyed blind index rather than a plaintext host column.
 */
interface VaultFieldCrypto {
    fun seal(plaintext: String): String
    fun open(sealed: String): String
    fun index(value: String): String
}

class AesGcmVaultFieldCrypto(private val keys: VaultCatalogKeys) : VaultFieldCrypto {

    override fun seal(plaintext: String): String {
        // The provider generates the nonce. AndroidKeyStore keys are created
        // with randomized encryption required, which rejects a caller-supplied
        // IV, and letting the provider pick it keeps GCM nonces unique.
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, keys.dataKey())
        }
        val iv = cipher.iv
        check(iv != null && iv.size == IV_BYTES) { "Unexpected AES-GCM nonce length" }
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return encoder.encodeToString(iv + ciphertext)
    }

    override fun open(sealed: String): String {
        val raw = try {
            decoder.decode(sealed)
        } catch (invalid: IllegalArgumentException) {
            throw VaultCatalogUnreadable("Vault catalog row is not valid base64", invalid)
        }
        if (raw.size <= IV_BYTES) {
            throw VaultCatalogUnreadable("Vault catalog row is truncated")
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    keys.dataKey(),
                    GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES),
                )
            }
            String(
                cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES),
                Charsets.UTF_8,
            )
        } catch (failure: GeneralSecurityException) {
            throw VaultCatalogUnreadable("Vault catalog row failed authentication", failure)
        }
    }

    override fun index(value: String): String {
        val mac = Mac.getInstance(MAC_ALGORITHM).apply { init(keys.indexKey()) }
        return mac.doFinal(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val MAC_ALGORITHM = "HmacSHA256"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        val encoder: Base64.Encoder = Base64.getEncoder().withoutPadding()
        val decoder: Base64.Decoder = Base64.getDecoder()
    }
}

/** Seals a nullable column without turning `null` into the string "null". */
fun VaultFieldCrypto.sealOrNull(plaintext: String?): String? = plaintext?.let(::seal)

fun VaultFieldCrypto.openOrNull(sealed: String?): String? = sealed?.let(::open)
