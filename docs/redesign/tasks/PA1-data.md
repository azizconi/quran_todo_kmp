# Задача A1 — фундамент данных

Исполнитель: Sonnet. Ветка: твой worktree от `redesign/android`. Параллельно идут A2 (звук) и A3 (приватность и релиз). Эмулятор занят A2 — **не используй его**.

## Окружение worktree

1. Worktree может быть создан от `master`: первым делом `git merge --ff-only redesign/android`.
2. `cp /Users/azizjon/projects/quran_todo_kmp/local.properties .`
3. Собирай только `./gradlew` из корня worktree. Основной checkout не трогай.

## Прочитай сначала

1. `docs/redesign/PRODUCT_PLAN.md` — разделы 0, 2.1, 2.7 («Гонки состояния»), 3.1 (куда пойдёт схема дальше), 6 «Этап A».
2. `docs/redesign/API.md` — правило про общие ViewModel и `UiPrefs`.
3. Код: `commonMain/.../data/**`, `di/CommonModule.kt`, `domain/repository/**`, `presentation/home/HomeViewModel.kt`, `androidMain/.../database/androidDatabaseBuilder.kt`, `iosMain/.../database/DatabaseBuilder.kt`, `androidMain/.../ui/reader/Bismillah.kt`, `ui/common/SurahNames.kt`.

## Важно про iOS

`data/**`, `domain/**` и `HomeViewModel` общие с iOS. iOS-интерфейс (старый общий UI) не должен сломаться и не должен поменять поведение. После изменений в `commonMain` обязательно: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`. Если эта задача на машине вообще не собирается — проверь это на чистом `redesign/android` и напиши в отчёте.

## Зона

`commonMain/.../data/**`, `domain/**` (аддитивно), `di/CommonModule.kt`, `common/utils/**`, `presentation/home/HomeViewModel.kt`, `composeResources/files/**` (новое), `composeApp/schemas/**`, `scripts/**` (новое), `androidMain/.../database/**`, `iosMain/.../database/**`, `ui/common/SurahNames.kt`. В `ui/reader/**` — только плейсхолдер перевода (п. 5). Сборочные файлы — только если нужен `room-testing` или настройка тестов.

## Что сделать

### 1. Схема БД и миграции (P0)
- `exportSchema = true`, сгенерируй `composeApp/schemas/.../10.json` и закоммить.
- **Выясни, какая версия БД сейчас у пользователей Google Play.** В Play стоит `versionCode 7` / `1.0.2`; в истории git есть коммиты с `composeApp/release/composeApp-release.aab`. Найди коммит релиза (`git log --oneline -- composeApp/release/composeApp-release.aab`, `git log -S "versionCode = 7"`) и версию `@Database(version = …)` на нём. Если она меньше 10 — напиши настоящие миграции до 10 (разница схем — из истории `entity/**` и `schemas/9.json`), чтобы обновление из Play не падало и не теряло прогресс.
- `data/database/Migrations.kt`: `val AllMigrations: Array<Migration>` (может быть пустым, если в Play уже 10) и подключение в общем `getRoomDatabase(...)`, чтобы обе платформы получали одно и то же. **Никакого** `fallbackToDestructiveMigration`.
- Тест миграции: инфраструктура `MigrationTestHelper` (Room 2.7, KMP), на которой B1 потом проверит 10 → 11. Если найдена реальная миграция — тест на неё. Если локальный JVM-тест невозможен — инструментальный (`androidInstrumentedTest`); запустить его ты не сможешь (эмулятор занят), но он должен компилироваться. Напиши в отчёте команду запуска.

### 2. Вшитый текст Корана (P0)
- Скрипт `scripts/build_quran_asset.py` (только stdlib) качает `https://api.alquran.cloud/v1/quran/quran-uthmani` — **тот же текст, что сейчас**, — и пишет компактный файл `composeApp/src/commonMain/composeResources/files/quran/quran-uthmani.tsv`: строки сур (номер, арабское имя, englishName, englishNameTranslation, revelationType) и аятов (глобальный номер, сура, номер в суре, juz, hizbQuarter, manzil, page, ruku, sajda id/recommended/obligatory, text). Формат опиши в шапке файла скрипта. Закоммить и скрипт, и файл.
- Очистка при генерации: убрать BOM `U+FEFF` (он есть в 1:1). **Бисмиллях в первом аяте не трогай:** старый iOS-интерфейс показывает его прямо из текста аята, Android отделяет его сам (`ui/reader/Bismillah.kt`).
- Загрузка: `BundledQuranSource` в `data/**` читает файл через `Res.readBytes("files/quran/quran-uthmani.tsv")`, парсит и проверяет 114 сур и 6236 аятов. `QuranRemoteRepositoryImpl.getCompleteQuran` (или новый репозиторий за тем же интерфейсом) берёт текст из БД, а если БД пуста, неполна (≠ 6236 аятов) или в 1:1 есть BOM — пишет вшитый текст в БД **одной транзакцией** (`@Transaction`: очистить таблицы текста, вставить edition, суры, аяты; помни про `ON DELETE CASCADE` на `ayahs`). Сеть для текста больше не нужна: убери запрос `quran/quran-uthmani` из пути запуска.
- Пользовательские таблицы (`surah_todo`, `ayah_todo`, `ayah_note`, `ayah_review`) не имеют внешних ключей на текст и не должны пострадать. Проверь это.
- Первый запуск без сети должен давать полный список сур и рабочий ридер (арабский текст). Холодный старт не должен заметно замедлиться: парсинг — на `Dispatchers.Default`, не на главном потоке. В отчёте — замер разбора и записи (лог с временем в debug).

### 3. Сеть
- `createHttpClient`: убери `LogLevel.ALL`. В релизе — без логов, в debug — `LogLevel.INFO` или `HEADERS` (не тела). Флаг передай параметром из платформенного DI.

### 4. Переводы по суре (P1)
- `getAyahTranslations` качает **одну суру**: `GET /v1/surah/{n}/{edition}` (alquran.cloud), а не весь Коран. Сигнатуру интерфейса не ломай (iOS), новые параметры — со значениями по умолчанию.
- Неудачу запоминай: тот же (сура, язык) не перезапрашивается чаще раза в 30 с без явного «Повторить» (параметр `force`).
- **Узбекский:** `uz.sodik` на alquran.cloud — кириллица, а интерфейс латинский. Найди латинский перевод Мухаммада Содика Мухаммада Юсуфа на quran.com v4 (`https://api.quran.com/api/v4/resources/translations?language=uz`, затем `https://api.quran.com/api/v4/quran/translations/{id}?chapter_number={n}`), проверь, что текст латиницей, вычисти HTML-сноски (`<sup …>…</sup>`, теги). Для UZ используй его; для остальных языков источник прежний (`editionForLanguage`).
- Кэш переводов в Room прежний. Если в кэше UZ лежит кириллица (старые данные) — замени латиницей при следующем открытии (например, по признаку кириллицы в тексте).
- Плейсхолдер в ридере Android, если перевод не пришёл: строка «Перевод не загружен · Повторить» (5 языков) на месте перевода у первого аята или над списком, «Повторить» вызывает загрузку с `force = true`. Минимальный хук в `ui/reader/ReaderViewModel.kt` и одном компоненте.

### 5. Названия сур для tg и uz (P1)
- quran.com возвращает для `language=tg` и `language=uz` английские названия. Вшей `composeResources/files/quran/chapter_names_tg.json` (кириллица: транслитерация и смысл, например «Фотиҳа» / «Кушоянда») и `chapter_names_uz.json` (латиница: «Fotiha» / «Ochuvchi»). Для этих языков `getChapterNames` возвращает вшитое без сети; для en/ru/tr — как сейчас.
- Составь сам, аккуратно и единообразно (общепринятые таджикские и узбекские написания). Список всех 114 строк для проверки носителем — в `docs/redesign/review/chapter-names-tg-uz.md` (таблица: №, арабское, tg, uz).
- Проверь, что `SurahNames.displayName` на Android показывает их правильно для tg и uz (строка списка, шапка ридера, мини-плеер).

### 6. Гонки состояния в `HomeViewModel` (P1)
- Все `_uiState.value = _uiState.value.copy(...)` → `_uiState.update { it.copy(...) }`. Поведение не меняется.
- Восстановление из копии (`restoreProgressFromCloud` и что оно вызывает): запись в БД должна быть внутри `try/catch` и одной транзакцией (метод DAO с `@Transaction` или `useWriterConnection`/`immediateTransaction`), ошибка → состояние ошибки, а не падение. Формат копии не меняй — его переделает отдельная задача.

## Проверка

1. `./gradlew :composeApp:assembleDebug`, `./gradlew :composeApp:testDebugUnitTest`, `./gradlew :composeApp:compileKotlinIosSimulatorArm64`.
2. Unit-тесты (`androidUnitTest` или `commonTest`): разбор вшитого файла (114/6236, нет BOM, 1:1 начинается с «بِسْمِ», у 2:1 сохранён префикс Бисмиллях как в API, саджда у 15 аятов); чистка HTML в узбекском переводе; правило «не чаще раза в 30 с».
3. `curl`-проверки API, которые ты делал (UZ id, формат ответа), — в отчёт.

## Отчёт

Файлы; версия БД в Play и как ты это установил; какие миграции; размер файла текста (сырой и в APK: сравни размер `composeApp-debug.apk` до и после); время разбора и записи; UZ-перевод (id, переводчик); что осталось на проверку носителю. Один коммит `redesign: data foundation (A1)` с последней строкой `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`; путь worktree и ветка.
