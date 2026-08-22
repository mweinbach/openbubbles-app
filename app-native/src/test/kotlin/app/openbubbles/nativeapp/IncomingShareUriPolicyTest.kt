package app.openbubbles.nativeapp

import android.content.Intent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IncomingShareUriPolicyTest {
    private val applicationPackageName = "com.openbubbles.messaging"
    private val applicationUid = 12345
    private val externalProvider = IncomingShareContentProvider(
        packageName = "com.example.gallery",
        uid = 23456,
        exported = false,
        grantUriPermissions = true,
    )

    @Test
    fun `valid external file provider is accepted only with a delivered read grant`() {
        var resolvedAuthority: String? = null
        var checkedStream: String? = null
        val stream = "content://com.example.gallery.fileprovider/photos/1.jpg"

        assertTrue(
            isTrustedIncomingShareStream(
                stream = stream,
                intentFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION,
                applicationPackageName = applicationPackageName,
                applicationUid = applicationUid,
                resolveProvider = { authority ->
                    resolvedAuthority = authority
                    externalProvider
                },
                hasReadGrant = { grantedStream ->
                    checkedStream = grantedStream
                    true
                },
            ),
        )

        assertEquals("com.example.gallery.fileprovider", resolvedAuthority)
        assertEquals(stream, checkedStream)
    }

    @Test
    fun `intent read flag alone never substitutes for a real uri grant`() {
        assertFalse(isTrusted("content://com.example.gallery/photo/1", hasReadGrant = false))
    }

    @Test
    fun `existing provider access without a delivered intent grant is rejected`() {
        assertFalse(
            isTrusted(
                stream = "content://com.example.gallery/photo/1",
                intentFlags = 0,
                hasReadGrant = true,
            ),
        )
    }

    @Test
    fun `same app provider authority cannot be used as a confused deputy`() {
        assertFalse(
            isTrusted(
                stream = "content://$applicationPackageName.fileprovider/attachments/private.jpg",
                provider = externalProvider,
            ),
        )
    }

    @Test
    fun `same package and shared uid provider aliases are rejected`() {
        assertFalse(
            isTrusted(
                stream = "content://unexpected.alias/private",
                provider = externalProvider.copy(packageName = applicationPackageName),
            ),
        )
        assertFalse(
            isTrusted(
                stream = "content://unexpected.alias/private",
                provider = externalProvider.copy(uid = applicationUid),
            ),
        )
    }

    @Test
    fun `private external provider that cannot grant uri access is rejected`() {
        assertFalse(
            isTrusted(
                stream = "content://com.example.gallery/private",
                provider = externalProvider.copy(exported = false, grantUriPermissions = false),
            ),
        )
    }

    @Test
    fun `unresolved provider fails closed`() {
        assertFalse(isTrusted("content://missing.provider/photo/1", provider = null))
    }

    @Test
    fun `file malformed and disguised content authorities are rejected`() {
        listOf(
            "file:///data/user/0/$applicationPackageName/files/secret.db",
            "https://com.example.gallery/photo/1",
            "content:///photo/1",
            "content://com.example.gallery:443/photo/1",
            "content://attacker@com.example.gallery/photo/1",
            "content://com.example%2egallery/photo/1",
            "content://com.example.gallery\\@evil/photo/1",
            "content:com.example.gallery/photo/1",
            "content://com.example.gallery/photo/%ZZ",
        ).forEach { stream ->
            assertFalse(isTrusted(stream), "Unexpectedly trusted $stream")
        }
    }

    @Test
    fun `share parser drops file and malformed streams but preserves valid text and content`() {
        val request = parseIncomingShareRequest(
            action = Intent.ACTION_SEND_MULTIPLE,
            mimeType = "image/*",
            extraText = "Trip photos",
            streams = listOf(
                "file:///data/user/0/$applicationPackageName/files/secret.db",
                "content:///missing-authority",
                "content://com.example.gallery/photo/1",
            ),
        )

        assertEquals("Trip photos", request?.text)
        assertEquals(listOf("content://com.example.gallery/photo/1"), request?.streams)
        assertNull(
            parseIncomingShareRequest(
                action = Intent.ACTION_SEND,
                mimeType = "image/*",
                extraText = null,
                streams = listOf("file:///private/secret.jpg"),
            ),
        )
    }

    private fun isTrusted(
        stream: String,
        intentFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION,
        provider: IncomingShareContentProvider? = externalProvider,
        hasReadGrant: Boolean = true,
    ): Boolean = isTrustedIncomingShareStream(
        stream = stream,
        intentFlags = intentFlags,
        applicationPackageName = applicationPackageName,
        applicationUid = applicationUid,
        resolveProvider = { provider },
        hasReadGrant = { hasReadGrant },
    )
}
