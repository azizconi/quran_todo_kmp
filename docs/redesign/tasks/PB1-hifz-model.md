# Задача B1 — модель заучивания: отрывки, лестница, манзиль, журнал

Исполнитель: Sonnet. Ветка: твой worktree от `redesign/android`. Работаешь один, **эмулятор твой** (нужен для инструментального теста миграции). После тебя на этой модели параллельно строят B2 (экран «Урок») и B3 (главный, «Мой план», лист суры) — им нужны твой API и `API.md`, а не UI.

## Окружение

1. Worktree может быть создан от `master`: первым делом `git merge --ff-only redesign/android`.
2. `cp /Users/azizjon/projects/quran_todo_kmp/local.properties .`
3. Собирай только `./gradlew` из корня worktree. Основной checkout не трогай.
4. Эмулятор `DoruIman_API_35` (`~/Library/Android/sdk/platform-tools/adb devices`).

## Прочитай сначала

1. `docs/redesign/PRODUCT_PLAN.md` — 2.4, 3.1, 3.2, 6 «Этап B». **Где этот бриф уточняет план — прав бриф.**
2. `docs/redesign/API.md` — правило DI (общие VM — синглтоны Koin, `get()`, никогда `koinViewModel`).
3. Код:
   - `commonMain/.../data/database/**`: `QuranTodoDatabase` (v10), `Migrations.kt`, `ProgressRestoreDao` (пример `@Transaction`), сущности `todo/*`, `quran/Quran.kt` (`AyahEntity`: `number` — глобальный, `numberInSurah`, `page`).
   - `androidInstrumentedTest/.../MigrationTest.kt` (подсказка внутри, как добавить 10→11).
   - `data/repository/QuranRemoteRepositoryImpl.kt` (`ensureInstalled` — вшитый текст ставится в БД), `data/bundled/QuranFacts.kt`.
   - Как пишутся статусы сейчас: `presentation/home/HomeViewModel.kt` (`completeReview`, SM-2, слабые аяты), `presentation/surah/SurahViewModel.kt` (`updateAyahStatus`, `syncSurahStatusInternal`), `common/settings/FeatureSettings.kt` (слабые аяты: ключ `"сура:глобальный_номер"` в prefs).
   - `di/CommonModule.kt`, `QuranTodoApplication.kt`.

## Зона

- `commonMain`: новые сущности и `HifzDao`, `QuranTodoDatabase` (v11 + новые сущности/DAO), `Migrations.kt`, `schemas/.../11.json`, новый пакет `tj.app.quran_todo.hifz` (модель, планировщик, перенос, репозиторий), `di/CommonModule.kt` (регистрация), `QuranRemoteRepository` — **аддитивно** публичный `ensureQuranInstalled()`.
- `androidMain`: `QuranTodoApplication.kt` (запуск переноса), при необходимости маленький `HifzStartup.kt`.
- Тесты: `commonTest/.../hifz/**`, `androidInstrumentedTest/.../MigrationTest.kt`.
- `docs/redesign/API.md` — новый раздел «Хифз».
- **Не трогай UI** (`ui/**`), `HomeViewModel`, `SurahViewModel`, `StatsViewModel`: на новый API их переводят B2/B3/D2. После каждого изменения `commonMain` — `./gradlew :composeApp:compileKotlinIosSimulatorArm64` (если KSP упадёт на «unbound generics» — это устаревший кэш, `--rerun-tasks`).

## Модель

Единица заучивания — **отрывок**: непрерывный диапазон аятов одной суры (номера **в суре**). Статус аята и суры — производные от отрывков. Все даты — `epochDay` (календарный день в часовом поясе устройства, kotlinx-datetime).

### Таблицы (миграция 10 → 11, только `CREATE TABLE`/`CREATE INDEX`)

```
hifz_plan      id (PK, одна строка = активный план; id = 1), scope (JUZ_30 | JUZ_29 | FROM_START | CUSTOM),
               surahOrder TEXT (CSV номеров сур — без TypeConverter), dailyNewAyahs INT (1..20),
               targetEpochDay INT?, startedEpochDay INT, manzilDays INT (3..30, по умолчанию 7)
hifz_passage   id (PK auto), surah, startAyah, endAyah, learnedEpochDay, stage (TEXT, enum ниже),
               nextDueEpochDay INT? (null у OLD), lastReviewedEpochDay INT?, lapses INT
               индекс (surah), индекс (stage, nextDueEpochDay)
passage_hard   passageId (FK → hifz_passage ON DELETE CASCADE), ayahInSurah, clearsAfter INT; PK (passageId, ayahInSurah)
review_log     id (PK auto), passageId INT? (без FK — история переживает удаление отрывка), surah, startAyah, endAyah,
               kind (NEW | RECENT | OLD | CHECK), grade (FORGOT | HARD | GOOD | EASY), epochDay, timestamp
               индекс (epochDay)
hifz_meta      key TEXT PK, value TEXT  — служебное (метка переноса и т. п.)
```

Нет плана — нет строки в `hifz_plan`. Старые таблицы (`surah_todo`, `ayah_todo`, `ayah_review`) **не удаляются и не меняются схемой**.

Порядок: сначала сущности и `version = 11`, сборка (Room пишет `11.json`), затем SQL миграции **дословно из `createSql` в `11.json`**, затем тест `migrate10To11` (`runMigrationsAndValidate(11, AllMigrations.toList())` + строки v10 целы). Обнови KDoc `Migrations.kt` и `MigrationTest`.

### Этапы и лестница (чистые функции, `hifz/schedule`)

`PassageStage`: `R1, R3, R7, R14, R30, OLD` — «следующий раз через N дней» (интервалы `[1, 3, 7, 14, 30]`). Оценка повторения отрывка в лестнице на этапе k:

| Оценка | Результат |
|---|---|
| EASY | этап k+2 (дальше R30 → OLD) |
| GOOD | этап k+1 (после R30 → OLD) |
| HARD | тот же этап, срок = сегодня + интервал(k) |
| FORGOT | R1 (завтра), `lapses + 1` |

Новый выученный отрывок: этап `R1`, срок завтра, `learnedEpochDay` = сегодня (даже при оценке FORGOT — тогда ещё и трудные аяты). Перешёл в `OLD` → `nextDueEpochDay = null`, `lastReviewedEpochDay` = сегодня.

Повторение `OLD` (манзиль): GOOD/EASY → остаётся OLD, `lastReviewed` = сегодня; HARD → `R3`; FORGOT → `R1`, `lapses + 1`.

**Трудные аяты:** при HARD/FORGOT отмеченные пользователем аяты получают `clearsAfter = 2` (уже трудные — снова 2). Каждое GOOD/EASY повторение отрывка уменьшает счётчик у всех его трудных аятов; 0 → строка удаляется. Если отрывок разрезан или слит (ниже) — флаги переходят к частям по номеру аята.

### Манзиль — круг старого

Без курсора, устойчиво к новым отрывкам:
- вес отрывка = число символов его арабского текста (`LENGTH(text)` по аятам; см. «Метрики»);
- дневная квота = `ceil(сумма весов всех OLD / manzilDays)`;
- порция дня: сначала то, что уже повторено **сегодня** как OLD (по `review_log`, засчитывается в квоту), затем непройденные OLD по приоритету: `lastReviewed` (null — самые старые) по возрастанию, при равенстве — порядок мусхафа (сура, аят); берём, пока квота не набрана (отрывок, перешагнувший квоту, берётся целиком). Внутри урока порция — по мусхафу.
- Квота набрана → блок «Старое» закрыт. Так каждый отрывок повторяется не реже ~раза в `manzilDays` дней, и порция не «прыгает» в течение дня.

**Слияние старого:** при переходе отрывка в OLD слей его с соседними OLD-отрывками той же суры (смежные диапазоны), пока вес слитого ≤ `MAX_OLD_PASSAGE_CHARS` (≈ одна страница мусхафа; посчитай по вшитому тексту среднюю длину страницы и вынеси константу). `lastReviewed` = меньший, `lapses` = сумма, трудные аяты переходят.

### «Новое» на сегодня

- План задаёт очередь сур `surahOrder`. **Текущая сура** — первая в очереди, у которой есть непокрытые аяты.
- Отрывок: от первого непокрытого аята, `dailyNewAyahs` аятов, не переходя покрытый аят и конец суры. Если в суре ничего не покрыто и её длина ≤ `dailyNewAyahs × 1.5` — вся сура. Если после отрывка в суре остаётся ≤ `dailyNewAyahs / 2` непокрытых подряд до конца — забираем их тоже.
- Если сегодня в журнале уже есть `NEW` — блок закрыт (сделано), второй новый не предлагаем.
- Плана нет или все суры плана покрыты — блока нет (и это отдельный признак «план выполнен»).

### Бюджет урока

- Оценка времени: `секунды = вес / CHARS_PER_SECOND × множитель + накладные`, множитель: новое ×7 (3× по аяту, 2× отрывком, чтение, вспоминание), повторение ×2; накладные 15 с на карточку. `CHARS_PER_SECOND` откалибруй по вшитому тексту так, чтобы аль-Фатиха (с басмалой) ≈ 50 с. Константы — в одном месте, с комментарием.
- Лимит урока `LESSON_BUDGET_MINUTES = 25`. Заполнение: **недавнее** к сроку (самое просроченное первым) → **старое** (порция манзиля) → **новое**. Не влезло — переносится; признак `catchingUp` и число отложенных. Новое откладывается целиком, если не влезает; блок, который уже начат сегодня (есть записи в журнале), не урезается.
- «Сделано сегодня» — по `review_log` за сегодня: отрывок, повторённый сегодня в RECENT/OLD, остаётся в уроке как выполненный (даже если FORGOT перенёс его на завтра).

### Прогноз и серия

- Остаток плана = непокрытые аяты сур плана. Без срока: дата окончания = сегодня + `ceil(остаток / dailyNewAyahs)` (если новое сегодня уже сделано — со следующего дня).
- Со сроком: нужный темп = `ceil(остаток / дней до срока)`; отставание = ожидаемое покрытие по прямой от `startedEpochDay` до срока минус фактическое (только если > 0).
- Серия дней: дни с хотя бы одной записью в `review_log` подряд, заканчивая сегодня или вчера (сегодня ещё не занимался — серия жива).

### Метрики текста

Нужны для веса и деления на отрывки: по каждому аяту `(surah, ayahInSurah, page, chars)`. Одна проекция-запрос к таблице `ayahs` (там только арабское издание), кэш в памяти репозитория (6236 записей). Перед чтением — `QuranRemoteRepository.ensureQuranInstalled()` (вынеси приватный `ensureInstalled(force = false)` в интерфейс аддитивно; вшитый текст ставится оффлайн).

## Перенос данных существующих пользователей (один раз)

Чистая функция `LegacyImport` (вход — строки старых таблиц, слабые ключи, метрики, сегодня; выход — отрывки, трудные аяты, план) + выполнение в репозитории **одной транзакцией**, метка `hifz_meta.legacy_import = done`. Запуск — из `QuranTodoApplication` в фоне (app-scope корутина, ошибки логируются через `AppTelemetry.logError` без свободного текста; при ошибке метки нет — повторится при следующем запуске). Повторный запуск при наличии метки — ничего не делает.

Правила:
1. `surah_todo` LEARNED → вся сура покрыта отрывками `OLD`, `lastReviewed = null`.
2. `ayah_todo` LEARNED (сура не LEARNED) → подряд идущие аяты → отрывки `OLD`.
3. `ayah_todo` LEARNING и аяты, у которых есть `ayah_review`, но нет статуса → отрывки в лестнице: этап из `ayah_review.intervalIndex` (0→R1, 1→R3, 2→R7, 3→R14, 4→R30, ≥5→OLD), срок — день `nextReviewAt` (может быть в прошлом — значит просрочено); без `ayah_review` — R1 со сроком сегодня. Отрывок из нескольких аятов берёт минимальный этап и минимальный срок.
4. Нарезка: подряд идущие аяты одного класса режутся **по страницам мусхафа** (аят относится к странице, где начинается); короткие суры, целиком на одной странице, — один отрывок.
5. `ayah_todo.ayahNumber`, `ayah_review.ayahNumber` — **глобальные** номера → в номер в суре через `QuranFacts.firstAyahNumber`. Слабые ключи `"s:n"`: если `n` в глобальном диапазоне суры — глобальный, иначе если `1..ayahCount(s)` — номер в суре, иначе отбросить. Слабый аят внутри отрывка → `passage_hard` (`clearsAfter = 2`); вне отрывков — отбросить. После успешного переноса слабые ключи в prefs очисти (`UserSettingsStorage.saveWeakAyahKeys(emptySet())`).
6. План: если есть суры «Заучиваю» (`surah_todo` LEARNING или частично покрытые по `ayah_todo`) — создать `CUSTOM`-план из них: все в 67..114 → по убыванию номера (от короткой к длинной), иначе по возрастанию; `dailyNewAyahs` = `UserSettingsStorage.getDailyGoal()` в 1..20, иначе 5; `startedEpochDay` = сегодня. Нет таких сур — плана нет.
7. `ayah_review` и SM-2 (`ReviewStateStore`) больше не используются Android-путём; не удаляй.

## Статусы (производные)

- Аят: `LEARNED`, если покрыт любым отрывком; иначе нет статуса. Трудный — если есть в `passage_hard`.
- Сура: `LEARNED` — покрыты все аяты; `LEARNING` — покрыта часть **или** это текущая сура плана; иначе нет статуса.

### Зеркало в старые таблицы

Пока прогресс (D2), ридер и главный (B2/B3) не переведены, старые экраны читают `surah_todo`/`ayah_todo`. Каждое действие репозитория в **той же транзакции** пересчитывает для затронутых сур: `ayah_todo` — строки LEARNED ровно для покрытых аятов (глобальные номера, остальные строки этой суры удаляются); `surah_todo` — по производному статусу суры (нет статуса → строка удаляется). Изменение плана меняет «текущую суру» → пересчитай обе суры (старую и новую текущую). `ayah_review` не зеркалится.

## Репозиторий `HifzRepository` (commonMain, Koin `single`)

Все записи — под одним `Mutex` репозитория и **одной транзакцией** Room на действие (KMP: `@Transaction`-метод DAO с готовым набором изменений, как `ProgressRestoreDao`, или `useWriterConnection { immediateTransaction { … } }`). Вычисления — чистыми функциями.

Чтение (Flow, пересчитываются при изменении таблиц и смене дня):
- `lesson(today): Flow<LessonPlan>` — блоки «Новое» (отрывок, оценка, сделано/отложено), «Недавнее» и «Старое» (элементы: `passageId`, сура, диапазон, этап, трудные аяты, сделано, оценка), общая и оставшаяся оценка в минутах, `catchingUp` + число отложенных, `isComplete` (всё, что было в уроке, сделано), `hasPlan`, `planFinished`, «завтра»: следующий новый отрывок и число отрывков к сроку завтра.
- `plan(): Flow<HifzPlan?>`, `forecast(today): Flow<PlanForecast?>`, `streak(today): Flow<Int>`.
- `surahStatuses(): Flow<Map<Int, SurahHifzStatus>>`, `surahCoverage(surah): Flow<SurahCoverage>` (покрытые аяты, трудные, отрывки суры).
- `reviewLog(fromDay, toDay)` — для D2 и итога урока.

Действия (`suspend`, каждое — одна транзакция + зеркало + запись в журнал, где уместно):
- `completeNew(surah, start, end, grade, hardAyahs, today)` — создаёт отрывок (R1) + `NEW` в журнал. Перекрытие с покрытыми аятами — отказ (исключение/результат), не дубликат.
- `review(passageId, grade, hardAyahs, today)` — лестница или манзиль по этапу отрывка; журнал `RECENT` или `OLD`; слияние при переходе в OLD.
- `logCheck(surah, start, end, grade, hardAyahs, today)` — режим «Проверь себя»: только журнал `CHECK` и трудные аяты у покрывающих отрывков; этапы не меняются.
- `markKnown(surah, start = 1, end = последний)` — непокрытые аяты → OLD-отрывки (нарезка по страницам), `lastReviewed = null`.
- `forget(surah, start = 1, end = последний)` — убирает аяты из отрывков (режет отрывки, остатки сохраняют этап/сроки/трудные).
- План: `setPlan(scope, surahOrder, dailyNewAyahs, targetEpochDay?, manzilDays)`, `learnNext(surah)` («Учу»: нет плана → `CUSTOM` [сура], темп 5; есть → сура ставится первой в очереди, `scope = CUSTOM`), `clearPlan()`.
- Пресеты: `HifzPresets.order(scope)`: JUZ_30 = 114 → 78, JUZ_29 = 77 → 67, FROM_START = 1 → 114.
- `importLegacyIfNeeded()`.

**Смена дня:** `EpochDayTicker` (commonMain): `StateFlow<Int>` текущего дня, сам переключается в полночь (delay до следующей полуночи по часовому поясу устройства) и метод `refresh()` (B3 вызовет на ON_RESUME).

Аналитика — не добавляй (это B2).

## Проверка

1. `./gradlew :composeApp:assembleDebug`, `./gradlew :composeApp:testDebugUnitTest`, `./gradlew :composeApp:compileKotlinIosSimulatorArm64`.
2. Unit-тесты в `commonTest/.../hifz/` (не меньше, чем на каждое правило выше): лестница (все оценки на всех этапах, переход в OLD), трудные аяты (сброс через 2, переход при разрезе/слиянии), манзиль (квота, приоритет, стабильность порции в течение дня после частичного прохождения, новый OLD-отрывок в середине круга, покрытие всех за `manzilDays` дней при ежедневном прохождении — симуляция 30 дней), слияние, «новое» (короткая сура целиком, хвост, покрытый аят посреди суры, конец плана, второе новое в тот же день не предлагается), бюджет (просрочки → `catchingUp`, порядок заполнения, начатый блок не режется), прогноз (без срока, со сроком, отставание), серия, `forget` (разрез), перенос (все правила 1–6, глобальные и «внутрисурные» слабые ключи, нарезка по страницам — на метриках-фикстурах), производные статусы и зеркало (на чистой функции «строки старых таблиц для суры»).
3. Инструментальный тест на эмуляторе: `./gradlew :composeApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=tj.app.quran_todo.data.database.MigrationTest` — все тесты зелёные, включая `migrate10To11`. Добавь тест репозитория на реальной Room-базе (in-memory, тот же драйвер): перенос с фикстурой старых строк → отрывки/план/зеркало; `review` + `completeNew` пишут журнал; второй `importLegacyIfNeeded` ничего не меняет.
4. Ручная проверка на эмуляторе: поставь сборку **поверх** текущей (`adb install -r`, данные сохраняются), до этого в старом UI отметь пару сур/аятов; после запуска — в `adb shell run-as tj.app.quran_todo sqlite3`… (или через отладочный лог) убедись, что отрывки созданы и старые экраны показывают то же, что до обновления.

## Отчёт

Файлы; решения, где отступил от брифа, и почему; откалиброванные константы (`CHARS_PER_SECOND`, `MAX_OLD_PASSAGE_CHARS`) и как получены; число тестов; что проверено на эмуляторе. В `API.md` раздел «Хифз»: модель, этапы, все функции репозитория с контрактом, `LessonPlan`, `EpochDayTicker`, правило «UI пишет только через `HifzRepository`», что старые таблицы — зеркало. Один коммит `redesign: hifz passage model, scheduler and legacy import (B1)` с последней строкой `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`; путь worktree и ветка.
