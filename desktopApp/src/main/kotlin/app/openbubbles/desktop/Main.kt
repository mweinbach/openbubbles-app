package app.openbubbles.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.openbubbles.db.Db
import app.openbubbles.shared.Hello
import java.io.File

private fun dbStatus(): String = try {
    val dir = java.nio.file.Files.createTempDirectory("ob-desktop").toFile()
    val store = Db.build(dir)
    val count = store.boxFor(app.openbubbles.db.Chat::class.java).count()
    store.close()
    "objectbox ok (chats in fresh store: $count)"
} catch (t: Throwable) {
    "objectbox failed: ${t.message}"
}

private fun rustStatus(): String = try {
    uniffi.rust_lib_bluebubbles.uniffiEnsureInitialized()
    "uniffi ok — isLocked=${uniffi.rust_lib_bluebubbles.isLocked()}"
} catch (t: Throwable) {
    "rust not loaded: ${t.message?.lineSequence()?.first()}"
}

fun main() {
    application {
        Window(title = "OpenBubbles Native", onCloseRequest = ::exitApplication) {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(Hello.greeting(), style = MaterialTheme.typography.headlineSmall)
                        Text(dbStatus(), style = MaterialTheme.typography.bodyMedium)
                        Text(rustStatus(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
