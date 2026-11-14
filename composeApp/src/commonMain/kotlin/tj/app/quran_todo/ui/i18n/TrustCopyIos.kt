package tj.app.quran_todo.ui.i18n

import tj.app.quran_todo.common.i18n.AppLanguage

/** Indexes of the points of [TrustCopy.privacyPoints] that name the platform (backup, statistics, deleting). */
private const val PointBackup = 1
private const val PointStatistics = 3
private const val PointDeleting = 7

/**
 * The privacy copy for iOS: the shared [trustCopy] with the three points that speak of Android (backup, the device data
 * Firebase collects, deleting the data) said for iPhone. Android keeps [trustCopy] unchanged.
 */
fun trustCopyIos(language: AppLanguage): TrustCopy {
    val base = trustCopy(language)
    val (backup, statistics, deleting) = when (language) {
        AppLanguage.RU -> Triple(
            "Прогресс, заметки и настройки хранятся на устройстве. Если включена копия iCloud, она может храниться в вашем аккаунте Apple.",
            "Firebase собирает события использования, модель устройства, версию iOS и отчёты о сбоях — без рекламного идентификатора. Выключить можно в настройках.",
            "Удалите приложение или сотрите его данные в настройках iPhone — и всё, что хранится на устройстве, исчезнет.",
        )
        AppLanguage.TG -> Triple(
            "Пешрафт, ёддоштҳо ва танзимот дар дастгоҳ нигоҳ дошта мешаванд. Агар нусхабардории iCloud фаъол бошад, нусха метавонад дар ҳисоби Apple-и шумо нигоҳ дошта шавад.",
            "Firebase рӯйдодҳои истифода, модели дастгоҳ, версияи iOS ва ҳисоботи садамаҳоро ҷамъ мекунад — бе шиносаи таблиғотӣ. Онро дар танзимот хомӯш кардан мумкин аст.",
            "Барномаро нест кунед ё маълумоти онро дар танзимоти iPhone пок кунед — ва ҳар чӣ дар дастгоҳ нигоҳ дошта мешавад, нест мешавад.",
        )
        AppLanguage.UZ -> Triple(
            "Jarayon, qaydlar va sozlamalar qurilmada saqlanadi. iCloud zaxira nusxasi yoqilgan bo‘lsa, nusxa Apple hisobingizda saqlanishi mumkin.",
            "Firebase foydalanish hodisalarini, qurilma modelini, iOS versiyasini va xatolik hisobotlarini yig‘adi — reklama identifikatorisiz. Uni sozlamalarda o‘chirib qo‘yish mumkin.",
            "Ilovani o‘chiring yoki uning ma’lumotlarini iPhone sozlamalarida tozalang — qurilmada saqlangan hamma narsa yo‘qoladi.",
        )
        AppLanguage.TR -> Triple(
            "İlerleme, notlar ve ayarlar cihazda saklanır. iCloud yedekleme açıksa, bir kopya Apple hesabınızda tutulabilir.",
            "Firebase; kullanım olaylarını, cihaz modelini, iOS sürümünü ve çökme raporlarını toplar — reklam kimliği olmadan. Ayarlardan kapatabilirsiniz.",
            "Uygulamayı silin veya iPhone ayarlarında verilerini temizleyin — cihazda saklanan her şey kaybolur.",
        )
        else -> Triple(
            "Progress, notes and settings live on this device. If iCloud backup is on, Apple can keep a copy in your iCloud account.",
            "Firebase collects usage events, the device model, the iOS version and crash reports, without an advertising ID. You can turn this off in Settings.",
            "Delete the app or erase its data in iPhone settings, and everything stored on the device is gone.",
        )
    }
    val points = base.privacyPoints.toMutableList()
    points[PointBackup] = points[PointBackup].copy(text = backup)
    points[PointStatistics] = points[PointStatistics].copy(text = statistics)
    points[PointDeleting] = points[PointDeleting].copy(text = deleting)
    return base.copy(privacyPoints = points)
}
