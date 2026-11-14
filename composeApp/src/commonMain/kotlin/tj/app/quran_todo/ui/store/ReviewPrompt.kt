package tj.app.quran_todo.ui.store

/**
 * The store's "rate the app" card, asked for at a happy moment: on the summary of a finished lesson.
 *
 * Android: Google Play in-app review (`InAppReview`, needs the activity). iOS (S2): `AppStore.requestReview(in:)` /
 * `SKStoreReviewController.requestReview(in:)` from the active scene. Both rules of when to ask (lessons finished on N different days,
 * days since install, a quiet period after an ask) are in the implementation; the platform may show nothing, so the app never waits for it.
 */
interface ReviewPrompt {
    /** A lesson was finished: counts it and, when the conditions hold, asks the store for its card. Every failure is swallowed. */
    suspend fun onLessonFinished()
}

/** Does nothing: tests, previews and a platform without a card. */
object NoReviewPrompt : ReviewPrompt {
    override suspend fun onLessonFinished() = Unit
}
