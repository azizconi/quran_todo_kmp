package tj.app.quran_todo.common.reminder

import kotlin.test.Test
import kotlin.test.assertEquals

/** The reminder by days away (task U18). */
class ReminderDecisionTest {
    private fun assertDecision(expected: ReminderDecision, vararg days: Int) =
        days.forEach { assertEquals(expected, reminderDecision(it), "days away: $it") }

    @Test
    fun `up to two days away the usual reminder`() = assertDecision(ReminderDecision.ShowNormal, 0, 1, 2)

    @Test
    fun `three to six days away the gentle one every day`() = assertDecision(ReminderDecision.ShowGentle, 3, 4, 5, 6)

    @Test
    fun `seven to twenty days away the gentle one every third day`() {
        assertDecision(ReminderDecision.ShowGentle, 7, 10, 13, 16, 19)
        assertDecision(ReminderDecision.Skip, 8, 9, 11, 12, 14, 15, 17, 18, 20)
    }

    @Test
    fun `twenty one days and more nothing`() = assertDecision(ReminderDecision.Skip, 21, 22, 28, 100)

    @Test
    fun `no recorded day or a clock gone back is no absence`() {
        assertEquals(0, ReminderAbsence.daysAway(null, 20_000))
        assertEquals(0, ReminderAbsence.daysAway(20_005, 20_000))
    }

    @Test
    fun `opening the app resets the count`() {
        val today = 20_000
        val lastOpened = today - 30
        assertEquals(ReminderDecision.Skip, reminderDecision(ReminderAbsence.daysAway(lastOpened, today)))
        val afterOpening = ReminderAbsence.onForeground(today)
        assertEquals(0, ReminderAbsence.daysAway(afterOpening, today))
        assertEquals(ReminderDecision.ShowNormal, reminderDecision(ReminderAbsence.daysAway(afterOpening, today)))
        assertEquals(4, ReminderAbsence.daysAway(afterOpening, today + 4))
    }
}
