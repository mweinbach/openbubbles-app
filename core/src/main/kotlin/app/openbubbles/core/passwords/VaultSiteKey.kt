package app.openbubbles.core.passwords

import java.net.IDN
import java.net.URI
import java.util.Locale

/**
 * Canonical lookup key for a vault site.
 *
 * Apple stores the site as a bare host in `srvr` (passwords) and `labl`
 * (passkeys), while an Android request arrives as an RP ID, a full origin, or
 * a browser-reported web domain. Both sides are reduced to one ASCII host so a
 * cached row and a live request agree; the raw Apple string is kept alongside
 * it in [VaultItemRecord.site] because the backend still matches exactly.
 *
 * Returns `null` for anything that is not a usable host, which the callers
 * treat as "no credentials" rather than "match everything".
 */
fun vaultSiteKey(value: String?): String? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val host = runCatching {
        val uri = URI(if ("://" in trimmed) trimmed else "https://$trimmed")
        uri.host ?: uri.authority?.substringAfterLast('@')?.substringBefore(':')
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null

    // A trailing dot names the same host in DNS but not in string equality.
    val bare = host.trim().trim('[', ']').trimEnd('.').lowercase(Locale.ROOT)
    if (bare.isEmpty() || bare == "." || bare.any { it.isWhitespace() }) return null
    return runCatching { IDN.toASCII(bare).lowercase(Locale.ROOT) }.getOrNull()?.takeIf { it.isNotEmpty() }
}

/** True when a cached row's site is the exact same host as the requested one. */
fun vaultSiteMatches(recordSite: String?, requestedSite: String?): Boolean {
    val requested = vaultSiteKey(requestedSite) ?: return false
    return vaultSiteKey(recordSite) == requested
}
