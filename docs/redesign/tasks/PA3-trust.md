# Задача A3 — приватность, источники и гигиена релиза

Исполнитель: Sonnet. Ветка: твой worktree от `redesign/android`. Параллельно идут A1 (данные) и A2 (звук). Эмулятор занят A2 — **не используй его**.

## Окружение

1. Worktree может быть создан от `master`: первым делом `git merge --ff-only redesign/android`.
2. `cp /Users/azizjon/projects/quran_todo_kmp/local.properties .`
3. Собирай только `./gradlew` из корня worktree. Основной checkout не трогай.

## Прочитай сначала

1. `docs/redesign/PRODUCT_PLAN.md` — 2.1 («Источники и лицензии»), 2.4 («Распознавание чтения»), 2.5 («Резервная копия» — только правила Auto Backup), 2.7, 6 «Этап A».
2. `docs/redesign/API.md`, `docs/redesign/ANDROID_REDESIGN.md` — разделы 1, 2, 6.6 (настройки).
3. Код: `ui/settings/**`, `ui/navigation/Dest.kt`, `ui/QuranHifzApp.kt` (NavHost), `ui/state/UiPrefs*` (или где лежит `UiPrefs`), `androidMain/.../common/analytics/AppTelemetry.android.kt`, `androidMain/.../common/recitation/**`, `QuranTodoApplication`, `AndroidManifest.xml`, `composeApp/build.gradle.kts`, `play_store_assets/privacy-policy-*.md`, `composeResources/font/NOTICE.md`.

## Зона

`ui/settings/**` (кроме раздела «Звук» — его меняет A2), новые экраны в `ui/settings/` или `ui/about/`, `Dest.kt` + записи NavHost в `QuranHifzApp.kt` (только новые маршруты), `UiPrefs` (новое поле), `AppTelemetry.android.kt`, `androidMain/.../common/recitation/**`, манифест (удаление разрешений, правила бэкапа), `res/xml/**`, `composeApp/build.gradle.kts` (release, подпись, R8), `composeApp/proguard-rules.pro`, `.gitignore`, `play_store_assets/privacy-policy-*.md`, `docs/RELEASE.md` (новый). `commonMain` не трогай.

## Что сделать

### 1. Убрать распознавание чтения (P0)
- `RECORD_AUDIO` из манифеста. `RecitationRecognizer.android.kt` → заглушка «недоступно» (`expect` в общем коде остаётся — его использует iOS). Проверь, что ни один Android-экран нового UI не ведёт к распознаванию и что в настройках/онбординге нет упоминаний микрофона.

### 2. Аналитика с согласием (P0)
- Решение владельца: по умолчанию **включена**, раскрытие — в онбординге (сделает этап C), тумблер — в настройках.
- `UiPrefs.analyticsEnabled` (по умолчанию `true`). `AppTelemetry.android`: при инициализации и при смене — `setAnalyticsCollectionEnabled` и `setCrashlyticsCollectionEnabled`; при выключенной — `logEvent`/`logScreen`/`logError` ничего не отправляют. Дополнительный метод `actual object` можно объявить только в android-коде.
- Рекламный ID: в манифесте `<uses-permission android:name="com.google.android.gms.permission.AD_ID" tools:node="remove"/>`, `google_analytics_adid_collection_enabled=false`, `google_analytics_default_allow_ad_personalization_signals=false`. Проверь итоговый манифест (`composeApp/build/intermediates/merged_manifest/...` после `processDebugMainManifest`): `AD_ID` нет.
- Пройди все вызовы `AppTelemetry.logEvent/logError` (`grep -rn "AppTelemetry\." composeApp/src`) и убедись, что в параметры не попадает свободный текст (заметки, поиск, сообщения исключений с данными пользователя). Если попадает — убери на Android-стороне (в `AppTelemetry.android` обрезай/отбрасывай такие параметры), общий код не трогай.
- Настройки: новый раздел «Конфиденциальность» перед «О приложении»: тумблер «Анонимная статистика и отчёты о сбоях» с описанием «Помогает находить ошибки. Без рекламы и личных данных», строки «Политика конфиденциальности» и «Источники» (см. ниже).

### 3. Экран «Источники» (P0)
- Новый маршрут `Dest.Sources`, вход из настроек; общий стиль экрана настроек (`QhLargeTopBar`, карточки-разделы, переход как у остальных экранов настроек).
- Содержание (каждый пункт — название, автор/переводчик, источник со ссылкой, условия, если известны):
  - Текст Корана: Tanzil Project, Uthmani (`tanzil.net`), получен через Islamic Network / `alquran.cloud`. Условие Tanzil: текст не изменяется, указывается источник со ссылкой.
  - Переводы: ru — Эльмир Кулиев (`ru.kuliev`); en — Saheeh International (`en.sahih`); tg — Абдулмуҳаммад Оятӣ (`tg.ayati`); tr — Elmalılı Hamdi Yazır (`tr.yazir`); uz — Muhammad Sodiq Muhammad Yusuf (латиница, через `api.quran.com` — меняет A1).
  - Названия сур: `api.quran.com` (en, ru, tr); для tg и uz — составлены командой приложения.
  - Аудио: Islamic Network CDN (`cdn.islamic.network`); чтецы — Мишари Аль-Афаси, Махмуд Халиль аль-Хусари, Мухаммад Сиддик аль-Минышави, Абдуль-Басит Абдус-Самад (выбор добавляет A2).
  - Шрифты: из `composeResources/font/NOTICE.md` (арабские) и Manrope (OFL).
  - Библиотеки с открытым кодом: короткий список основных (Kotlin, Compose Multiplatform, AndroidX, Room, Ktor, Koin, Media3, Firebase SDK) с лицензиями.
- Данные — в одном файле (`SourcesData.kt`), чтобы их было легко поправить. Тексты интерфейса — 5 языков (ru, en, tg, uz, tr); имена и названия источников не переводятся.

### 4. Политика конфиденциальности (P0)
- Перепиши `play_store_assets/privacy-policy-ru.md` и `-en.md` **под фактическое поведение**: нет аккаунтов; прогресс, заметки и настройки хранятся на устройстве; системное резервное копирование Android в аккаунт Google пользователя (если включено на устройстве); сетевые запросы к `api.alquran.cloud`, `api.quran.com`, `cdn.islamic.network` (видят IP-адрес); Firebase Analytics и Crashlytics — что собирается (события использования, модель устройства, версия ОС, отчёты о сбоях, идентификатор установки приложения), без рекламного ID, можно выключить в настройках; микрофон не используется; уведомления — только если включены; дети; удаление данных (удалить приложение / очистить данные); изменения политики; дата вступления в силу. Без обещаний «облачных функций» и Apple-распознавания.
- Контакт разработчика: владелец ещё не дал e-mail. Поставь **одну** метку `{{CONTACT_EMAIL}}` в каждом файле и в коде (константа рядом с `PrivacyPolicyUrl`), перечисли их в отчёте.
- Экран «Конфиденциальность» в приложении (`Dest.Privacy`, вход из нового раздела настроек): 6–8 коротких пунктов о главном (5 языков) и кнопка «Полный текст», которая открывает `PrivacyPolicyUrl`, если он задан (сейчас `null` — тогда кнопки нет). Строку политики в разделе «О приложении» убери: она теперь в «Конфиденциальности».

### 5. Резервное копирование Android
- `android:fullBackupContent="@xml/backup_rules"` (API ≤ 30) и `android:dataExtractionRules="@xml/data_extraction_rules"` (API 31+): включить базу данных и настройки (`sharedpref`), исключить кэши и скачанное аудио (проверь, где лежит кэш аудио: `noBackupFilesDir` исключается и так). Облачный бэкап и перенос на новое устройство — одинаково.

### 6. Гигиена релиза
- `.gitignore`: `*.jks`, `*.keystore`, `*.aab`, `*.apk`, `composeApp/release/`. **Файлы `keystoreq.jks` и `composeApp-release.aab` из git не удаляй** — это сделает оркестратор в основном checkout (иначе слияние удалит ключ с диска владельца).
- Подпись релиза из `local.properties` (или переменных окружения для CI): `release.storeFile`, `release.storePassword`, `release.keyAlias`, `release.keyPassword`. Если ключей нет — релиз собирается неподписанным, конфигурация Gradle не падает. Пароли нигде не пиши.
- R8: `isMinifyEnabled = true`, `isShrinkResources = true`, `proguard-android-optimize.txt` + `composeApp/proguard-rules.pro`. Правила по факту предупреждений R8: kotlinx.serialization (маршруты навигации `Dest` — `@Serializable`, проверь, что их сериализаторы сохраняются), Room, Ktor (`-dontwarn` для отсутствующих классов), Koin, Firebase, Compose Resources. Mapping для Crashlytics выгружается плагином по умолчанию — не ломай это.
- `./gradlew :composeApp:assembleRelease` проходит. Запуск релиза на устройстве проверит оркестратор — опиши в отчёте, на что смотреть (что может отвалиться при обфускации).
- `docs/RELEASE.md`: как настроить `local.properties` для подписи, как собрать AAB, что делать с ключом, если репозиторий публичный (сменить upload key через Play Console, вычистить ключ из истории git — **только решением владельца**).

### 7. Мёртвые настройки (P1)
- Если в Android-настройках или ридере есть «Стиль карточки аята» или «режим экзамена» — убери строки (значения в хранилище оставь).

## Проверка

1. `./gradlew :composeApp:assembleDebug`, `./gradlew :composeApp:assembleRelease`, `./gradlew :composeApp:testDebugUnitTest`.
2. Итоговый манифест: нет `RECORD_AUDIO`, нет `AD_ID`; есть правила бэкапа.
3. Unit-тест: при выключенной аналитике события не уходят (через фейковый приёмник или флаг).

## Отчёт

Файлы; новые маршруты и где вход; что R8 потребовал (правила) и размер release APK до/после; места `{{CONTACT_EMAIL}}`; что проверить на устройстве в релизной сборке. Один коммит `redesign: privacy, sources and release hygiene (A3)` с последней строкой `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`; путь worktree и ветка.
