package app.openbubbles.nativeapp.data

import java.util.Calendar
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScheduledSendTest {
    private val zone = TimeZone.getTimeZone("UTC")

    @Test
    fun `one hour is offset from now`() {
        val now = 1_700_000_000_000L
        assertEquals(
            now + 3_600_000L,
            scheduledSendEpochMs(ScheduledSendWhen.IN_ONE_HOUR, now, Calendar.getInstance(zone)),
        )
    }

    @Test
    fun `tonight rolls to tomorrow after 9pm`() {
        val calendar = Calendar.getInstance(zone)
        calendar.set(2026, Calendar.AUGUST, 19, 22, 15, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val now = calendar.timeInMillis
        val scheduled = scheduledSendEpochMs(ScheduledSendWhen.TONIGHT, now, Calendar.getInstance(zone))
        val result = Calendar.getInstance(zone).apply { timeInMillis = scheduled }
        assertEquals(20, result.get(Calendar.DAY_OF_MONTH))
        assertEquals(21, result.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, result.get(Calendar.MINUTE))
    }

    @Test
    fun `tomorrow morning is 8am next day`() {
        val calendar = Calendar.getInstance(zone)
        calendar.set(2026, Calendar.AUGUST, 19, 10, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val now = calendar.timeInMillis
        val scheduled = scheduledSendEpochMs(
            ScheduledSendWhen.TOMORROW_MORNING,
            now,
            Calendar.getInstance(zone),
        )
        assertTrue(scheduled > now)
        val result = Calendar.getInstance(zone).apply { timeInMillis = scheduled }
        assertEquals(20, result.get(Calendar.DAY_OF_MONTH))
        assertEquals(8, result.get(Calendar.HOUR_OF_DAY))
    }
}
