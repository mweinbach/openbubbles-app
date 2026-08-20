package app.openbubbles.nativeapp.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SensitiveLoggingPolicyTest {

    @Test
    fun `credential and protocol sources do not restore raw logging sinks`() {
        val root = repositoryRoot()
        val sources = listOf(
            "app-native/src/main/kotlin/app/openbubbles/nativeapp/credentials/AutofillStructure.kt",
            "app-native/src/main/kotlin/app/openbubbles/nativeapp/credentials/CredentialGetActivity.kt",
            "app-native/src/main/kotlin/app/openbubbles/nativeapp/credentials/OBAutofillService.kt",
            "app-native/src/main/kotlin/app/openbubbles/nativeapp/data/CoreGraph.kt",
            "app-native/src/main/kotlin/app/openbubbles/nativeapp/data/ICloudContacts.kt",
            "app-native/src/main/kotlin/app/openbubbles/nativeapp/service/SimpleFilePackager.kt",
            "app-native/src/main/kotlin/app/openbubbles/nativeapp/service/NativePushService.kt",
            "app-native/src/main/kotlin/app/openbubbles/nativeapp/ui/settings/SettingsAccountSection.kt",
            "rust/src/api/api.rs",
            "rust/src/keystore.rs",
            "rust/src/lib.rs",
        ).associateWith { File(root, it).readText() }

        val forbidden = listOf(
            "Log.i(\"Client data\"",
            "Log.i(\"sign data\"",
            "Log.i(\"FILL\"",
            "printStackTrace()",
            "contact photo download failed: \$resolved",
            "gallery export failed for \$path",
            "Apple account sign-out teardown failed\", error",
            "Apple push service stop failed during sign-out\", error",
            "push service stop failed during iCloud repair\", error",
            "iCloud service repair finished: \$it",
            "shared profile fetch failed\", it",
            "failure.message ?: failure.javaClass.simpleName",
            "result.exceptionOrNull()?.message ?: \"Sign-out cleanup failed\"",
            "incoming pointer \$msg failed",
            "Apple push restore failed: \$reason",
            "registration.error)",
            "info!(\"Got user {user}\")",
            "info!(\"wrapped asn.1",
            "log::error!(\"RUST PANIC: {info}",
        )

        sources.forEach { (path, source) ->
            forbidden.forEach { fragment ->
                assertFalse(source.contains(fragment), "$path contains forbidden logging fragment: $fragment")
            }
        }

        val logger = sources.getValue("rust/src/lib.rs")
        assertTrue(logger.contains("let max_level = log::Level::Warn;"))
        assertTrue(logger.contains("let level_spec = \"warn\";"))
    }

    private fun repositoryRoot(): File {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        var cursor = File(workingDirectory).canonicalFile
        while (true) {
            if (File(cursor, "settings.gradle").isFile) return cursor
            cursor = cursor.parentFile ?: error("Repository root not found from $workingDirectory")
        }
    }
}
