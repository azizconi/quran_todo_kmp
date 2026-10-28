# Задача 0B — каркас Android-приложения

Исполнитель: Sonnet. Ветка: твой worktree от `redesign/android`. Параллельно идёт задача 0A (дизайн-система: `ui/theme`, `ui/motion/*` кроме контрактов, `ui/haptics`, `ui/components`, `ui/catalog`) — не создавай и не меняй эти файлы.

## Окружение worktree

`local.properties` не в git: перед первой сборкой выполни `cp /Users/azizjon/projects/quran_todo_kmp/local.properties .` в корне своего worktree. Сборка — только `./gradlew` из корня worktree. Не трогай основной checkout `/Users/azizjon/projects/quran_todo_kmp`.

## Прочитай сначала

- `docs/redesign/ANDROID_REDESIGN.md` — разделы 0, 3.4, 3.5, 4 (целиком), 6.1, 7, 8.
- Контракты (уже лежат, сигнатуры не менять): `ui/navigation/Dest.kt`, `ui/motion/SharedKeys.kt`, `ui/motion/SharedElements.kt`, `ui/motion/ReducedMotion.kt`.
- Текущий код, который заменяешь: `androidMain/.../MainActivity.kt`, `commonMain/.../App.kt` (побочные эффекты и CompositionLocal-ы), `commonMain/.../presentation/hifz/HifzAppShell.kt`, `commonMain/.../common/audio/ReaderAudioSession.kt`, `androidMain/.../di/AndroidModule.kt`, `androidMain/.../QuranTodoApplication.kt`, `androidMain/.../common/settings/UserSettingsStorage.android.kt` (как хранятся настройки).

Корень пакетов: `composeApp/src/androidMain/kotlin/tj/app/quran_todo/ui/`.

## Твоя зона

`MainActivity.kt`, `QuranTodoApplication.kt` (если нужно), `AndroidManifest.xml`, `res/values/` (тема splash), `ui/QuranHifzApp.kt`, `ui/navigation/NavTransitions.kt` (+ `Dest.kt` — только если без правки никак), `ui/state/UiPrefs.kt`, `ui/playback/PlaybackController.kt`, `ui/di/UiModules.kt`, `di/AndroidModule.kt` (подключение модулей), заглушки экранов `ui/{home,reader,settings,onboarding,progress}/<Feature>Screen.kt`, `gradle/libs.versions.toml` и `composeApp/build.gradle.kts` (splashscreen).

## Что сделать

### 1. Корень приложения `QuranHifzApp()`
- Перенеси из `App()` всё корневое состояние: язык, тема, палитра, шрифт чтения, стиль карточки, `AppSettings`, реакцию на `SettingsChangeNotifier`, телеметрию (`app_opened`, `logScreen` при смене маршрута), отключение напоминаний. Отдай те же `Local*`-провайдеры из commonMain (`LocalAppStrings`, `LocalAppLanguage(+Setter)`, `LocalThemeMode(+Setter)`, `LocalThemePalette(+Setter)`, `LocalReadingFontStyle(+Setter)`, `LocalAyahCardStyle(+Setter)`, `LocalAppSettings(+Setter)`), чтобы общие функции (`getQuranFontFamily()` и др.) работали.
- Режим «как в системе»: `UiPrefs.themeFollowsSystem`; если true — тема следует `isSystemInDarkTheme()`. Сеттер темы из UI выключает следование системе.
- Тема: пока 0A не слита, оборачивай в существующий `AppTheme(mode, palette)` + M3 `MaterialTheme` с простой схемой из `nativeReaderThemeColors` (как `settingsColorScheme` в `NativeSettingsScreen`). Оставь `// TODO(0A-merge): QhTheme` в одном месте — оркестратор заменит одной строкой.
- Провайди `LocalReducedMotion` (из `rememberSystemReducedMotion()` ИЛИ `UiPrefs.reduceMotion`).
- Системные бары: edge-to-edge, прозрачные, иконки светлые/тёмные по теме; меняются при смене темы. Убери зависимость от «isReaderRoute» из MainActivity — экраны сами рисуют фон под барами.
- Онбординг-гейт: если язык или шрифт не сохранены (как сейчас в `App()`), стартовый маршрут `Dest.Onboarding`, иначе `Dest.Home`. По `onFinished` — `navigate(Dest.Home) { popUpTo<Dest.Onboarding> { inclusive = true } }`.
- Оставь место для `ToastHost` (0A) — `// TODO(0A-merge): ToastHost`.

### 2. Навигация
- `SharedTransitionLayout { NavHost(...) }` с type-safe маршрутами `Dest.*` (navigation-compose уже в зависимостях androidMain; убедись, что type-safe API доступен в этой версии, иначе обнови `navigationCompose` до ближайшей совместимой с Compose Multiplatform 1.8.0 и напиши об этом).
- В каждом `composable<Dest.X>` провайди `LocalSharedTransitionScope` и `LocalNavAnimatedVisibilityScope` (контракт в `SharedElements.kt`).
- `NavTransitions.kt`: enter/exit/popEnter/popExit по разделу 3.4; при `LocalReducedMotion` — кроссфейд 150мс.
- Предиктивный назад: `android:enableOnBackInvokedCallback="true"` в `<application>`.
- Защита от двойного нажатия: навигация вперёд игнорируется, пока текущий переход не завершён (`transition.isRunning`/`currentBackStackEntry` lifecycle != RESUMED).
- `Dest.Catalog` — только в debug (`BuildConfig.DEBUG` или `ApplicationInfo.FLAG_DEBUGGABLE`); открывается колбэком `onOpenCatalog`, который ты прокинешь в заглушку главного как долгое нажатие на заголовок. Пока 0A не слита — заглушка «Catalog».

### 3. Заглушки экранов (фаза 1 заменит их содержимое)
Создай файлы с сигнатурами из раздела 4.1 спецификации. Чтобы приложение работало всегда, заглушки временно делегируют в старые экраны:
- `ui/home/HomeScreen.kt` → `QuranLibraryRoute(viewModel = koinViewModel<HomeViewModel>(), onOpenSurah = { onOpenReader(Dest.Reader(it)) }, onOpenSettings = onOpenSettings)` — он `internal` в commonMain; если недоступен из androidMain, сделай его `public` (единственная допустимая правка commonMain-файла в этой задаче) или временно отрисуй простой список.
- `ui/reader/ReaderScreen.kt` → берёт `SurahWithAyahs` из `HomeViewModel.uiState.completeQuran` и показывает старый `SurahScreen(surahWithAyahs, onDismiss = onBack)`.
- `ui/settings/SettingsScreen.kt` → `NativeSettingsScreen(onBack)`.
- `ui/onboarding/OnboardingScreen.kt` → старые `LanguageOnboardingScreen` → `FontOnboardingScreen` по шагам.
- `ui/progress/ProgressScreen.kt` → простой текст «Прогресс» + назад.

### 4. UiPrefs
`ui/state/UiPrefs.kt` — по разделу 4.3: SharedPreferences (контекст — как в `UserSettingsStorage.android.kt`), каждое значение доступно как `StateFlow` + сеттер. `recordRecentSurah(n)`, `togglePin(n)`, `saveLastRead(surah, ayahInSurah)` (debounce на стороне вызывающего), `lastRead(): Pair<Int, Int>?` с fallback на старые `UserSettingsStorage.getLastPlaybackSurah/AyahNumber` (у старого — глобальный номер аята; конверсия в номер в суре требует `completeQuran`, поэтому верни сырой глобальный номер отдельным полем и задокументируй). Синглтон в Koin.

### 5. PlaybackController
`ui/playback/PlaybackController.kt` — по разделу 4.4. Один `ReaderAudioSession`, app-scope. `start(...)` конфигурирует сессию под суру (через `session.configure(...)`), затем `select(fromIndex)` и, если `autoplay`, `toggle()`. `nowPlaying` = null, пока ничего не запускали или после `stop()`. Не трогай `ReaderAudioSession` и его тесты; если нужен новый публичный метод — добавь, не меняя существующее поведение, и прогони `testDebugUnitTest`. Синглтон в Koin.
Заглушка ридера пока использует старый `SurahScreen` со своей сессией — это нормально; ридер на контроллере сделает задача 1B.

### 6. Koin
`ui/di/UiModules.kt`: `val homeUiModule`, `readerUiModule`, `hifzUiModule`, `progressUiModule`, `settingsUiModule`, `onboardingUiModule` (пустые), `val appUiModule` (UiPrefs, PlaybackController) и `val uiModules = listOf(...)`. Подключи в `startKoin` (там, где сейчас подключаются модули).

### 7. Splash
`androidx.core:core-splashscreen` (последняя стабильная, совместимая с compileSdk 36). Тема запуска: фон `bg` (светлый `#F6F3EC`, `values-night` — `#0D1311`), иконка приложения. `installSplashScreen()` в `MainActivity`, `setKeepOnScreenCondition` пока Коран не загружен (`HomeViewModel.uiState.quranLoadState`), но не дольше 900мс. Плавный exit (scale 1→0.9 + alpha 1→0, 280мс).

### 8. MainActivity
Тонкая: splash, edge-to-edge, `setContent { QuranHifzApp() }`, `AndroidContextHolder`, запрос разрешения на уведомления (как сейчас). Старый NavHost удалить.

## Проверка

1. `./gradlew :composeApp:assembleDebug` и `./gradlew :composeApp:testDebugUnitTest` — зелёные.
2. Поставь на эмулятор (если `adb devices` пуст — запусти `~/Library/Android/sdk/emulator/emulator -avd DoruIman_API_35 -no-snapshot-save &` и дождись `adb wait-for-device` + `sys.boot_completed`). Проверь: старт → главный (старая библиотека) → сура (старый ридер) → назад → настройки → назад; системный жест назад; поворот экрана; холодный старт с чистыми данными (`adb shell pm clear tj.app.quran_todo`) показывает онбординг. Снимки — `adb exec-out screencap -p > <scratch>/x.png`.
3. Звук в старом ридере играет и останавливается при выходе (это старое поведение; нормально).

## Отчёт

- Файлы, ключевые сигнатуры (`QuranHifzApp`, `UiPrefs`, `PlaybackController`, модули), версии добавленных/обновлённых зависимостей.
- Что проверено на эмуляторе, что нет.
- Закоммить одним коммитом `redesign: app shell, navigation, playback controller (0B)`.
