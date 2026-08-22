package app.openbubbles.nativeapp.credentials

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.verify.domain.DomainVerificationManager
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.ProviderCreateCredentialRequest
import androidx.credentials.provider.ProviderGetCredentialRequest
import java.security.MessageDigest
import org.json.JSONObject

/** Shared with legacy Autofill, which runs before Credential Manager's API 34 boundary. */
internal object CredentialIntentContract {
    const val EXTRA_SITE = "credential.site"
    const val EXTRA_CRED_ID = "credential.cred_id"
    const val EXTRA_TYPE = "credential.type"
    const val EXTRA_ORIGIN = "credential.origin"
    const val EXTRA_PACKAGE_NAME = "credential.package_name"
    const val EXTRA_REQUEST_JSON = "credential.request_json"
    const val EXTRA_CLIENT_DATA_HASH = "credential.client_data_hash"
    const val TYPE_PASSWORD = "password"
    const val TYPE_PASSKEY = "passkey"
}

// DomainVerificationUserState.DOMAIN_STATE_VERIFIED has the stable value 2. Keep the
// pure host matcher usable before API 31 without linking an API 31 framework class.
private const val VERIFIED_APP_LINK_DOMAIN_STATE = 2

/**
 * Package/domain trust shared by Credential Manager and legacy Autofill.
 *
 * A ViewStructure's webDomain and a native WebAuthn rpId are supplied by the
 * calling app, so neither proves ownership. Android App Links supplies an
 * already-verified, package-and-signature-bound Digital Asset Links verdict;
 * user-selected links are deliberately insufficient.
 */
internal object CredentialCallerTrust {
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun accepts(context: Context, caller: CallingAppInfo, site: String): Boolean {
        val host = canonicalRpHost(site) ?: return false
        if (!matchesInstalledSigners(context, caller)) return false

        return if (caller.isOriginPopulated()) {
            val origin = runCatching { CredentialService.appInfoToOrigin(context, caller) }
                .getOrNull() ?: return false
            credentialDomainAuthorized(host, origin, emptyMap())
        } else {
            credentialDomainAuthorized(host, null, domainStates(context, caller.packageName))
        }
    }

    fun acceptsAutofill(context: Context, packageName: String, domain: String): Boolean {
        val host = canonicalRpHost(domain) ?: return false
        if (installedSigners(context, packageName).isEmpty()) return false
        return isDefaultBrowser(context, packageName) ||
            isSignedPrivilegedBrowser(context, packageName, host) ||
            hasVerifiedAppDomain(context, packageName, host)
    }

    fun soleVerifiedAppDomain(context: Context, packageName: String): String? =
        verifiedAppDomains(context, packageName).singleOrNull()

    private fun hasVerifiedAppDomain(context: Context, packageName: String, host: String): Boolean =
        isVerifiedDomain(host, domainStates(context, packageName))

    private fun verifiedAppDomains(context: Context, packageName: String): Set<String> =
        domainStates(context, packageName)
            .filterValues { it == VERIFIED_APP_LINK_DOMAIN_STATE }
            .keys
            .mapNotNull(::canonicalRpHost)
            .toSet()

    private fun domainStates(context: Context, packageName: String): Map<String, Int> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyMap()
        return runCatching {
            val manager = context.getSystemService(DomainVerificationManager::class.java)
                ?: return emptyMap()
            val state = manager.getDomainVerificationUserState(packageName)
                ?: return emptyMap()
            if (state.packageName != packageName) return emptyMap()
            state.hostToStateMap
        }.getOrDefault(emptyMap())
    }

    private fun isDefaultBrowser(context: Context, packageName: String): Boolean = runCatching {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(DEFAULT_BROWSER_PROBE))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        context.packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == packageName
    }.getOrDefault(false)

    private fun isSignedPrivilegedBrowser(
        context: Context,
        packageName: String,
        host: String,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return runCatching {
            @Suppress("DEPRECATION")
            val packageInfo = context.packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            val signingInfo = packageInfo.signingInfo ?: return false
            val origin = "https://$host"
            val caller = CallingAppInfo(packageName, signingInfo, origin)
            val allowlist = CredentialWebAuthnUtils.readPrivilegedAllowlistFromDiskOrThrow(context)
            caller.getOrigin(allowlist) == origin
        }.getOrDefault(false)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun matchesInstalledSigners(context: Context, caller: CallingAppInfo): Boolean {
        val actual = installedSigners(context, caller.packageName)
        val claimed = caller.signingInfo.apkContentsSigners.orEmpty()
            .map(::signatureFingerprint)
            .toSet()
        return claimed.isNotEmpty() && actual == claimed
    }

    private fun installedSigners(context: Context, packageName: String): Set<String> = runCatching {
        val packageManager = context.packageManager
        val signatures: Array<out Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                .signatures.orEmpty()
        }
        signatures.map(::signatureFingerprint).toSet()
    }.getOrDefault(emptySet())

    private fun signatureFingerprint(signature: Signature): String =
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private const val DEFAULT_BROWSER_PROBE = "https://openbubbles.invalid/"
}

/** Only OS-verified App Links prove a package owns this exact relying-party host. */
internal fun isVerifiedDomain(host: String, states: Map<String, Int>): Boolean {
    val expected = canonicalRpHost(host) ?: return false
    return states.any { (candidate, state) ->
        state == VERIFIED_APP_LINK_DOMAIN_STATE &&
            canonicalRpHost(candidate) == expected
    }
}

internal fun credentialDomainAuthorized(
    site: String,
    browserOrigin: String?,
    nativeDomainStates: Map<String, Int>,
): Boolean = if (browserOrigin != null) {
    originMatchesRpId(browserOrigin, site)
} else {
    isVerifiedDomain(site, nativeDomainStates)
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal data class VerifiedCredentialSelection(
    val site: String,
    val credentialId: String,
    val type: String,
    val origin: String,
    val packageName: String,
    val requestJson: String?,
    val clientDataHash: ByteArray?,
    val allowedUserIds: Set<String>,
    val browserOrigin: Boolean,
)

/** Associates create requests with either a signed browser origin or a verified native app. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal fun verifiedCredentialCreationSite(
    context: Context,
    intent: Intent,
    request: ProviderCreateCredentialRequest,
): String? {
    val caller = request.callingAppInfo
    val expectedPackage = intent.getStringExtra(CredentialIntentContract.EXTRA_PACKAGE_NAME)
    if (expectedPackage != null && expectedPackage != caller.packageName) return null
    val origin = runCatching { CredentialService.appInfoToOrigin(context, caller) }.getOrNull()
        ?: return null
    val requestedHost = when (val credential = request.callingRequest) {
        is CreatePublicKeyCredentialRequest -> runCatching {
            JSONObject(credential.requestJson).optJSONObject("rp")?.optString("id")
        }.getOrNull()

        is CreatePasswordRequest -> credential.origin
        else -> return null
    }
    val site = requestedHost?.takeIf(String::isNotBlank)?.let(::canonicalRpHost)
        ?: if (caller.isOriginPopulated()) {
            canonicalRpHost(origin)
        } else {
            CredentialCallerTrust.soleVerifiedAppDomain(context, caller.packageName)
        }
        ?: return null
    return site.takeIf { CredentialCallerTrust.accepts(context, caller, it) }
}

/** Rebuilds the selected caller and challenge from the framework's final request. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal fun verifyCredentialSelection(
    context: Context,
    intent: Intent,
    request: ProviderGetCredentialRequest,
): VerifiedCredentialSelection? {
    val site = intent.getStringExtra(CredentialIntentContract.EXTRA_SITE)?.takeIf(String::isNotBlank)
        ?: return null
    val credentialId = intent.getStringExtra(CredentialIntentContract.EXTRA_CRED_ID)
        ?.takeIf(String::isNotBlank) ?: return null
    val type = intent.getStringExtra(CredentialIntentContract.EXTRA_TYPE) ?: return null
    val expectedPackage = intent.getStringExtra(CredentialIntentContract.EXTRA_PACKAGE_NAME) ?: return null
    val expectedOrigin = intent.getStringExtra(CredentialIntentContract.EXTRA_ORIGIN) ?: return null
    val caller = request.callingAppInfo
    val origin = runCatching { CredentialService.appInfoToOrigin(context, caller) }.getOrNull()
        ?: return null

    if (!selectionCallerMatches(expectedPackage, expectedOrigin, caller.packageName, origin)) {
        return null
    }
    if (!CredentialCallerTrust.accepts(context, caller, site)) return null

    return when (type) {
        CredentialIntentContract.TYPE_PASSWORD -> {
            val option = request.credentialOptions.filterIsInstance<GetPasswordOption>()
                .singleOrNull() ?: return null
            VerifiedCredentialSelection(
                site = site,
                credentialId = credentialId,
                type = type,
                origin = origin,
                packageName = caller.packageName,
                requestJson = null,
                clientDataHash = null,
                allowedUserIds = option.allowedUserIds,
                browserOrigin = caller.isOriginPopulated(),
            )
        }

        CredentialIntentContract.TYPE_PASSKEY -> {
            val option = request.credentialOptions.filterIsInstance<GetPublicKeyCredentialOption>()
                .singleOrNull() ?: return null
            val rpId = runCatching { JSONObject(option.requestJson).optString("rpId") }
                .getOrNull()?.takeIf(String::isNotBlank) ?: return null
            if (canonicalRpHost(rpId) != canonicalRpHost(site)) return null
            VerifiedCredentialSelection(
                site = site,
                credentialId = credentialId,
                type = type,
                origin = origin,
                packageName = caller.packageName,
                requestJson = option.requestJson,
                clientDataHash = option.clientDataHash,
                allowedUserIds = emptySet(),
                browserOrigin = caller.isOriginPopulated(),
            )
        }

        else -> null
    }
}

internal fun selectionCallerMatches(
    expectedPackage: String,
    expectedOrigin: String,
    actualPackage: String,
    actualOrigin: String,
): Boolean = expectedPackage.isNotBlank() && expectedOrigin.isNotBlank() &&
    expectedPackage == actualPackage && expectedOrigin == actualOrigin

internal fun autofillSelectionMatches(
    expectedPackage: String,
    expectedSite: String,
    actualPackage: String,
    actualSite: String?,
): Boolean {
    val expectedHost = canonicalRpHost(expectedSite) ?: return false
    val actualHost = actualSite?.let(::canonicalRpHost) ?: return false
    return expectedPackage.isNotBlank() && expectedPackage == actualPackage &&
        expectedHost == actualHost
}

/** Extras are ignored for PendingIntent identity, so make request ownership part of the action. */
internal fun credentialPendingIntentAction(
    packageName: String,
    origin: String,
    site: String,
    credentialId: String,
    type: String,
): String {
    val identity = listOf(packageName, origin, site, credentialId, type)
        .joinToString(separator = "\u0000")
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return "app.openbubbles.nativeapp.credentials.SELECT.$digest"
}
