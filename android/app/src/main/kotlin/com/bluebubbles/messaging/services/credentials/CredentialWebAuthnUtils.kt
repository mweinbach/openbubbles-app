package com.bluebubbles.messaging.services.credentials

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.upokecenter.cbor.CBORObject
import java.math.BigInteger
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey
import java.util.concurrent.Executors
import org.json.JSONObject

data class WebAuthnUser(
    val id: ByteArray?,
    val name: String?,
    val displayName: String?
)

fun encodeUserTag(userJson: JSONObject): ByteArray {
    val map = CBORObject.NewMap()
    if (userJson.has("id")) {
        map.Add("id", base64UrlDecode(userJson.getString("id")))
    }
    if (userJson.has("name")) {
        map.Add("name", userJson.getString("name"))
    }
    if (userJson.has("displayName")) {
        map.Add("displayName", userJson.getString("displayName"))
    }
    return map.EncodeToBytes()
}

fun decodeUserTag(tag: ByteArray): WebAuthnUser {
    val cbor = CBORObject.DecodeFromBytes(tag)
    val id = cbor["id"]?.GetByteString()
    val name = cbor["name"]?.AsString()
    val displayName = cbor["displayName"]?.AsString()
    return WebAuthnUser(id, name, displayName)
}

fun encodeCosePublicKey(publicKey: ECPublicKey): ByteArray {
    val x = bigIntToFixedLength(publicKey.w.affineX, 32)
    val y = bigIntToFixedLength(publicKey.w.affineY, 32)
    return CBORObject.NewMap().apply {
        Add(1, 2)    // kty: EC2
        Add(3, -7)   // alg: ES256
        Add(-1, 1)   // crv: P-256
        Add(-2, x)
        Add(-3, y)
    }.EncodeToBytes()
}

fun sha256(data: ByteArray): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(data)
}

fun base64UrlEncode(bytes: ByteArray): String {
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}

fun base64UrlDecode(value: String): ByteArray {
    return Base64.decode(value, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}

private fun bigIntToFixedLength(value: BigInteger, size: Int): ByteArray {
    val raw = value.toByteArray()
    return when {
        raw.size == size -> raw
        raw.size == size + 1 && raw[0] == 0.toByte() -> raw.copyOfRange(1, raw.size)
        raw.size < size -> ByteArray(size - raw.size) + raw
        else -> raw.copyOfRange(raw.size - size, raw.size)
    }
}

object CredentialWebAuthnUtils {
    private const val PRIVILEGED_ALLOWLIST_URL =
        "https://www.gstatic.com/gpm-passkeys-privileged-apps/apps.json"
    private const val PRIVILEGED_ALLOWLIST_CACHE_FILE = "privileged_apps_allowlist.json"
    private const val CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun ensurePrivilegedAllowlistFresh(context: Context, callback: (Throwable?) -> Unit) {
        val appContext = context.applicationContext
        executor.execute {
            val error = try {
                val cacheFile = getAllowlistCacheFile(appContext)
                val cacheIsFresh = cacheFile.exists() &&
                    (System.currentTimeMillis() - cacheFile.lastModified()) <= CACHE_TTL_MS
                if (!cacheIsFresh) {
                    downloadPrivilegedAllowlist(cacheFile)
                }
                null
            } catch (t: Throwable) {
                t
            }

            mainHandler.post {
                callback(error)
            }
        }
    }

    fun readPrivilegedAllowlistFromDiskOrThrow(context: Context): String {
        val cacheFile = getAllowlistCacheFile(context.applicationContext)
        if (!cacheFile.exists()) {
            throw IllegalStateException("Privileged allowlist cache is missing: ${cacheFile.absolutePath}")
        }

        val content = cacheFile.readText(StandardCharsets.UTF_8).trim()
        if (content.isEmpty()) {
            throw IllegalStateException("Privileged allowlist cache is empty: ${cacheFile.absolutePath}")
        }

        return content
    }

    private fun getAllowlistCacheFile(context: Context): File {
        return File(context.filesDir, PRIVILEGED_ALLOWLIST_CACHE_FILE)
    }

    private fun downloadPrivilegedAllowlist(destinationFile: File) {
        val connection = URL(PRIVILEGED_ALLOWLIST_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("Failed to download privileged allowlist. HTTP $responseCode")
            }

            val bytes = connection.inputStream.buffered().use { it.readBytes() }
            if (bytes.isEmpty()) {
                throw IOException("Downloaded privileged allowlist was empty")
            }

            destinationFile.parentFile?.mkdirs()
            val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.tmp")
            tempFile.outputStream().buffered().use { output ->
                output.write(bytes)
            }

            if (!tempFile.renameTo(destinationFile)) {
                destinationFile.outputStream().buffered().use { output ->
                    output.write(bytes)
                }
                tempFile.delete()
            }
            destinationFile.setLastModified(System.currentTimeMillis())
        } finally {
            connection.disconnect()
        }
    }
}
