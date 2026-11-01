package tj.app.quran_todo.ui.onboarding

import tj.app.quran_todo.common.platform.AndroidContextHolder
import tj.app.quran_todo.common.platform.OnceResolved
import tj.app.quran_todo.ui.state.OnboardingFlags
import tj.app.quran_todo.ui.state.UiPrefs

/** What the start of the app knows about the onboarding: the decision and the stored flags, read together. */
internal class OnboardingStartState(val gate: OnboardingGate, val flags: OnboardingFlags)

/**
 * The onboarding decision of this launch, worked out once. The disk read behind it (the SharedPreferences files) starts on a worker thread
 * in `Application.onCreate` ([resolveInBackground]); the splash screen waits for it without blocking the main thread
 * ([MainActivity]), so the first screen is never the wrong one. Anyone who asks before it is ready ([current]) reads it on the spot.
 */
internal object OnboardingStartup {
    private val resolved = OnceResolved {
        val context = AndroidContextHolder.context
        val gate = OnboardingGate.resolve(context)
        // Read after the decision: an existing user has just been marked as having passed it.
        OnboardingStartState(gate, UiPrefs.readOnboarding(context))
    }

    val isReady: Boolean get() = resolved.isReady

    fun resolveInBackground() = resolved.resolveQuietly()

    /** Waits (suspending) until [resolveInBackground] is done. Does not return when it failed: the caller times out and uses [current]. */
    suspend fun await(): OnboardingStartState = resolved.await()

    fun current(): OnboardingStartState = resolved.get()
}
