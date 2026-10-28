package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/** One short point of the in-app privacy summary. */
data class PrivacyPoint(val title: String, val text: String)

/**
 * Strings of the "Privacy" section in the settings, the privacy summary screen and the "Sources"
 * screen. Names of authors, editions and services are not translated: they live in `SourcesData.kt`.
 * Tajik, Uzbek and Turkish texts are to be checked by native speakers.
 */
data class TrustCopy(
    // Settings section
    val sectionPrivacy: String,
    val analyticsTitle: String,
    val analyticsDescription: String,
    val privacyRowDescription: String,
    val sourcesRowTitle: String,
    val sourcesRowDescription: String,
    // Privacy screen
    val privacyTitle: String,
    val privacyIntro: String,
    val privacyPoints: List<PrivacyPoint>,
    val privacyFullText: String,
    val privacyContact: String,
    // Sources screen
    val sourcesTitle: String,
    val sourcesIntro: String,
    val groupQuranText: String,
    val groupTranslations: String,
    val groupSurahNames: String,
    val groupAudio: String,
    val groupFonts: String,
    val groupLibraries: String,
    val licenseLabel: String,
    val noteTanzilTerms: String,
    val noteTranslatorRights: String,
    val noteAppTeamNames: String,
    val noteReciterRights: String,
    val openLink: String,
    val sourcesFooter: String,
)

@Composable
fun rememberTrustCopy(language: AppLanguage = LocalAppLanguage.current): TrustCopy =
    remember(language) { trustCopy(language) }

fun trustCopy(language: AppLanguage): TrustCopy = when (language) {
    AppLanguage.EN -> TrustCopy(
        sectionPrivacy = "Privacy",
        analyticsTitle = "Anonymous statistics and crash reports",
        analyticsDescription = "Helps find bugs. No ads and no personal data.",
        privacyRowDescription = "What is stored and what is sent",
        sourcesRowTitle = "Sources",
        sourcesRowDescription = "Quran text, translations, audio, fonts",
        privacyTitle = "Privacy",
        privacyIntro = "The short version. The full text is in the privacy policy.",
        privacyPoints = listOf(
            PrivacyPoint(
                "No accounts",
                "You don't sign in and nothing is tied to a name or an e-mail. The app has no server of its own.",
            ),
            PrivacyPoint(
                "Stays on your phone",
                "Progress, notes and settings live on this device. If Android backup is on, Google can keep a copy in your Google account.",
            ),
            PrivacyPoint(
                "Internet use",
                "To load the Quran text, translations and audio the app contacts api.alquran.cloud, api.quran.com and cdn.islamic.network. They can see your IP address. Your progress and notes are never sent.",
            ),
            PrivacyPoint(
                "Anonymous statistics",
                "Firebase collects usage events, the device model, the Android version and crash reports, without an advertising ID. You can turn this off in Settings.",
            ),
            PrivacyPoint(
                "Few permissions",
                "Internet access, and notifications only if you turn on reminders. The app does not access your contacts, location or files.",
            ),
            PrivacyPoint(
                "No ads",
                "There are no ads, and your data is never sold or shared for advertising.",
            ),
            PrivacyPoint(
                "Deleting data",
                "Delete the app or clear its data in Android settings, and everything stored on the device is gone.",
            ),
        ),
        privacyFullText = "Full text",
        privacyContact = "Contact the developer",
        sourcesTitle = "Sources",
        sourcesIntro = "Where the Quran text, translations, audio and fonts come from, and on what terms.",
        groupQuranText = "Quran text",
        groupTranslations = "Translations",
        groupSurahNames = "Surah names",
        groupAudio = "Audio",
        groupFonts = "Fonts",
        groupLibraries = "Open-source libraries",
        licenseLabel = "License",
        noteTanzilTerms = "Tanzil's condition: the text is not changed and the source is credited with a link.",
        noteTranslatorRights = "Rights to the translation belong to the translator and the publisher.",
        noteAppTeamNames = "Compiled by the app team.",
        noteReciterRights = "The recitation belongs to the reciter.",
        openLink = "Open link",
        sourcesFooter = "Names of authors, editions and services are given as the sources publish them and are not translated.",
    )

    AppLanguage.RU -> TrustCopy(
        sectionPrivacy = "Конфиденциальность",
        analyticsTitle = "Анонимная статистика и отчёты о сбоях",
        analyticsDescription = "Помогает находить ошибки. Без рекламы и личных данных.",
        privacyRowDescription = "Что хранится и что отправляется",
        sourcesRowTitle = "Источники",
        sourcesRowDescription = "Текст Корана, переводы, аудио, шрифты",
        privacyTitle = "Конфиденциальность",
        privacyIntro = "Коротко о главном. Полный текст — в политике конфиденциальности.",
        privacyPoints = listOf(
            PrivacyPoint(
                "Без аккаунтов",
                "Входить никуда не нужно, ничто не привязано к имени или почте. У приложения нет собственного сервера.",
            ),
            PrivacyPoint(
                "Данные на вашем телефоне",
                "Прогресс, заметки и настройки хранятся на устройстве. Если на телефоне включено резервное копирование Android, копия может храниться в вашем аккаунте Google.",
            ),
            PrivacyPoint(
                "Обращения к интернету",
                "Чтобы загрузить текст Корана, переводы и аудио, приложение обращается к api.alquran.cloud, api.quran.com и cdn.islamic.network. Они видят ваш IP-адрес. Прогресс и заметки не отправляются.",
            ),
            PrivacyPoint(
                "Анонимная статистика",
                "Firebase собирает события использования, модель устройства, версию Android и отчёты о сбоях — без рекламного идентификатора. Выключить можно в настройках.",
            ),
            PrivacyPoint(
                "Минимум разрешений",
                "Доступ в интернет и уведомления — только если вы включили напоминания. Контакты, местоположение и файлы приложению не нужны.",
            ),
            PrivacyPoint(
                "Без рекламы",
                "В приложении нет рекламы, а ваши данные не продаются и не передаются для рекламы.",
            ),
            PrivacyPoint(
                "Удаление данных",
                "Удалите приложение или очистите его данные в настройках Android — и всё, что хранится на устройстве, исчезнет.",
            ),
        ),
        privacyFullText = "Полный текст",
        privacyContact = "Связаться с разработчиком",
        sourcesTitle = "Источники",
        sourcesIntro = "Откуда взяты текст Корана, переводы, аудио и шрифты и на каких условиях они используются.",
        groupQuranText = "Текст Корана",
        groupTranslations = "Переводы",
        groupSurahNames = "Названия сур",
        groupAudio = "Аудио",
        groupFonts = "Шрифты",
        groupLibraries = "Библиотеки с открытым кодом",
        licenseLabel = "Лицензия",
        noteTanzilTerms = "Условие Tanzil: текст не изменяется, источник указывается со ссылкой.",
        noteTranslatorRights = "Права на перевод принадлежат переводчику и издателю.",
        noteAppTeamNames = "Составлено командой приложения.",
        noteReciterRights = "Запись принадлежит чтецу.",
        openLink = "Открыть ссылку",
        sourcesFooter = "Имена авторов, названия изданий и сервисов даны так, как их публикуют источники, и не переводятся.",
    )

    AppLanguage.TG -> TrustCopy(
        sectionPrivacy = "Махфият",
        analyticsTitle = "Омори беном ва ҳисоботи хатоҳо",
        analyticsDescription = "Ба ёфтани хатоҳо кӯмак мекунад. Бе реклама ва маълумоти шахсӣ.",
        privacyRowDescription = "Чӣ нигоҳ дошта ва чӣ фиристода мешавад",
        sourcesRowTitle = "Сарчашмаҳо",
        sourcesRowDescription = "Матни Қуръон, тарҷумаҳо, аудио, шрифтҳо",
        privacyTitle = "Махфият",
        privacyIntro = "Муҳимтаринҳо мухтасар. Матни пурра — дар сиёсати махфият.",
        privacyPoints = listOf(
            PrivacyPoint(
                "Бе ҳисоб",
                "Ба ҷое даромадан лозим нест ва чизе ба ном ё почта вобаста нест. Барнома сервери худро надорад.",
            ),
            PrivacyPoint(
                "Маълумот дар телефони шумо",
                "Пешрафт, ёддоштҳо ва танзимот дар дастгоҳ нигоҳ дошта мешаванд. Агар нусхабардории Android фаъол бошад, нусха метавонад дар ҳисоби Google-и шумо нигоҳ дошта шавад.",
            ),
            PrivacyPoint(
                "Истифодаи интернет",
                "Барои боркунии матни Қуръон, тарҷумаҳо ва аудио барнома ба api.alquran.cloud, api.quran.com ва cdn.islamic.network муроҷиат мекунад. Онҳо суроғаи IP-и шуморо мебинанд. Пешрафт ва ёддоштҳо фиристода намешаванд.",
            ),
            PrivacyPoint(
                "Омори беном",
                "Firebase рӯйдодҳои истифода, модели дастгоҳ, версияи Android ва ҳисоботи садамаҳоро ҷамъ мекунад — бе шиносаи таблиғотӣ. Онро дар танзимот хомӯш кардан мумкин аст.",
            ),
            PrivacyPoint(
                "Иҷозатҳои ками зарурӣ",
                "Дастрасӣ ба интернет ва огоҳиномаҳо — танҳо агар шумо ёдраскуниро фаъол кунед. Барнома ба тамосҳо, ҷойгиршавӣ ва файлҳо ниёз надорад.",
            ),
            PrivacyPoint(
                "Бе реклама",
                "Дар барнома реклама нест ва маълумоти шумо барои реклама фурӯхта ё дода намешавад.",
            ),
            PrivacyPoint(
                "Нест кардани маълумот",
                "Барномаро нест кунед ё маълумоти онро дар танзимоти Android пок кунед — ва ҳар чӣ дар дастгоҳ нигоҳ дошта мешавад, нест мешавад.",
            ),
        ),
        privacyFullText = "Матни пурра",
        privacyContact = "Бо таҳиягар тамос гирифтан",
        sourcesTitle = "Сарчашмаҳо",
        sourcesIntro = "Матни Қуръон, тарҷумаҳо, аудио ва шрифтҳо аз куҷо гирифта шудаанд ва бо кадом шартҳо истифода мешаванд.",
        groupQuranText = "Матни Қуръон",
        groupTranslations = "Тарҷумаҳо",
        groupSurahNames = "Номҳои сураҳо",
        groupAudio = "Аудио",
        groupFonts = "Шрифтҳо",
        groupLibraries = "Китобхонаҳои кодашон кушода",
        licenseLabel = "Литсензия",
        noteTanzilTerms = "Шарти Tanzil: матн тағйир дода намешавад, сарчашма бо истинод нишон дода мешавад.",
        noteTranslatorRights = "Ҳуқуқи тарҷума ба тарҷумон ва нашриёт тааллуқ дорад.",
        noteAppTeamNames = "Аз ҷониби дастаи барнома таҳия шудааст.",
        noteReciterRights = "Сабт ба қорӣ тааллуқ дорад.",
        openLink = "Кушодани истинод",
        sourcesFooter = "Номи муаллифон, нашрҳо ва хизматрасонӣ ҳамон тавр дода шудааст, ки сарчашмаҳо нашр мекунанд, ва тарҷума намешавад.",
    )

    AppLanguage.UZ -> TrustCopy(
        sectionPrivacy = "Maxfiylik",
        analyticsTitle = "Anonim statistika va xatolik hisobotlari",
        analyticsDescription = "Xatolarni topishga yordam beradi. Reklamasiz va shaxsiy ma’lumotlarsiz.",
        privacyRowDescription = "Nima saqlanadi va nima yuboriladi",
        sourcesRowTitle = "Manbalar",
        sourcesRowDescription = "Qur’on matni, tarjimalar, audio, shriftlar",
        privacyTitle = "Maxfiylik",
        privacyIntro = "Eng muhimi qisqacha. To‘liq matn — maxfiylik siyosatida.",
        privacyPoints = listOf(
            PrivacyPoint(
                "Akkauntsiz",
                "Hech qayerga kirish shart emas, hech narsa ism yoki pochtaga bog‘lanmaydi. Ilovaning o‘z serveri yo‘q.",
            ),
            PrivacyPoint(
                "Ma’lumotlar telefoningizda",
                "Jarayon, qaydlar va sozlamalar qurilmada saqlanadi. Android zaxira nusxalash yoqilgan bo‘lsa, nusxa Google hisobingizda saqlanishi mumkin.",
            ),
            PrivacyPoint(
                "Internetdan foydalanish",
                "Qur’on matni, tarjimalar va audioni yuklash uchun ilova api.alquran.cloud, api.quran.com va cdn.islamic.network manzillariga murojaat qiladi. Ular IP-manzilingizni ko‘radi. Jarayon va qaydlar yuborilmaydi.",
            ),
            PrivacyPoint(
                "Anonim statistika",
                "Firebase foydalanish hodisalarini, qurilma modelini, Android versiyasini va xatolik hisobotlarini yig‘adi — reklama identifikatorisiz. Uni sozlamalarda o‘chirib qo‘yish mumkin.",
            ),
            PrivacyPoint(
                "Kam ruxsatlar",
                "Internetga kirish va bildirishnomalar — faqat eslatmalarni yoqsangiz. Ilovaga kontaktlar, joylashuv va fayllar kerak emas.",
            ),
            PrivacyPoint(
                "Reklamasiz",
                "Ilovada reklama yo‘q, ma’lumotlaringiz sotilmaydi va reklama uchun berilmaydi.",
            ),
            PrivacyPoint(
                "Ma’lumotlarni o‘chirish",
                "Ilovani o‘chiring yoki uning ma’lumotlarini Android sozlamalarida tozalang — qurilmada saqlangan hamma narsa yo‘qoladi.",
            ),
        ),
        privacyFullText = "To‘liq matn",
        privacyContact = "Dasturchi bilan bog‘lanish",
        sourcesTitle = "Manbalar",
        sourcesIntro = "Qur’on matni, tarjimalar, audio va shriftlar qayerdan olingan va qanday shartlarda ishlatiladi.",
        groupQuranText = "Qur’on matni",
        groupTranslations = "Tarjimalar",
        groupSurahNames = "Sura nomlari",
        groupAudio = "Audio",
        groupFonts = "Shriftlar",
        groupLibraries = "Ochiq kodli kutubxonalar",
        licenseLabel = "Litsenziya",
        noteTanzilTerms = "Tanzil sharti: matn o‘zgartirilmaydi, manba havola bilan ko‘rsatiladi.",
        noteTranslatorRights = "Tarjimaga bo‘lgan huquqlar tarjimon va nashriyotga tegishli.",
        noteAppTeamNames = "Ilova jamoasi tomonidan tuzilgan.",
        noteReciterRights = "Yozuv qoriga tegishli.",
        openLink = "Havolani ochish",
        sourcesFooter = "Mualliflar, nashrlar va xizmatlar nomlari manbalar e’lon qilgandek berilgan va tarjima qilinmaydi.",
    )

    AppLanguage.TR -> TrustCopy(
        sectionPrivacy = "Gizlilik",
        analyticsTitle = "Anonim istatistikler ve çökme raporları",
        analyticsDescription = "Hataları bulmaya yardımcı olur. Reklamsız ve kişisel veri olmadan.",
        privacyRowDescription = "Neler saklanır, neler gönderilir",
        sourcesRowTitle = "Kaynaklar",
        sourcesRowDescription = "Kur’an metni, mealler, ses, yazı tipleri",
        privacyTitle = "Gizlilik",
        privacyIntro = "Önemli olanlar kısaca. Tam metin gizlilik politikasındadır.",
        privacyPoints = listOf(
            PrivacyPoint(
                "Hesap yok",
                "Hiçbir yere giriş yapmanız gerekmez, hiçbir şey ad veya e-postaya bağlanmaz. Uygulamanın kendi sunucusu yoktur.",
            ),
            PrivacyPoint(
                "Veriler telefonunuzda",
                "İlerleme, notlar ve ayarlar cihazda saklanır. Android yedekleme açıksa, bir kopya Google hesabınızda tutulabilir.",
            ),
            PrivacyPoint(
                "İnternet kullanımı",
                "Kur’an metnini, mealleri ve sesi yüklemek için uygulama api.alquran.cloud, api.quran.com ve cdn.islamic.network adreslerine bağlanır. Bunlar IP adresinizi görür. İlerlemeniz ve notlarınız gönderilmez.",
            ),
            PrivacyPoint(
                "Anonim istatistikler",
                "Firebase; kullanım olaylarını, cihaz modelini, Android sürümünü ve çökme raporlarını toplar — reklam kimliği olmadan. Ayarlardan kapatabilirsiniz.",
            ),
            PrivacyPoint(
                "Az izin",
                "İnternet erişimi ve bildirimler — yalnızca hatırlatıcıları açarsanız. Uygulama kişilerinize, konumunuza veya dosyalarınıza erişmez.",
            ),
            PrivacyPoint(
                "Reklam yok",
                "Uygulamada reklam yoktur; verileriniz satılmaz ve reklam amacıyla paylaşılmaz.",
            ),
            PrivacyPoint(
                "Verileri silme",
                "Uygulamayı silin veya Android ayarlarında verilerini temizleyin — cihazda saklanan her şey kaybolur.",
            ),
        ),
        privacyFullText = "Tam metin",
        privacyContact = "Geliştiriciyle iletişim",
        sourcesTitle = "Kaynaklar",
        sourcesIntro = "Kur’an metni, mealler, ses ve yazı tiplerinin nereden alındığı ve hangi koşullarla kullanıldığı.",
        groupQuranText = "Kur’an metni",
        groupTranslations = "Mealler",
        groupSurahNames = "Sure adları",
        groupAudio = "Ses",
        groupFonts = "Yazı tipleri",
        groupLibraries = "Açık kaynaklı kütüphaneler",
        licenseLabel = "Lisans",
        noteTanzilTerms = "Tanzil şartı: metin değiştirilmez, kaynak bağlantıyla belirtilir.",
        noteTranslatorRights = "Mealin hakları çevirmene ve yayıncıya aittir.",
        noteAppTeamNames = "Uygulama ekibi tarafından hazırlandı.",
        noteReciterRights = "Kayıt kâriye aittir.",
        openLink = "Bağlantıyı aç",
        sourcesFooter = "Yazar, yayın ve hizmet adları kaynakların yayımladığı gibi verilmiştir ve çevrilmez.",
    )
}
