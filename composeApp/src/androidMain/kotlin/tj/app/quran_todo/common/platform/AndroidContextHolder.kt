package tj.app.quran_todo.common.platform

import android.app.Activity
import android.content.Context
import java.lang.ref.WeakReference

object AndroidContextHolder {
    lateinit var context: Context

    private var activityRef: WeakReference<Activity>? = null

    var activity: Activity?
        get() = activityRef?.get()
        set(value) {
            activityRef = if (value == null) null else WeakReference(value)
        }
}
