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

/** Conversation day-separator label: Today / Yesterday / "August 12" (+ year when different). */
fun formatConversationDay(
    epochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    nowMillis: Long = System.currentTimeMillis(),
): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val nowDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return when {
        date == nowDate -> "Today"
        date == nowDate.minusDays(1) -> "Yesterday"
        date.year == nowDate.year -> dayFormat.format(date)
        else -> dayWithYearFormat.format(date)
    }
}

/** Local calendar day for a timestamp, used to detect day boundaries. */
fun localDay(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
