# Задача A2 — фоновый звук на Media3 и выбор чтеца

Исполнитель: Sonnet. Ветка: твой worktree от `redesign/android`. Параллельно идут A1 (данные) и A3 (приватность и релиз). **Эмулятор твой** — остальные его не трогают.

## Окружение

1. Worktree может быть создан от `master`: первым делом `git merge --ff-only redesign/android`.
2. `cp /Users/azizjon/projects/quran_todo_kmp/local.properties .`
3. Собирай только `./gradlew` из корня worktree. Основной checkout не трогай.
4. Эмулятор `DoruIman_API_35` (`~/Library/Android/sdk/platform-tools/adb devices`). Ставь свою сборку: `adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk`. Скриншоты — в `/private/tmp/claude-501/-Users-azizjon-projects-quran-todo-kmp/3cd90de6-e3fd-4e6e-b61f-ad0156db34a3/scratchpad/pa2/` (`adb exec-out screencap -p > f.png`, `sips -Z 1000 f.png` перед просмотром).

## Прочитай сначала

1. `docs/redesign/PRODUCT_PLAN.md` — 2.1 («Кэш аудио», «Чтецы»), 2.3, 6 «Этап A».
2. `docs/redesign/API.md` — `PlaybackController`, `UiPrefs`, правило DI.
3. Код: `ui/playback/PlaybackController.kt`, `commonMain/.../common/audio/**` (`ReaderAudioSession` — секвенсор повторов, пауз, диапазона; у него есть `internal`-конструктор с `ReaderAudioOutput` и `source` — androidMain того же модуля может им пользоваться), `androidMain/.../common/audio/**`, `ui/reader/components/PlayerDock*.kt`, мини-плеер на главном (`grep -rn "MiniPlayer" composeApp/src/androidMain`), `ui/settings/SettingsScreen.kt` (раздел «Звук», строка `copy.reciter` сейчас статичная), `QuranTodoApplication`, `AndroidManifest.xml`.

## Зона

`ui/playback/**` (основная), `androidMain/.../common/audio/**`, `commonMain/.../common/audio/**` — **только аддитивно, поведение iOS не меняется**, `UiPrefs` (новое поле), `ui/di/UiModules.kt`, манифест (сервис и разрешения), `gradle/libs.versions.toml` и `composeApp/build.gradle.kts` (зависимости Media3), `res/drawable/` (иконка уведомления), раздел «Звук» в `ui/settings/SettingsScreen.kt` + свой лист чтецов, состояние ошибки в `PlayerDock*`, `docs/redesign/API.md` (раздел про звук). Если тронул `commonMain` — `./gradlew :composeApp:compileKotlinIosSimulatorArm64`.

## Что сделать

### 1. Media3 вместо `MediaPlayer` (P0)
Архитектура (отступай, только если упрёшься, и объясни в отчёте):
- **Вывод звука:** `ExoReaderAudioOutput : ReaderAudioOutput` на **одном** `ExoPlayer` на всё приложение (главный поток). `setAudioAttributes(USAGE_MEDIA, AUDIO_CONTENT_TYPE_SPEECH, handleAudioFocus = true)`, `setHandleAudioBecomingNoisy(true)`, `setWakeMode(C.WAKE_MODE_NETWORK)`. `PlaybackController` создаёт `ReaderAudioSession` через `internal`-конструктор с этим выводом. Секвенсор (повторы, паузы, диапазон, таймер сна) остаётся прежним.
- **Когда ExoPlayer ставит паузу сам** (потеря фокуса навсегда, отключение наушников — `onPlayWhenReadyChanged` с причиной `AUDIO_FOCUS_LOSS` / `AUDIO_BECOMING_NOISY`), сессия должна узнать об этом и перейти в паузу (кнопки в UI показывают ▶). Короткая потеря фокуса (звонок) — ExoPlayer сам вернёт звук; UI не должен «застрять» в неверном состоянии ни в одном из случаев.
- **Сервис:** `PlaybackService : MediaSessionService` (`foregroundServiceType="mediaPlayback"`, разрешения `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`). `MediaSession` с адаптером-плеером на `SimpleBasePlayer`, который отражает `PlaybackController.nowPlaying/state` (плейлист = аяты суры, текущий индекс, играет/пауза/буферизация, позиция) и превращает команды (play/pause, next/previous, seek к аяту, stop) в вызовы `PlaybackController`. Метаданные: заголовок «Ан-Наба · 5» (название суры по языку интерфейса, `SurahNames.displayName`), исполнитель — чтец, обложка — бейдж/иконка приложения.
- Подключение: при первом `start()` `PlaybackController` строит `MediaController` к сервису (`SessionToken` + `buildAsync`), дальше Media3 сам держит foreground и уведомление, пока играет.
- Уведомление и экран блокировки: «⏮ ⏯ ⏭», монохромная малая иконка `res/drawable/ic_stat_quran.xml` (векторная восьмиконечная звезда — её же потом возьмут напоминания), нажатие открывает приложение (`setSessionActivity` → `MainActivity`). Кнопки гарнитуры и Bluetooth работают через сессию.
- Смахнули приложение из недавних (`onTaskRemoved`) — стоп и `stopSelf`. Выход «назад» из приложения во время звука — звук продолжается **с уведомлением** (так ведут себя аудиоприложения); после паузы уведомление можно смахнуть.
- **Без пауз-щелчков между аятами:** при старте аята предзагружай следующие 2 в `AudioCache` (`prefetch`); играй из файла кэша.
- **Ошибки сети:** 2 повтора с паузой 1 с и 2 с, затем `hasError`. В доке и мини-плеере при ошибке — понятное «Нет связи» и «Повторить» (проверь, что сейчас показывается; доделай, если нет; 5 языков).
- Старый `AudioPlayer.android.kt` (`MediaPlayer`) больше не используется Android-путём ридера; `expect class AudioPlayer` оставь (его используют старые общие экраны).

### 2. Выбор чтеца (P1)
- Чтецы: Мишари Аль-Афаси, Махмуд Халиль аль-Хусари (муратталь), Мухаммад Сиддик аль-Минышави (муратталь), Абдуль-Басит Абдус-Самад (муратталь). **Проверь идентификаторы и битрейт** на CDN (`curl -sI https://cdn.islamic.network/quran/audio/{bitrate}/{edition}/1.mp3`, также 262 и 6236); список изданий: `https://api.alquran.cloud/v1/edition?format=audio&language=ar`. Убедись, что это муратталь, а не муджаввад.
- Один источник URL: `Reciter` (id, edition, bitrate, имена на 5 языках) и функция URL аята. Вшитые URL Аль-Афаси в `ReaderAudioSession.kt` и `HomeViewModel.kt` — общие с iOS: в общем коде можешь добавить функцию с чтецом по умолчанию Аль-Афаси, но не меняй поведение iOS. Оффлайн-пакет (`HomeViewModel`) пока качает Аль-Афаси — его переделает задача «Загрузки»; не трогай `HomeViewModel` (его меняет A1).
- Ключ кэша включает чтеца; у Аль-Афаси остаётся старый ключ `ayah_{s}_{a}`, чтобы уже скачанное не пропало.
- `UiPrefs.reciter` (StateFlow + сеттер). Смена чтеца во время звука — текущий аят перезапускается новым голосом.
- Настройки → «Звук» → «Чтец» открывает лист: 4 чтеца с короткой подписью (стиль, темп), у каждого «▶ образец» (112:1), выбранный отмечен. Компоненты дизайн-системы (`QhSheet`, строки, `pressable`).

### 3. Лимит кэша аудио (P1)
- `AudioCache.android`: лимит 1 ГБ, вытеснение самых давно использованных (обновляй время доступа при попадании). Файлы оффлайн-пакета вытеснять можно — их перекачают.
- В «Звук» — строка «Аудио на устройстве · 230 МБ» с «Очистить» (подтверждение, тост).

### 4. `API.md`
Опиши новое API звука: `Reciter`, `UiPrefs.reciter`, сервис, поведение фокуса, ошибки/повтор, `SleepTimer` (он уже есть в `PlaybackController`, но в `API.md` не описан).

## Проверка

1. `./gradlew :composeApp:assembleDebug`, `./gradlew :composeApp:testDebugUnitTest` (+ iOS-компиляция, если трогал `commonMain`). Unit-тесты: URL чтецов, ключи кэша, правило вытеснения.
2. На эмуляторе, со скриншотами:
   - пуск из ридера → уведомление с кнопками (`adb shell dumpsys media_session` — сессия активна, состояние PLAYING);
   - экран выключен (`adb shell input keyevent 26`) 30+ с — звук идёт, аяты сменяются (смотри по логу/`dumpsys`), включи обратно;
   - команды извне: `adb shell cmd media_session dispatch pause|play|next|previous` — UI ридера и мини-плеера отражает;
   - смахивание из недавних — звук остановлен, уведомления нет;
   - смена чтеца — слышен (по URL в логе) другой голос, образец в листе играет;
   - самолётный режим на неcкачанном аяте — после повторов «Нет связи · Повторить», после включения сети «Повторить» работает.
3. Холодный старт и прокрутка ридера не стали медленнее.

## Отчёт

Файлы; архитектура (если отступил — почему); проверенные идентификаторы чтецов с битрейтами; что проверено на эмуляторе (пути к скриншотам); что не удалось проверить (наушники, Bluetooth). Один коммит `redesign: media3 playback and reciters (A2)` с последней строкой `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`; путь worktree и ветка.
