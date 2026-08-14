package com.bluebubbles.messaging.services.rustpush

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import android.security.keystore.WrappedKeyEntry
import android.util.Log
import androidx.annotation.RequiresApi
import uniffi.rust_lib_bluebubbles.EcCurve
import uniffi.rust_lib_bluebubbles.EncryptMode
import uniffi.rust_lib_bluebubbles.KeyType
import uniffi.rust_lib_bluebubbles.KeystoreAccessRules
import uniffi.rust_lib_bluebubbles.KeystoreDigest
import uniffi.rust_lib_bluebubbles.KeystorePadding
import uniffi.rust_lib_bluebubbles.NativeKeystore
import uniffi.rust_lib_bluebubbles.doLock
import uniffi.rust_lib_bluebubbles.finishUnlock
import uniffi.rust_lib_bluebubbles.isLocked
import uniffi.rust_lib_bluebubbles.recoverKeychain
import java.math.BigInteger
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.interfaces.ECKey
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

class AndroidNativeKeystore(val context: Context) : NativeKeystore {
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private val IMPORT_WRAP_KEY_ALIAS = "keystore:wrap-key"

    private val KeyType.algorithm: String
        get() = when (this) {
            is KeyType.Ec -> KeyProperties.KEY_ALGORITHM_EC
            is KeyType.Aes -> KeyProperties.KEY_ALGORITHM_AES
            is KeyType.Rsa -> KeyProperties.KEY_ALGORITHM_RSA
        }

    private val EcCurve.size: Int
        get() = when (this) {
            EcCurve.P256 -> 256
            EcCurve.P384 -> 384
        }

    private val EcCurve.algorithmSpec: ECGenParameterSpec
        get() = when (this) {
            EcCurve.P256 -> ECGenParameterSpec("secp256r1")
            EcCurve.P384 -> ECGenParameterSpec("secp384r1")
        }

    private val KeyType.size: Int
        get() = when (this) {
            is KeyType.Ec -> v1.size
            is KeyType.Aes -> v1.toInt()
            is KeyType.Rsa -> v1.toInt()
        }

    private val KeystoreDigest.digest: String
        get() = when (this) {
            KeystoreDigest.SHA1 -> KeyProperties.DIGEST_SHA1
            KeystoreDigest.SHA256 -> KeyProperties.DIGEST_SHA256
            KeystoreDigest.SHA384 -> KeyProperties.DIGEST_SHA384
        }

    private val EncryptMode.blockMode: String
        get() = when (this) {
            is EncryptMode.Gcm -> KeyProperties.BLOCK_MODE_GCM
            is EncryptMode.Rsa -> KeyProperties.BLOCK_MODE_ECB
        }

    private val KeystorePadding.encryptionPadding: String
        get() = when (this) {
            is KeystorePadding.None -> KeyProperties.ENCRYPTION_PADDING_NONE
            is KeystorePadding.Oaep -> KeyProperties.ENCRYPTION_PADDING_RSA_OAEP
            is KeystorePadding.Pkcs1 -> KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1
        }

    private val KeystorePadding.signaturePadding: String
        get() = when (this) {
            is KeystorePadding.None -> throw Exception("Bad keystore padding none!")
            is KeystorePadding.Oaep -> throw Exception("Bad keystore padding oaep!")
            is KeystorePadding.Pkcs1 -> KeyProperties.SIGNATURE_PADDING_RSA_PKCS1
        }

    @SuppressLint("WrongConstant", "InlinedApi")
    private fun KeystoreAccessRules.getSpec(alias: String, type: KeyType): KeyGenParameterSpec {
        return KeyGenParameterSpec.Builder(
            alias,
            (if (canSign) KeyProperties.PURPOSE_SIGN else 0) or
                    (if (canEncrypt) KeyProperties.PURPOSE_ENCRYPT else 0) or
                    (if (canDecrypt) KeyProperties.PURPOSE_DECRYPT else 0) or
                    (if (canAgree) KeyProperties.PURPOSE_AGREE_KEY else 0)
        ).run {
            if (digests.isNotEmpty()) {
                setDigests(*digests.map { it.digest }.toTypedArray())
            }
            if (blockModes.isNotEmpty()) {
                setBlockModes(*blockModes.map { it.blockMode }.toTypedArray())
            }
            if (encryptionPaddings.isEmpty()) {
                setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            } else {
                setEncryptionPaddings(*encryptionPaddings.map { it.encryptionPadding }.toTypedArray())
            }
            if (signaturePadding.isNotEmpty()) {
                setSignaturePaddings(*signaturePadding.map { it.signaturePadding }.toTypedArray())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && mgf1Digests.isNotEmpty()) {
                setMgf1Digests(*mgf1Digests.map { it.digest }.toTypedArray())
            }
            setKeySize(type.size)
            if (type is KeyType.Ec) {
                setAlgorithmParameterSpec(type.v1.algorithmSpec)
            }

            if (requireUser) {
                val manager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (manager.isDeviceSecure) {
                    setUserAuthenticationRequired(true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(
                            0,
                            KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                        )
                    } else {
                        setUserAuthenticationValidityDurationSeconds(0)
                    }
                }
            }
            build()
        }
    }

    override fun createKey(alias: String, type: KeyType, accessRules: KeystoreAccessRules) {
        if (keyStore.containsAlias(alias)) {
            throw Exception("Key with alias '$alias' already exists.")
        }
        try {
            if (type is KeyType.Aes) {
                val generator = KeyGenerator.getInstance(
                    type.algorithm,
                    "AndroidKeyStore"
                )

                val spec = accessRules.getSpec(alias, type)
                generator.init(spec)
                generator.generateKey()
            } else {
                val generator = KeyPairGenerator.getInstance(
                    type.algorithm,
                    "AndroidKeyStore"
                )

                val spec = accessRules.getSpec(alias, type)
                generator.initialize(spec)
                generator.generateKeyPair()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    override fun destroyKey(alias: String) {
        keyStore.deleteEntry(alias)
    }

    override fun listKeys(): List<String> {
        return keyStore.aliases().toList()
    }

    @SuppressLint("WrongConstant")
    override fun importKey(
        alias: String,
        type: KeyType,
        wrappedKey: ByteArray,
        accessRules: KeystoreAccessRules
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            throw Exception("Android P+ required")
        }
        if (keyStore.containsAlias(alias)) {
            throw Exception("Key with alias '$alias' already exists.")
        }


        val spec = KeyGenParameterSpec.Builder(IMPORT_WRAP_KEY_ALIAS,
                KeyProperties.PURPOSE_WRAP_KEY)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build();
        val wrappedKeyEntry = WrappedKeyEntry(wrappedKey, IMPORT_WRAP_KEY_ALIAS,
                  "RSA/ECB/OAEPPadding", spec);

        try {
            keyStore.setEntry(alias, wrappedKeyEntry, null)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    @SuppressLint("WrongConstant")
    override fun getImportWrapKey(): ByteArray {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            throw Exception("Android P+ required")
        }
        if (!keyStore.containsAlias(IMPORT_WRAP_KEY_ALIAS)) {
            val generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                "AndroidKeyStore"
            )

            val spec = KeyGenParameterSpec.Builder(
                IMPORT_WRAP_KEY_ALIAS,
                KeyProperties.PURPOSE_WRAP_KEY
            ).run {
                setDigests(KeyProperties.DIGEST_SHA256)
                setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                setKeySize(2048)
                build()
            }
            generator.initialize(spec)
            generator.generateKeyPair()
        }
        return getPublicKey(IMPORT_WRAP_KEY_ALIAS)
    }

    override fun getKeyType(alias: String): KeyType? {
        val entry = keyStore.getEntry(alias, null) ?: return null
        val key = when (entry) {
            is KeyStore.PrivateKeyEntry -> entry.privateKey
            is KeyStore.SecretKeyEntry -> entry.secretKey
            else -> return null
        }

        return try {
            when (key.algorithm) {
                KeyProperties.KEY_ALGORITHM_EC -> {
                    val ecKey = key as? ECKey ?: return null
                    val fieldSize = ecKey.params.curve.field.fieldSize
                    when (fieldSize) {
                        256 -> KeyType.Ec(EcCurve.P256)
                        384 -> KeyType.Ec(EcCurve.P384)
                        else -> null
                    }
                }
                KeyProperties.KEY_ALGORITHM_RSA -> {
                    val factory = KeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
                    val keyInfo = factory.getKeySpec(key, KeyInfo::class.java)
                    KeyType.Rsa(keyInfo.keySize.toUShort())
                }
                KeyProperties.KEY_ALGORITHM_AES -> {
                    val factory = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
                    val keyInfo = factory.getKeySpec(key as SecretKey, KeyInfo::class.java) as KeyInfo
                    KeyType.Aes(keyInfo.keySize.toUShort())
                }
                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun sign(
        alias: String,
        digest: KeystoreDigest,
        padding: KeystorePadding,
        data: ByteArray
    ): ByteArray {
        try {
            val entry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
            val sigKeyAlgorithm = if (entry.privateKey.algorithm == KeyProperties.KEY_ALGORITHM_EC) {
                "ECDSA"
            } else {
                entry.privateKey.algorithm
            }
            val sigAlgorithm = "${digest.digest.replace("-", "")}with$sigKeyAlgorithm"
            val signature = Signature.getInstance(sigAlgorithm)
            signature.initSign(entry.privateKey)
            signature.update(data)
            return signature.sign()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    override fun verify(
        alias: String,
        digest: KeystoreDigest,
        padding: KeystorePadding,
        data: ByteArray,
        sig: ByteArray
    ): Boolean {
        try {
            val entry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
            val sigKeyAlgorithm = if (entry.privateKey.algorithm == KeyProperties.KEY_ALGORITHM_EC) {
                "ECDSA"
            } else {
                entry.privateKey.algorithm
            }
            val sigAlgorithm = "${digest.digest.replace("-", "")}with$sigKeyAlgorithm"
            val signature = Signature.getInstance(sigAlgorithm)
            signature.initVerify(entry.certificate.publicKey)
            signature.update(data)
            return signature.verify(sig)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    override fun getPublicKey(alias: String): ByteArray {
        return keyStore.getCertificate(alias).publicKey.encoded
    }

    override fun supportsImport(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }

    var savedCipher: Cipher? = null

    fun lockKeystore() {
        doLock()
    }

    // encrypt the keystore with a new master key (used to secure after adding a lockscreen)
    @RequiresApi(Build.VERSION_CODES.P)
    fun encryptKeystore() {
        unlockKeystore("Secure iCloud Keychain") { unlocked ->
            if (!unlocked) return@unlockKeystore

            recoverKeychain()
            lockKeystore()
        }
    }

    // make sure our master key is accessible
    fun checkMaster() {
        val entry = keyStore.getEntry("keystore:recovery:master", null)

        val key = (entry as? KeyStore.PrivateKeyEntry)?.privateKey

        if (key == null) {
            recoverKeychain()
            return
        }

        val cipher =
            Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_RSA}/${KeyProperties.BLOCK_MODE_ECB}/${KeyProperties.ENCRYPTION_PADDING_RSA_OAEP}")
        val spec = OAEPParameterSpec(
            KeyProperties.DIGEST_SHA256,
            "MGF1",
            MGF1ParameterSpec(KeyProperties.DIGEST_SHA1),
            PSource.PSpecified.DEFAULT
        )

        try {
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
        } catch (e: KeyPermanentlyInvalidatedException) {
            recoverKeychain()
        } catch (e: InvalidKeyException) {
            recoverKeychain()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun unlockKeystore(title: String, callback: (success: Boolean) -> Unit) {
        if (!isLocked()) {
            callback(true)
            return
        }

        val entry = keyStore.getEntry("keystore:recovery:master", null)

        val key = (entry as? KeyStore.PrivateKeyEntry)?.privateKey
        if (key == null) {
            recoverKeychain()
            unlockKeystore(title, callback)
            return
        }

        val cipher =
            Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_RSA}/${KeyProperties.BLOCK_MODE_ECB}/${KeyProperties.ENCRYPTION_PADDING_RSA_OAEP}")
        val spec = OAEPParameterSpec(
            KeyProperties.DIGEST_SHA256,
            "MGF1",
            MGF1ParameterSpec(KeyProperties.DIGEST_SHA1),
            PSource.PSpecified.DEFAULT
        )

        try {
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
        } catch (e: KeyPermanentlyInvalidatedException) {
            recoverKeychain()
            unlockKeystore(title, callback)
            return
        }

        val factory = KeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
        val keyInfo = factory.getKeySpec(key, KeyInfo::class.java)

        if (!keyInfo.isUserAuthenticationRequired) {
            savedCipher = cipher
            finishUnlock()
            callback(true)
            return
        }


        var builder = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setDescription("iCloud Keychain is used for Find My and Messages in iCloud. OpenBubbles requires user authentication when managing iCloud Keychain for extra security.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder = builder.setDeviceCredentialAllowed(true)
        }

        val prompt = builder.build()

        prompt.authenticate(BiometricPrompt.CryptoObject(cipher), CancellationSignal(), context.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                val result = result!!.cryptoObject.cipher
                savedCipher = result
                finishUnlock()
                callback(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                Log.e("BiometricFailed", "Authentication failed $errorCode $errString")
                callback(false)
            }

            override fun onAuthenticationFailed() {
                callback(false)
            }
        })
    }

    override fun derive(alias: String, peer: ByteArray): ByteArray {
        val entry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
        val privateKey = entry.privateKey

        val keyFactory = KeyFactory.getInstance(privateKey.algorithm)
        val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peer))

        val keyAgreement = KeyAgreement.getInstance("ECDH", "AndroidKeyStore")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        return keyAgreement.generateSecret()
    }

    override fun encrypt(alias: String, plaintext: ByteArray, mode: EncryptMode): ByteArray {
        val entry = keyStore.getEntry(alias, null)

        try {
            return when (mode) {
                is EncryptMode.Gcm -> {
                    val key = (entry as? KeyStore.SecretKeyEntry)?.secretKey
                        ?: throw IllegalArgumentException("AES key not found for alias '$alias'.")
                    val cipher =
                        Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}")
                    cipher.init(Cipher.ENCRYPT_MODE, key)
                    // Prepend the IV to the ciphertext, as is standard for GCM.
                    val iv = cipher.iv ?: throw Exception("IV cannot be null for GCM encryption")
                    iv + cipher.doFinal(plaintext)
                }

                is EncryptMode.Rsa -> {
                    val key = (entry as? KeyStore.PrivateKeyEntry)?.certificate?.publicKey
                        ?: throw IllegalArgumentException("RSA public key not found for alias '$alias'.")

                    when (val padding = mode.v1) {
                        is KeystorePadding.Pkcs1 -> {
                            val cipher =
                                Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_RSA}/${KeyProperties.BLOCK_MODE_ECB}/${KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1}")
                            cipher.init(Cipher.ENCRYPT_MODE, key)
                            return cipher.doFinal(plaintext)
                        }

                        is KeystorePadding.Oaep -> {
                            val cipher =
                                Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_RSA}/${KeyProperties.BLOCK_MODE_ECB}/${KeyProperties.ENCRYPTION_PADDING_RSA_OAEP}")
                            val spec = OAEPParameterSpec(
                                padding.md.digest,
                                "MGF1",
                                MGF1ParameterSpec(padding.mgf1.digest),
                                PSource.PSpecified.DEFAULT
                            )
                            cipher.init(Cipher.ENCRYPT_MODE, key, spec)
                            return cipher.doFinal(plaintext)
                        }

                        else -> throw IllegalArgumentException("Unsupported padding for RSA encryption: $padding")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    override fun decrypt(alias: String, ciphertext: ByteArray, mode: EncryptMode): ByteArray {
        val entry = keyStore.getEntry(alias, null)

        try {
            if (alias == "keystore:recovery:master") {
                savedCipher?.let { cipher ->
                    savedCipher = null
                    return cipher.doFinal(ciphertext)
                }
                throw Exception("Did not initialize keystore cipher recovery")
            }

            return when (mode) {
                is EncryptMode.Gcm -> {
                    val key = (entry as? KeyStore.SecretKeyEntry)?.secretKey
                        ?: throw IllegalArgumentException("AES key not found for alias '$alias'.")
                    if (ciphertext.size < 12) {
                        throw IllegalArgumentException("Invalid GCM ciphertext: missing IV.")
                    }
                    val cipher =
                        Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}")
                    // The IV is expected to be the first 12 bytes of the ciphertext.
                    val spec = GCMParameterSpec(128, ciphertext, 0, 12)
                    cipher.init(Cipher.DECRYPT_MODE, key, spec)
                    cipher.doFinal(ciphertext, 12, ciphertext.size - 12)
                }

                is EncryptMode.Rsa -> {
                    val key = (entry as? KeyStore.PrivateKeyEntry)?.privateKey
                        ?: throw IllegalArgumentException("RSA private key not found for alias '$alias'.")

                    when (val padding = mode.v1) {
                        is KeystorePadding.Pkcs1 -> {
                            val cipher =
                                Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_RSA}/${KeyProperties.BLOCK_MODE_ECB}/${KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1}")
                            cipher.init(Cipher.DECRYPT_MODE, key)
                            return cipher.doFinal(ciphertext)
                        }

                        is KeystorePadding.Oaep -> {
                            val cipher =
                                Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_RSA}/${KeyProperties.BLOCK_MODE_ECB}/${KeyProperties.ENCRYPTION_PADDING_RSA_OAEP}")
                            val spec = OAEPParameterSpec(
                                padding.md.digest,
                                "MGF1",
                                MGF1ParameterSpec(padding.mgf1.digest),
                                PSource.PSpecified.DEFAULT
                            )
                            cipher.init(Cipher.DECRYPT_MODE, key, spec)
                            return cipher.doFinal(ciphertext)
                        }

                        else -> throw IllegalArgumentException("Unsupported padding for RSA decryption: $padding")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
