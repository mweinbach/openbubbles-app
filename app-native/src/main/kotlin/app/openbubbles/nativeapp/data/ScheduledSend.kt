package app.openbubbles.nativeapp.data

import java.util.Calendar

enum class ScheduledSendWhen {
    IN_ONE_HOUR,
    TONIGHT,
    TOMORROW_MORNING,
}

fun scheduledSendEpochMs(
    slot: ScheduledSendWhen,
    nowMs: Long = System.currentTimeMillis(),
    calendar: Calendar = Calendar.getInstance(),
): Long {
    calendar.timeInMillis = nowMs
    return when (slot) {
        ScheduledSendWhen.IN_ONE_HOUR -> nowMs + 60L * 60L * 1000L
        ScheduledSendWhen.TONIGHT -> {
            calendar.set(Calendar.HOUR_OF_DAY, 21)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            if (calendar.timeInMillis <= nowMs) calendar.add(Calendar.DAY_OF_YEAR, 1)
            calendar.timeInMillis
        }
        ScheduledSendWhen.TOMORROW_MORNING -> {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 8)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        }
    }
}
