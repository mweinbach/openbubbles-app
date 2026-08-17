package app.openbubbles.nativeapp.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a")
private val shortDateFormat = DateTimeFormatter.ofPattern("M/d/yy")
private val dayFormat = DateTimeFormatter.ofPattern("MMMM d")
private val dayWithYearFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy")

/**
 * Chat-list timestamp: time today, "Yesterday", weekday inside the last week,
 * otherwise a short date.
 */
fun formatListTimestamp(
    epochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    nowMillis: Long = System.currentTimeMillis(),
): String {
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val nowDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val date = dateTime.toLocalDate()
    return when {
        date == nowDate -> timeFormat.format(dateTime)
        date == nowDate.minusDays(1) -> "Yesterday"
        ChronoUnit.DAYS.between(date, nowDate) < 7 -> date.dayOfWeek.name.lowercase()
            .replaceFirstChar { it.uppercase() }
            .take(3)
        else -> shortDateFormat.format(dateTime)
    }
}

/** Conversation time-separator label split so the day renders bold and the time regular. */
data class ConversationTimestamp(val day: String, val time: String)

/**
 * Conversation time-separator label, the Apple Messages way: "Today 3:02 PM",
 * "Yesterday …", a weekday inside the last week, then "August 12" (+ year
 * when different).
 */
fun formatConversationTimestamp(
    epochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    nowMillis: Long = System.currentTimeMillis(),
): ConversationTimestamp {
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val date = dateTime.toLocalDate()
    val nowDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val day = when {
        date == nowDate -> "Today"
        date == nowDate.minusDays(1) -> "Yesterday"
        ChronoUnit.DAYS.between(date, nowDate) < 7 -> date.dayOfWeek.name.lowercase()
            .replaceFirstChar { it.uppercase() }
        date.year == nowDate.year -> dayFormat.format(date)
        else -> dayWithYearFormat.format(date)
    }
    return ConversationTimestamp(day = day, time = timeFormat.format(dateTime))
}

/** Local calendar day for a timestamp, used to detect day boundaries. */
fun localDay(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

/**
 * Elapsed-time label for "last checked" style status rows: "just now",
 * "5 min ago", "3 h ago", "Yesterday", then a short date.
 */
fun formatRelativePast(
    epochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    nowMillis: Long = System.currentTimeMillis(),
): String {
    if (epochMillis <= 0L) return "never"
    val elapsedMs = (nowMillis - epochMillis).coerceAtLeast(0L)
    val minutes = elapsedMs / 60_000L
    if (minutes < 1) return "just now"
    if (minutes < 60) return "$minutes min ago"
    val hours = minutes / 60
    if (hours < 24) return "$hours h ago"
    val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val nowDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    if (date == nowDate.minusDays(1)) return "Yesterday"
    return shortDateFormat.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
}
