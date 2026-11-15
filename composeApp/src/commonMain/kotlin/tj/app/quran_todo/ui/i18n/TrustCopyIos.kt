package tj.app.quran_todo.ui.i18n

import tj.app.quran_todo.common.i18n.AppLanguage

/** Indexes of the points of [TrustCopy.privacyPoints] that name the platform (backup, statistics, deleting). */
private const val PointBackup = 1
private const val PointStatistics = 3
private const val PointDeleting = 6

/**
 * The privacy copy for iOS: the shared [trustCopy] with the three points that speak of Android said for iPhone. The iOS app has no
 * Firebase and sends no statistics or crash reports, so the "statistics" point says that nothing is collected, and the "deleting" point
 * does not promise a data eraser in the iPhone settings (there is none). Android keeps [trustCopy] unchanged.
 */
fun trustCopyIos(language: AppLanguage): TrustCopy {
    val base = trustCopy(language)
    val (backup, statistics, deleting) = when (language) {
        AppLanguage.RU -> Triple(
            "Прогресс, заметки и настройки хранятся на устройстве. Если включена копия iCloud, она может храниться в вашем аккаунте Apple.",
            PrivacyPoint(
                "Ничего не собираем",
                "Приложение не собирает статистику и отчёты о сбоях. Никакие данные не покидают устройство, кроме загрузок аудио Корана, переводов и таджвида из открытых источников.",
            ),
            "Удалите приложение — и всё, что хранится на устройстве, исчезнет.",
        )
        AppLanguage.TG -> Triple(
            "Пешрафт, ёддоштҳо ва танзимот дар дастгоҳ нигоҳ дошта мешаванд. Агар нусхабардории iCloud фаъол бошад, нусха метавонад дар ҳисоби Apple-и шумо нигоҳ дошта шавад.",
            PrivacyPoint(
                "Чизе ҷамъ намекунем",
                "Барнома омор ва ҳисоботи садамаҳоро ҷамъ намекунад. Ҳеҷ маълумот аз дастгоҳ берун намеравад, ғайр аз боргирии аудиои Қуръон, тарҷумаҳо ва таҷвид аз сарчашмаҳои кушод.",
            ),
            "Барномаро нест кунед — ва ҳар чӣ дар дастгоҳ нигоҳ дошта мешавад, нест мешавад.",
        )
        AppLanguage.UZ -> Triple(
            "Jarayon, qaydlar va sozlamalar qurilmada saqlanadi. iCloud zaxira nusxasi yoqilgan bo‘lsa, nusxa Apple hisobingizda saqlanishi mumkin.",
            PrivacyPoint(
                "Hech narsa yig‘ilmaydi",
                "Ilova statistika va xatolik hisobotlarini yig‘maydi. Qur’on audiosi, tarjimalar va tajvidni ochiq manbalardan yuklashdan tashqari, qurilmadan hech qanday ma’lumot chiqmaydi.",
            ),
            "Ilovani o‘chiring — qurilmada saqlangan hamma narsa yo‘qoladi.",
        )
        AppLanguage.TR -> Triple(
            "İlerleme, notlar ve ayarlar cihazda saklanır. iCloud yedekleme açıksa, bir kopya Apple hesabınızda tutulabilir.",
            PrivacyPoint(
                "Hiçbir şey toplanmaz",
                "Uygulama istatistik ve çökme raporu toplamaz. Kur’an sesi, çeviriler ve tecvidin herkese açık kaynaklardan indirilmesi dışında cihazdan hiçbir veri çıkmaz.",
            ),
            "Uygulamayı silin — cihazda saklanan her şey kaybolur.",
        )
        else -> Triple(
            "Progress, notes and settings live on this device. If iCloud backup is on, Apple can keep a copy in your iCloud account.",
            PrivacyPoint(
                "Nothing is collected",
                "The app collects no statistics and no crash reports. No data leaves the device except downloads of Quran audio, translations and tajweed from the public sources.",
            ),
            "Delete the app, and everything stored on the device is gone.",
        )
    }
    val points = base.privacyPoints.toMutableList()
    points[PointBackup] = points[PointBackup].copy(text = backup)
    points[PointStatistics] = statistics
    points[PointDeleting] = points[PointDeleting].copy(text = deleting)
    return base.copy(privacyPoints = points)
}
