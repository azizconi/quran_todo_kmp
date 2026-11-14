package tj.app.quran_todo.ui.store

import android.content.Context

/** [ReviewPrompt] of Google Play, for the activity [context] of the lesson screen (the card needs an activity). */
class AndroidReviewPrompt(private val context: Context) : ReviewPrompt {
    override suspend fun onLessonFinished() = InAppReview.onLessonFinished(context)
}
