package app.openbubbles.nativeapp.update

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Streams a release APK into app-private storage, verifying it while it lands:
 * incremental SHA-256 and exact byte count against the feed manifest. A file
 * this class returned has passed both checks; anything else is deleted.
 *
 * Blocking OkHttp calls — run from `Dispatchers.IO`.
 */
class UpdateDownloader(
    private val client: OkHttpClient,
) {
    sealed class DownloadException(message: String, cause: Throwable? = null) :
        Exception(message, cause) {
        /** Bytes landed but the SHA-256 does not match the feed. */
        class HashMismatch : DownloadException("downloaded APK failed SHA-256 verification")
        /** Transfer ended early (size known and not matched). */
        class SizeMismatch(val got: Long, val want: Long) :
            DownloadException("download truncated: $got of $want bytes")
        class Http(val code: Int) : DownloadException("download HTTP $code")
        class Io(cause: Throwable) : DownloadException("download IO failure", cause)
    }

    /**
     * @param onProgress called on the calling thread roughly once per megabyte
     *        with the number of bytes written so far.
     * @return the verified APK file inside [destDir].
     */
    fun download(
        feed: UpdateFeed,
        destDir: File,
        onProgress: ((bytesWritten: Long) -> Unit)? = null,
    ): File {
        destDir.mkdirs()
        val manifest = feed.manifest
        val target = apkFileFor(destDir, manifest.versionCode)
        val part = File(destDir, target.name + PART_SUFFIX)

        val builder = Request.Builder()
            .url(feed.apkAssetUrl)
            .header("Accept", "application/octet-stream")

        try {
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) throw DownloadException.Http(response.code)
                val body = response.body ?: throw DownloadException.Io(
                    IllegalStateException("null body"),
                )
                val digest = MessageDigest.getInstance("SHA-256")
                var written = 0L
                var lastReported = 0L
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            written += read
                            if (onProgress != null && written - lastReported >= REPORT_EVERY_BYTES) {
                                lastReported = written
                                onProgress(written)
                            }
                        }
                    }
                }
                if (manifest.bytes > 0 && written != manifest.bytes) {
                    throw DownloadException.SizeMismatch(written, manifest.bytes)
                }
                val hashHex = digest.digest().joinToString("") { "%02x".format(it) }
                if (hashHex != manifest.normalizedSha256()) {
                    throw DownloadException.HashMismatch()
                }
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
                return target
            }
        } catch (e: DownloadException) {
            // Only the partial file is dirty; target (a previously verified
            // APK, if any) must survive a failed re-download attempt.
            part.delete()
            throw e
        } catch (e: IOException) {
            part.delete()
            throw DownloadException.Io(e)
        }
    }

    companion object {
        private const val PART_SUFFIX = ".part"
        private const val REPORT_EVERY_BYTES = 1024L * 1024L

        fun updatesDir(cacheDir: File): File = File(cacheDir, "updates")

        fun apkFileFor(destDir: File, versionCode: Long): File =
            File(destDir, "openbubbles-$versionCode.apk")

        /** Delete every downloaded APK except the one for [keepVersionCode]. */
        fun purgeStale(destDir: File, keepVersionCode: Long) {
            val keep = apkFileFor(destDir, keepVersionCode).name
            destDir.listFiles()?.forEach { f ->
                if (f.name != keep) f.delete()
            }
        }
    }
}
