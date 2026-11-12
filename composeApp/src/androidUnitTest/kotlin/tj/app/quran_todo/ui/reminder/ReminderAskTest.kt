package tj.app.quran_todo.ui.reminder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderAskTest {
    @Test
    fun showsOnACompletedLessonWithReminderOffAndNoEarlierAnswer() {
        assertTrue(ReminderAsk.shouldShow(lessonComplete = true, reminderEnabled = false, alreadyAnswered = false))
    }

    @Test
    fun staysHiddenOtherwise() {
        assertFalse(ReminderAsk.shouldShow(lessonComplete = false, reminderEnabled = false, alreadyAnswered = false))
        assertFalse(ReminderAsk.shouldShow(lessonComplete = true, reminderEnabled = true, alreadyAnswered = false))
        assertFalse(ReminderAsk.shouldShow(lessonComplete = true, reminderEnabled = false, alreadyAnswered = true))
        assertFalse(ReminderAsk.shouldShow(lessonComplete = true, reminderEnabled = true, alreadyAnswered = true))
    }

    @Test
    fun timeIsRoundedToTheNearestHalfHour() {
        assertEquals(20 * 60, ReminderAsk.proposedMinute(20 * 60))
        assertEquals(20 * 60, ReminderAsk.proposedMinute(20 * 60 + 14))
        assertEquals(20 * 60 + 30, ReminderAsk.proposedMinute(20 * 60 + 15))
        assertEquals(20 * 60 + 30, ReminderAsk.proposedMinute(20 * 60 + 44))
        assertEquals(21 * 60, ReminderAsk.proposedMinute(20 * 60 + 45))
    }

    @Test
    fun timeAfterMidnightWraps() {
        assertEquals(0, ReminderAsk.proposedMinute(23 * 60 + 50))
        assertEquals(0, ReminderAsk.proposedMinute(0))
        assertEquals(23 * 60 + 30, ReminderAsk.proposedMinute(23 * 60 + 29))
    }
}
