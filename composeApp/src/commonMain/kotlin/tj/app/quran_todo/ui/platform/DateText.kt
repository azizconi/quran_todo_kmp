package tj.app.quran_todo.ui.platform

import tj.app.quran_todo.common.i18n.AppLanguage

/**
 * "14 March" (pattern `d MMMM`) in the language of the app. The epoch day is a calendar day, so the date is formatted in UTC whatever the
 * time zone of the device is.
 *
 * iOS implementation: an `NSDateFormatter` with `dateFormat = "d MMMM"`, the locale of the language tag (ru, en, tg, uz, tr) and the UTC
 * time zone, applied to `NSDate(timeIntervalSince1970: epochDay * 86400)`.
 */
expect fun formatDayOfMonth(epochDay: Int, language: AppLanguage): String
