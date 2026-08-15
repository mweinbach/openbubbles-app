package app.openbubbles.nativeapp.data

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RustBootTest {

    @Test
    fun `legacy Rust logs are removed without touching unrelated files`() {
        val root = Files.createTempDirectory("openbubbles-rust-logs")
        try {
            val logs = root.resolve("logs").createDirectories()
            logs.resolve("rs_rCURRENT.log").createFile().writeText("sensitive")
            logs.resolve("rs_r00001.log").createFile().writeText("sensitive")
            val keep = logs.resolve("diagnostic.txt").createFile().also { it.writeText("keep") }

            assertEquals(2, deleteLegacyRustLogs(root.toFile()))
            assertTrue(legacyRustLogs(root.toFile()).isEmpty())
            assertTrue(Files.exists(keep))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `missing log directory is already clean`() {
        val root = Files.createTempDirectory("openbubbles-no-rust-logs")
        try {
            assertEquals(0, deleteLegacyRustLogs(root.toFile()))
            assertFalse(root.resolve("logs").toFile().exists())
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
