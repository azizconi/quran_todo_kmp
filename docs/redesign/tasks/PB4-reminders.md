# Задача B4 — напоминание об уроке

Исполнитель: Sonnet. Ветка: твой worktree от `redesign/android`. Работаешь один, **эмулятор твой**.

## Окружение

1. Worktree может быть создан от `master`: первым делом `git merge --ff-only redesign/android`.
2. `cp /Users/azizjon/projects/quran_todo_kmp/local.properties .`
3. Собирай только `./gradlew` из корня worktree. Основной checkout не трогай.
4. Эмулятор `DoruIman_API_35` (`~/Library/Android/sdk/platform-tools/adb`). Два дисплея: скриншот `adb exec-out screencap -d 4619827259835644672 -p > f.png`, затем `sips -Z 1000 f.png`. Скриншоты — в `/private/tmp/claude-501/-Users-azizjon-projects-quran-todo-kmp/3cd90de6-e3fd-4e6e-b61f-ad0156db34a3/scratchpad/pb4/`. `connectedDebugAndroidTest` в конце удаляет приложение.

## Прочитай сначала

1. `docs/redesign/PRODUCT_PLAN.md` — 2.5 («Напоминания»), 6 «Этап B».
2. `docs/redesign/API.md` — «Хифз» (`HifzRepository.lesson`, `LessonPlan`, `EpochDays`), «Звук» (иконка `ic_stat_quran`, каналы уведомлений плеера), правило DI.
3. Код: `androidMain/.../common/reminder/**` (`ReminderScheduler.android.kt`, `ReminderReceiver`), `commonMain/.../common/reminder/ReminderScheduler.kt` (`expect`, его зовёт iOS и старый общий UI), `ui/QuranHifzApp.kt` (сейчас **при каждом запуске выключает напоминания**: `saveReminderEnabled(false)` + `syncDailyReminder`), `MainActivity.kt`, `AndroidManifest.xml`, `ui/settings/**` (разделы, `TransferSettings`), `ui/state/UiPrefs.kt`, `ui/playback/PlaybackService` (как сделан канал и иконка), `data/bundled/BundledChapterNames.kt` / `SurahNames` (названия сур по языку).

## Зона

`androidMain/.../common/reminder/**` (переписать Android-реализацию; `expect` в общем коде не меняй), новые файлы в `ui/reminder/**`, `AndroidManifest.xml` (приёмники, разрешение `RECEIVE_BOOT_COMPLETED`), `ui/state/UiPrefs.kt` (поля), `ui/settings/**` (раздел «Напоминание», перенос в копию), `ui/i18n/ReminderCopy.kt` (новый), `ui/QuranHifzApp.kt` и `MainActivity.kt` (убрать принудительное выключение, диплинк), `res/values*/` (если нужны строки для системы). `commonMain` не трогай.

## Что сделать

### 1. Настройка (P0)

- `UiPrefs.reminderEnabled` (по умолчанию **выкл**) и `UiPrefs.reminderMinuteOfDay` (по умолчанию 20:00). Старое `UserSettingsStorage.isReminderEnabled` на Android больше не читается (оно всё равно принудительно сбрасывалось).
- Настройки → раздел «Напоминание» (после «Заучивание»/«Звук» — выбери место по смыслу): тумблер «Напоминать об уроке», строка «Время · 20:00» (Material3 `TimePicker` в диалоге в стиле приложения), под ними подпись «Не приходит, если урок уже сделан».
- Включение на API 33+ — запрос `POST_NOTIFICATIONS` **в этот момент**. Отказ → тумблер обратно выкл, тост с кнопкой «Открыть настройки» (системные настройки уведомлений приложения). Уведомления или канал выключены в системе → строка-предупреждение в разделе с той же кнопкой.
- Время и включённость переносятся в копию B5 (`TransferSettings`; формат игнорирует неизвестные поля — добавь `reminderEnabled`, `reminderMinuteOfDay`); после импорта включённое напоминание без разрешения не показывается и не ломается.

### 2. Планирование (P0)

- Одно разовое срабатывание на ближайшее «время напоминания» (`AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, …)` — без разрешения на точные будильники), после срабатывания — следующее на завтра. Расчёт ближайшего момента — чистая функция на `java.time` (часовой пояс устройства, переходы на летнее время) + unit-тесты.
- Перепланирование: запуск приложения, смена настройки, `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED` (манифест-приёмник, `exported=false` там, где можно; для системных действий — как требует Android). `LOCALE_CHANGED` — обновить имя канала.
- Канал «Урок дня» (`IMPORTANCE_DEFAULT`), имя и описание — на языке приложения.

### 3. Умный текст и «не присылать, если сделано» (P0)

- Приёмник срабатывания (`goAsync` + корутина, таймаут ~8 с): `HifzRepository.lesson(EpochDays.now()).first()`.
  - `isComplete` → не показывать.
  - Урок есть → заголовок «Урок дня», текст по содержимому: «Сегодня: Ан-Наба 1–5 и 3 повторения · ~12 мин»; только повторения — «Сегодня 3 повторения · ~6 мин»; только новое — «Сегодня: Ан-Наба 1–5 · ~5 мин»; часть сделана — «Осталось: 2 повторения · ~4 мин». Касание → урок (`Dest.Lesson`).
  - Урока нет (нет плана и нечего повторять) и есть позиция чтения (`UiPrefs.lastReadPosition`) → «Продолжить чтение: Аль-Кахф, аят 12» → ридер. Нет ничего → не показывать.
- Названия сур — по языку приложения, как в остальном UI. Тексты — 5 языков (ru, en, tg, uz, tr), с падежами/числами, чистая функция + unit-тесты.
- Малая иконка — `ic_stat_quran` (открытый Коран на подставке; **никаких звёзд** — владелец запретил звёзды в любом виде). Действие «Через час» — отложить на 60 минут (одно разовое срабатывание), не трогая завтрашнее.
- Аналитика (без свободного текста): `reminder_enabled` (`hour`), `reminder_shown` (`kind` = lesson|reading), `reminder_opened`, `reminder_snoozed`.

### 4. Диплинк (P0)

- `PendingIntent` на `MainActivity` с явным действием/экстрой (`open=lesson` или `open=reader` + сура/аят). Холодный старт и уже открытое приложение (`onNewIntent`, `launchMode` проверь) → поверх «Главного» открывается урок/ридер; назад — на «Главный». Если не пройден онбординг — просто старт приложения.
- Повторный поворот экрана не открывает урок второй раз (экстра потребляется).

### 5. Старое

- Убери из `QuranHifzApp` принудительное выключение и вызов старого `ReminderScheduler.syncDailyReminder`. Android-`actual` старого `expect` оставь рабочей заглушкой, которая делегирует новому планировщику или ничего не делает (его больше никто не зовёт на Android) — объясни выбор в KDoc.

## Проверка

1. `./gradlew :composeApp:assembleDebug`, `./gradlew :composeApp:testDebugUnitTest`, `./gradlew :composeApp:assembleRelease`.
2. Unit-тесты: ближайший момент (до/после времени сегодня, переход на летнее время, смена пояса), тексты всех видов на 5 языках, «сделано → не показывать».
3. На эмуляторе, со скриншотами:
   - включение в настройках → системный запрос разрешения → разрешить; отказ → тумблер выключен, тост;
   - время на 2–3 минуты вперёд → уведомление приходит (`adb shell dumpsys notification --noredact | grep tj.app` и скриншот шторки), текст соответствует уроку, касание открывает урок, назад — главный;
   - урок сделан → в назначенное время уведомления нет (лог/`dumpsys`);
   - «Через час» → уведомление убрано, будильник через час (`adb shell dumpsys alarm | grep -A3 tj.app`);
   - перезагрузка эмулятора (`adb reboot`, дождаться `sys.boot_completed`) → будильник снова стоит;
   - смена языка → имя канала на новом языке;
   - диплинк при закрытом приложении (смахнуть из недавних) и при открытом.

## Отчёт

Файлы; решения, где отступил от брифа, и почему; пути к скриншотам; что не проверено. Один коммит `redesign: daily lesson reminder (B4)` с последней строкой `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`; путь worktree и ветка.
