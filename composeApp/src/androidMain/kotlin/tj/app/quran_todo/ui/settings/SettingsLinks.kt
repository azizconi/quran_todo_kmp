package tj.app.quran_todo.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.app.Activity

/** Package of the app on the store; the rate row opens its listing. */
private const val AppPackage = "tj.app.quran_todo"

/**
 * Where the privacy policy is published. No such URL exists in the repository (the policy text lives
 * in `play_store_assets/privacy-policy-*.md`, the Play listing URL is not recorded anywhere), so the
 * row stays hidden until the owner puts the address here.
 */
internal val PrivacyPolicyUrl: String? = null

/** "1.0.2 (7)": the installed version, or an empty string when the package info cannot be read. */
internal fun appVersionLabel(context: Context): String = try {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }
    "${info.versionName.orEmpty()} ($code)"
} catch (_: PackageManager.NameNotFoundException) {
    ""
}

/** Opens [url] in the browser. Returns false when nothing could handle it. */
internal fun openLink(context: Context, url: String): Boolean = startViewIntent(context, Uri.parse(url))

/**
 * Opens the app's store listing: the Play Store app through `market://`, the website when the store
 * app is missing. Returns false when neither could be opened.
 */
internal fun openStoreListing(context: Context): Boolean =
    startViewIntent(context, Uri.parse("market://details?id=$AppPackage")) ||
        startViewIntent(context, Uri.parse("https://play.google.com/store/apps/details?id=$AppPackage"))

private fun startViewIntent(context: Context, uri: Uri): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, uri)
    if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
