package app.openbubbles.shared

expect fun platformName(): String

object Hello {
    fun greeting(): String = "OpenBubbles native — Rust + Kotlin on ${platformName()}"
}
