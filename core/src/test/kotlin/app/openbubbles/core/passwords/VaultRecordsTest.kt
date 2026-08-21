package app.openbubbles.core.passwords

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import uniffi.rust_lib_bluebubbles.UVaultItem
import uniffi.rust_lib_bluebubbles.UVaultItemKind

class VaultRecordsTest {

    private val decoder = VaultPasskeyUserDecoder { tag ->
        when (String(tag, Charsets.UTF_8)) {
            "ada" -> VaultPasskeyUser(name = "ada@example.com", displayName = "Ada Lovelace")
            "display-only" -> VaultPasskeyUser(name = null, displayName = "Ada")
            else -> null
        }
    }

    private fun item(
        kind: UVaultItemKind,
        title: String = "example.com",
        username: String? = null,
        credentialId: ByteArray? = null,
        userTag: ByteArray? = null,
        modifiedAtMs: ULong = 1_700_000_000_000UL,
    ) = UVaultItem(
        id = "record-1",
        kind = kind,
        title = title,
        username = username,
        groupId = null,
        modifiedAtMs = modifiedAtMs,
        credentialId = credentialId,
        userTag = userTag,
    )

    @Test
    fun aPasswordRowKeepsItsAppleSiteAndAccount() {
        val record = item(
            UVaultItemKind.PASSWORD,
            title = "Example.com",
            username = "ada@example.com",
        ).record(decoder)

        assertEquals(VaultItemKind.Password, record.kind)
        assertEquals("Example.com", record.site)
        assertEquals("Example.com", record.title)
        assertEquals("ada@example.com", record.username)
        assertNull(record.webauthnCredentialId)
        assertEquals(1_700_000_000_000, record.modifiedAtMs)
    }

    @Test
    fun aPasskeyRowGetsItsLabelFromTheUserTag() {
        val record = item(
            UVaultItemKind.PASSKEY,
            credentialId = byteArrayOf(1, 2, 3, 4),
            userTag = "ada".toByteArray(Charsets.UTF_8),
        ).record(decoder)

        assertEquals(VaultItemKind.Passkey, record.kind)
        assertEquals("ada@example.com", record.username)
        assertEquals("Ada Lovelace", record.displayName)
        assertEquals("AQIDBA", record.webauthnCredentialId)
    }

    @Test
    fun aPasskeyWithOnlyADisplayNameStillGetsALabel() {
        val record = item(
            UVaultItemKind.PASSKEY,
            userTag = "display-only".toByteArray(Charsets.UTF_8),
        ).record(decoder)

        assertEquals("Ada", record.username)
        assertEquals("Ada", record.displayName)
    }

    @Test
    fun anUnreadableUserTagLeavesTheRowUsable() {
        val record = item(
            UVaultItemKind.PASSKEY,
            credentialId = byteArrayOf(9),
            userTag = "garbage".toByteArray(Charsets.UTF_8),
        ).record(decoder)

        assertNull(record.username)
        assertNull(record.displayName)
        assertEquals("CQ", record.webauthnCredentialId)
    }

    @Test
    fun aThrowingDecoderCannotFailTheListing() {
        val exploding = VaultPasskeyUserDecoder { error("bad tag") }
        val record = item(
            UVaultItemKind.PASSKEY,
            userTag = byteArrayOf(0),
        ).record(exploding)

        assertNull(record.username)
        assertEquals(VaultItemKind.Passkey, record.kind)
    }

    @Test
    fun credentialIdEncodingIsBase64UrlWithoutPadding() {
        assertEquals("AQIDBA", vaultWebauthnCredentialId(byteArrayOf(1, 2, 3, 4)))
        // 0xFB 0xFF exercises the URL-safe alphabet ('-' and '_').
        assertEquals("-_8", vaultWebauthnCredentialId(byteArrayOf(-5, -1)))
        assertNull(vaultWebauthnCredentialId(null))
        assertNull(vaultWebauthnCredentialId(ByteArray(0)))
    }

    @Test
    fun kindsMapBothWays() {
        VaultItemKind.entries.forEach { kind ->
            assertEquals(kind, kind.uniffi().record())
        }
        assertEquals(VaultItemKind.Code, UVaultItemKind.CODE.record())
        assertEquals(VaultItemKind.Wifi, UVaultItemKind.WIFI.record())
    }

    @Test
    fun onlyPasskeysConsultTheUserTag() {
        val record = item(
            UVaultItemKind.WIFI,
            title = "Home Wi-Fi",
            userTag = "ada".toByteArray(Charsets.UTF_8),
        ).record(decoder)

        assertEquals(VaultItemKind.Wifi, record.kind)
        assertNull(record.username)
        assertNull(record.displayName)
    }
}
