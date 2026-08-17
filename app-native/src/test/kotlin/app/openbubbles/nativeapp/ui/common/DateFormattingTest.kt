package app.openbubbles.nativeapp.ui.common

import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class DateFormattingTest {

    private val zone = ZoneOffset.UTC

    /** 2024-06-10T15:30:00Z — a fixed Monday afternoon "now". */
    private val now = 1_718_033_400_000L

    private fun daysBefore(days: Int, atHour: Int = 9, atMinute: Int = 41): Long {
        val startOfDay = now - (now % 86_400_000L)
        return startOfDay - days * 86_400_000L + atHour * 3_600_000L + atMinute * 60_000L
    }

    @Test
    fun `same day labels Today with the clock time`() {
        val label = formatConversationTimestamp(daysBefore(0), zone, now)
        assertEquals(ConversationTimestamp(day = "Today", time = "9:41 AM"), label)
    }

    @Test
    fun `previous day labels Yesterday`() {
        val label = formatConversationTimestamp(daysBefore(1), zone, now)
        assertEquals(ConversationTimestamp(day = "Yesterday", time = "9:41 AM"), label)
    }

    @Test
    fun `inside the last week uses the weekday name`() {
        // Three days before a Monday is a Friday.
        val label = formatConversationTimestamp(daysBefore(3, atHour = 18, atMinute = 5), zone, now)
        assertEquals(ConversationTimestamp(day = "Friday", time = "6:05 PM"), label)
    }

    @Test
    fun `a week or more ago uses the date`() {
        val label = formatConversationTimestamp(daysBefore(7), zone, now)
        assertEquals(ConversationTimestamp(day = "June 3", time = "9:41 AM"), label)
    }

    @Test
    fun `a different year appends the year`() {
        val label = formatConversationTimestamp(daysBefore(365), zone, now)
        assertEquals(ConversationTimestamp(day = "June 11, 2023", time = "9:41 AM"), label)
    }
}
