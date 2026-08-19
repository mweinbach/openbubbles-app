package app.openbubbles.nativeapp.ui.effects

/**
 * Bubble-only expressive-send ids. Screen effects live in [SendScreenEffect];
 * invisible ink is handled as a blur-reveal, the rest as a spatial animation.
 */
enum class BubbleEffect(val id: String, val label: String, val icon: String) {
    SLAM("com.apple.MobileSMS.expressivesend.slam", "Slam", "💥"),
    LOUD("com.apple.MobileSMS.expressivesend.loud", "Loud", "📢"),
    GENTLE("com.apple.MobileSMS.expressivesend.gentle", "Gentle", "🎈"),
    ECHO("com.apple.MobileSMS.expressivesend.echo", "Echo", "🔁"),
    INVISIBLE_INK(INVISIBLE_INK_EFFECT_ID, "Invisible Ink", "◍"),
    ;

    companion object {
        fun fromId(id: String?): BubbleEffect? {
            if (id.isNullOrBlank()) return null
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
                ?: when {
                    id.contains("slam", ignoreCase = true) -> SLAM
                    id.contains("loud", ignoreCase = true) -> LOUD
                    id.contains("gentle", ignoreCase = true) -> GENTLE
                    id.contains("echo", ignoreCase = true) -> ECHO
                    id.contains("invisibleink", ignoreCase = true) -> INVISIBLE_INK
                    else -> null
                }
        }
    }
}
