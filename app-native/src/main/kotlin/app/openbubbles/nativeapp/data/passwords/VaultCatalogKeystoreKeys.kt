package app.openbubbles.nativeapp.data.passwords

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.openbubbles.core.passwords.VaultCatalogKeys
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * AndroidKeyStore keys that seal the durable vault catalog.
 *
 * Deliberately not bound to user authentication: the credential provider and
 * the legacy Autofill service are started by the system while the app is in the
 * background, and a key that needed a prompt would turn every request into an
 * empty picker. The keys are hardware-backed where the device supports it, so
 * lifting the database off the device does not reveal the cached site list.
 *
 * These are separate aliases from the Rust keychain/Octagon keys in
 * `AndroidNativeKeystore`; nothing here can decrypt account state.
 */
class VaultCatalogKeystoreKeys : VaultCatalogKeys {
    private val lock = Any()

    @Volatile private var data: SecretKey? = null

    @Volatile private var index: SecretKey? = null

    override fun dataKey(): SecretKey = data ?: synchronized(lock) {
        data ?: loadOrCreate(DATA_ALIAS).also { data = it }
    }

    override fun indexKey(): SecretKey = index ?: synchronized(lock) {
        index ?: loadOrCreate(INDEX_ALIAS).also { index = it }
    }

    /** Drops both keys so any surviving ciphertext becomes permanently unreadable. */
    fun destroy() = synchronized(lock) {
        data = null
        index = null
        val keyStore = keyStore()
        destroyVaultAliases(listOf(DATA_ALIAS, INDEX_ALIAS)) { alias ->
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        }
    }

    private fun loadOrCreate(alias: String): SecretKey {
        existing(alias)?.let { return it }
        val generator = KeyGenerator.getInstance(algorithm(alias), ANDROID_KEY_STORE)
        generator.init(spec(alias))
        return runCatching { generator.generateKey() }.getOrNull()
            // A concurrent generator in another component won the alias.
            ?: existing(alias)
            ?: error("Could not create the vault catalog key")
    }

    private fun existing(alias: String): SecretKey? =
        runCatching { keyStore().getKey(alias, null) as? SecretKey }.getOrNull()

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private fun algorithm(alias: String): String = when (alias) {
        INDEX_ALIAS -> KeyProperties.KEY_ALGORITHM_HMAC_SHA256
        else -> KeyProperties.KEY_ALGORITHM_AES
    }

    private fun spec(alias: String): KeyGenParameterSpec = when (alias) {
        INDEX_ALIAS -> KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()

        else -> KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val DATA_ALIAS = "openbubbles.vault.catalog.data.v1"
        const val INDEX_ALIAS = "openbubbles.vault.catalog.index.v1"
    }
}

internal fun destroyVaultAliases(
    aliases: List<String>,
    delete: (String) -> Unit,
) {
    var firstFailure: Throwable? = null
    aliases.forEach { alias ->
        try {
            delete(alias)
        } catch (failure: Throwable) {
            val first = firstFailure
            if (first == null) firstFailure = failure else first.addSuppressed(failure)
        }
    }
    firstFailure?.let { throw it }
}
