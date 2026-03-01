package tj.app.quran_todo.common.reminder

expect object ReminderScheduler {
    fun syncDailyReminder(
        enabled: Boolean,
        title: String,
        body: String,
    )
}
