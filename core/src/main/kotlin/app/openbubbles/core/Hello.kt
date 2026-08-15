package app.openbubbles.core

/**
 * Boot smoke-test greeting shown in the debug status footer.
 *
 * This used to live in a Kotlin Multiplatform `:shared` module whose entire
 * content was this string plus an `expect`/`actual` platform name. That module
 * was removed: `org.jetbrains.kotlin.multiplatform` is not compatible with
 * `com.android.library` from AGP 9 onward, and a whole KMP source-set layout is
 * not worth carrying for one debug label. `:core` is a plain kotlin-jvm module
 * that both the Android app and the desktop app already depend on, so the
 * expect/actual split collapses into a single runtime check that produces the
 * exact same two strings as before.
 */
object Hello {
    fun greeting(): String = "OpenBubbles native — Rust + Kotlin on ${platformName()}"

    private fun platformName(): String =
        if (runCatching { Class.forName("android.os.Build") }.isSuccess) "Android" else "desktop JVM"
}
