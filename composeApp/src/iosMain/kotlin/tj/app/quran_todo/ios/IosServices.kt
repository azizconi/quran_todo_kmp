package tj.app.quran_todo.ios

import tj.app.quran_todo.ui.progress.report.ReportDoc

/**
 * What Kotlin needs from Apple frameworks that are Swift-only (WidgetKit, the drawing of the PDF, the share sheet). The Swift side
 * implements it in `iosApp/iosApp/Services/` and registers it in `ServicesBootstrap.register()` (call it once after `InitKoinIos()`).
 * Until it is registered every call is a no-op (tests, previews).
 */
interface IosPlatformHooks {
    /** `WidgetCenter.shared.reloadAllTimelines()`: the snapshot in the App Group changed. */
    fun reloadWidgets()

    /** Draws [doc] as an A4 PDF at [path] (`UIGraphicsPDFRenderer`); true when the file was written. */
    fun renderReportPdf(doc: ReportDoc, path: String): Boolean

    /** Opens the share sheet (`UIActivityViewController`) for the file at [location] (a `file://` url). */
    fun presentShare(location: String, title: String)

    /** `AppStore.requestReview(in:)` for the active scene. */
    fun requestReview()
}

/** The doors between the Swift services and the Kotlin ones. */
object IosServices {
    /** The App Group of the app and its widget. */
    const val APP_GROUP = "group.tj.app.quran-todo"

    /** Set by `ServicesBootstrap.register()`. */
    var hooks: IosPlatformHooks? = null
}
