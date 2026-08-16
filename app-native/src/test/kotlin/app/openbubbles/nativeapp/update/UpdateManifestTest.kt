package app.openbubbles.nativeapp.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class UpdateManifestTest {

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes the published feed schema`() {
        val text = """
            {
              "versionCode": 20002237,
              "versionName": "2.0.1",
              "apkAsset": "openbubbles-2.0.1.apk",
              "sha256": "ABCDEF0123",
              "bytes": 123456789,
              "notes": "Fixes things",
              "minVersionCode": 20002200,
              "futureField": true
            }
        """.trimIndent()
        val manifest = json.decodeFromString(UpdateManifest.serializer(), text)
        assertEquals(20002237L, manifest.versionCode)
        assertEquals("2.0.1", manifest.versionName)
        assertEquals("openbubbles-2.0.1.apk", manifest.apkAsset)
        assertEquals(123456789L, manifest.bytes)
        assertEquals("Fixes things", manifest.notes)
        assertEquals(20002200L, manifest.minVersionCode)
        // Unknown fields must not break forward compatibility.
        assertEquals("abcdef0123", manifest.normalizedSha256())
    }

    @Test
    fun `optional fields default`() {
        val text = """
            {
              "versionCode": 5,
              "versionName": "5",
              "apkAsset": "a.apk",
              "sha256": "aa"
            }
        """.trimIndent()
        val manifest = json.decodeFromString(UpdateManifest.serializer(), text)
        assertEquals(0L, manifest.bytes)
        assertEquals("", manifest.notes)
        assertEquals(0L, manifest.minVersionCode)
        assertNull(manifest.notes.takeIf { it.isNotBlank() })
    }

    @Test
    fun `missing required field fails decoding`() {
        val text = """{ "versionCode": 5, "versionName": "5", "sha256": "aa" }"""
        assertFailsWith<Exception> {
            json.decodeFromString(UpdateManifest.serializer(), text)
        }
    }
}
