package tj.app.quran_todo.common.reminder

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Each alarm is set twice for the same moment (a 10-minute window and an allow-while-idle copy): only the first one counts. */
class ReminderAlarmsTest {
    private val planned = 1_793_287_200_000L

    @Test
    fun theFirstCopyOfAMomentIsTaken() {
        assertTrue(ReminderAlarms.isFirstArrival(planned, lastClaimed = 0L))
        assertTrue(ReminderAlarms.isFirstArrival(planned, lastClaimed = planned - 24L * 60 * 60 * 1000))
    }

    @Test
    fun theSecondCopyOfTheSameMomentIsDropped() {
        assertFalse(ReminderAlarms.isFirstArrival(planned, lastClaimed = planned))
    }

    @Test
    fun anAlarmWithoutAPlannedMomentIsAlwaysTaken() {
        assertTrue(ReminderAlarms.isFirstArrival(0L, lastClaimed = 0L))
        assertTrue(ReminderAlarms.isFirstArrival(0L, lastClaimed = planned))
    }

    @Test
    fun theWindowIsTheShortestTheSystemKeepsWithoutThePermission() {
        assertTrue(ReminderAlarms.WINDOW_MILLIS == 10L * 60 * 1000)
    }
}
