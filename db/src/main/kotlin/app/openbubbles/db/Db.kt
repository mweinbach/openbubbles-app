package app.openbubbles.db

import io.objectbox.BoxStore
import java.io.File

/**
 * Store construction for the native clients.
 *
 * On Android the store lives in the app's files dir, matching the Flutter
 * app's `<appDocDir>/objectbox` location so an in-place update (same
 * applicationId at cutover) opens the existing data. Callers pass the
 * directory; platform code decides where that is.
 */
object Db {
    const val STORE_DIR_NAME = "objectbox"

    fun build(dir: File): BoxStore =
        MyObjectBox.builder()
            .directory(File(dir, STORE_DIR_NAME))
            .maxSizeInKByte(5 * 1024 * 1024) // 5 GB, same as the Flutter app
            .build()
}
