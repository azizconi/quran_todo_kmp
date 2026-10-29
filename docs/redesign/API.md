# API дизайн-системы и каркаса (для исполнителей фаз 1–2)

Код: `composeApp/src/androidMain/kotlin/tj/app/quran_todo/ui/`. Перед использованием компонента открой его файл — KDoc там короткий и точный. Витрина всех компонентов: `ui/catalog/CatalogScreen.kt` (там же примеры использования, например как дать списку верхний отступ под `QhLargeTopBar`).

## Тема (`ui/theme`)

- `QhTheme(mode, palette, reducedMotion = false, hapticsEnabled = true) { … }` — уже стоит в корне приложения. Сам провайдит `LocalReducedMotion` (системный масштаб анимаций OR тумблер), `LocalHaptics`, M3-тему из наших токенов и старый `AppTheme` внутри.
- Доступ: `QhTheme.colors` (`QhColors`: все токены раздела 2.1 + `readerHighlight`, `scrim`, `isDark`, `onDanger`, `onLearned`), `QhTheme.type` (`display`, `headline`, `title1`, `title2`, `body`, `callout`, `label`, `overline`; фабрики `quranHeader(family, size = 32.sp)`, `quranBody(family, size = 28.sp)`, `quranInline(family, size = 19.sp)`), `QhTheme.shapes` (`xs`, `sm`, `md`, `lg`, `xl`, `full`, `xlTop`).
- `TextStyle.tabular()` — табличные цифры. `qhColors(mode, palette)` — токены любой палитры (для превью палитр).
- `QhSpacing`: `x1…x12` (кратно 4dp), `screen` 20dp, `section` 28dp, `card`, `cardLarge`, `minTouch` 48dp, `iconBar` 22dp, `iconInline` 20dp.
- Арабский шрифт — как раньше: `getQuranFontFamily()` из commonMain (читает `LocalReadingFontStyle`).

## Motion (`ui/motion`)

- `QhMotion` — токены (длительности, `Emphasized*`, `snappy()`, `bouncy()`, `gentle()`, `press()`, `bounds()`, `emphasized()`, `enter()`, `exit()`). `spec.orSnap()` — `snap()` при Reduce Motion.
- `Modifier.pressable(onClick = …, onLongClick = null, haptic = HapticKind.Tick, scale = 0.97f, enabled = true, shape = RectangleShape, highlight = true, role = Role.Button, onClickLabel = null)` — **ставить ДО `clip`/`background`** и передавать ту же `shape`. Только именованный `onClick =` (без trailing-lambda).
- `rememberStaggerState(ready = true)` + `Modifier.staggeredEnter(index, state, enabled = true)` — `ready = false`, пока показан скелетон.
- `AnimatedCounter(value: Int, modifier, style, color)`.
- `CelebrationBurst(trigger: Int, modifier = Modifier.fillMaxSize(), radius = 72.dp, haptic = Success)` — срабатывает при изменении `trigger` (увеличивай счётчик).
- Общие элементы: `Modifier.sharedElementKey(key)`, `Modifier.sharedBoundsKey(key, resize = SharedResize.Remeasure|Scale, enter, exit)`, ключи только из `SharedKeys`, `origin` — из `Origin` (`ui/navigation/Dest.kt`). `isSharedTransitionActive()`; `rememberSharedSettled()` — экран в покое (вход закончен, не уходит, ничто не летит): им показывают декор, который не должен лететь вместе с общим элементом (плитка за шестерёнкой).
  - `Remeasure` — контейнеры (карточка/строка ↔ шапка): старое содержимое гаснет сразу (`SharedFades.ContainerExit`), новое проявляется с задержкой 100 мс (`ContainerEnter`). Содержимое перемеривается на каждом кадре, поэтому всё, что рисуется в фиксированных dp (обводка, шрифт, толщина кольца), так лететь не должно.
  - `Scale` — картинки и подписи (`ContentScale.Fit`, по центру): обе версии кроссфейдятся (`ContentEnter/Exit`) и масштабируются под анимируемые границы. **Границы должны быть по содержимому** (`wrapContentWidth`, без `fillMaxWidth`), иначе короткая подпись растягивается на ширину широкого бокса. Так сделаны название, арабское имя и кольцо прогресса (`ProgressRing`).
  - `sharedElementKey` — когда у обеих сторон один и тот же вектор/иконка (бейдж суры, шестерёнка, кнопка play).
  - Шапка ридера не берёт участия в общих переходах, если звук уже играет (список сразу уезжает к играющему аяту): `ReaderHeaderData.sharesElements`.
- Круговое раскрытие темы (`ui/motion/CircularReveal.kt`): корень оборачивает UI в `ThemeRevealHost` (запись в `GraphicsLayer`, оверлей, провайдит `LocalThemeRevealOrigin`). Экраны: `val switcher = rememberThemeSwitcher()`; `switcher.switch(mode, origin = last touch)`, `switcher.switchPalette(palette)`, `switcher.reveal { любая смена, перекрашивающая весь UI }`. Точку касания корень запоминает сам; в листах/диалогах (отдельные окна) повесь `Modifier.themeRevealOriginTracker()` на их содержимое. Старый кадр виден только в окне приложения: сам лист перекрашивается сразу. Reduce Motion — кроссфейд 150 мс. Иконки статус- и навбара переключаются, когда круг до них доходит.
- `LocalReducedMotion.current` — если нужно вручную выключить свой эффект.

## Тактильность (`ui/haptics`)

`LocalHaptics.current.perform(HapticKind.Tick | Segment | Step | LongPress | Success | Reject)`. `pressable`, `QhChip`, `QhSegmentedControl`, `QhSlider`, `AyahWheel`, свитчи уже вибрируют сами — не дублируй.

## Компоненты (`ui/components`)

| Компонент | Сигнатура (кратко) |
|---|---|
| Кнопка | `QhButton(text, onClick, modifier, variant = Primary/Tonal/Ghost/Danger, size = L/M/S, icon = null, enabled, loading)` |
| Иконка-кнопка | `QhIconButton(icon, contentDescription, onClick, modifier, filled = true, enabled, tint, iconSize)` |
| Карточка | `QhCard(modifier, onClick = null, onLongClick = null, style = Standard/Hero, contentPadding) { ColumnScope }`; `Modifier.softShadow(shape, color, blur, offsetY)`; `QhSectionHeader(title, modifier, trailing)` |
| Статус | `StatusPill(status: QhStatus, label, modifier, onClick = null)`; `QhStatus = NotStarted/Learning/Learned/Due`; `SurahTodoStatus?.toQhStatus()` |
| Чип | `QhChip(label, selected, onClick, modifier, leadingIcon = null, enabled)` |
| Сегменты | `QhSegmentedControl(options: List<String>, selectedIndex, onSelect, modifier, icons = null)` |
| Поиск | `QhSearchField(value, onValueChange, placeholder, clearContentDescription, modifier, focusRequester, onFocusChange, onSearch)` |
| Шапка | `QhLargeTopBar(title, state, modifier, overline = null, navigation = null, actions: RowScope.() -> Unit, windowInsets)`; `rememberQhTopBarState(listState | scrollState, collapseDistance = 48.dp)`. Шапка лежит поверх списка — дай списку верхний отступ под развёрнутую высоту (см. витрину) |
| Лист | `QhSheet(onDismiss, modifier, sheetState = rememberModalBottomSheetState(true)) { ColumnScope }` |
| Строки настроек | `QhSwitchRow(title, checked, onCheckedChange, modifier, description, icon, iconTint, enabled)`, `QhNavRow(title, onClick, …)`, `QhValueRow(title, value, modifier, onClick, …)`, `QhIconTile(icon, modifier, tint)`, `QhRowDivider()` |
| Слайдер | `QhSlider(value, onValueChange, modifier, valueRange, steps, enabled, onValueChangeFinished)` |
| Колесо | `AyahWheel(label, minimum, maximum, value, onValueChange, modifier)` |
| Прогресс | `ProgressRing(progress, modifier, size = 48.dp, strokeWidth = 4.dp, color, trackColor, content = null)`, `QhLinearProgress(progress, modifier, height = 6.dp, color, trackColor)` |
| Скелетон | `Modifier.shimmer(shape)`, `Skeleton(modifier, shape)`, `SkeletonRow()` |
| Пустое состояние | `EmptyState(title, modifier, message, actionLabel, onAction, illustration)` |
| Тост | `LocalToaster.current.show(message, kind = ToastKind.Info/Success/Error, durationMillis = 3000)` — хост уже в корне |
| Орнамент | `CalligraphyText(text, modifier, size = 40.dp, color = gold, revealMillis = 1_400, revealDelayMillis = 0, shimmer = true)` с `object Calligraphy { Bismillah, Iqra, Alhamdulillah, Hifz }`, `MihrabCalligraphy(modifier, width = 88.dp, word = Calligraphy.Iqra)`, `Medallion(modifier, size = 40.dp, color, strokeWidth = 1.5.dp, fill)`, `AnimatedCheckmark(…)`, `SurahNumberBadge(number, status: SurahTodoStatus?, learnedFraction, modifier, size = 40.dp)`, `ArabesqueLattice(modifier, color, alpha = 0.07f, cell, scrollOffset: () -> Float, parallax = 0.2f)`, `OrnamentDivider()`, `SurahCartouche(modifier, background = readerBg) { BoxScope }`, `AyahRosette(number, modifier, status: AyahTodoStatus? = null, weak = false, size = 34.dp)` |

## Каркас (`ui/`, задача 0B)

- `QuranHifzApp()` — корень: состояние языка/темы/шрифта/`AppSettings`, все `Local*`-провайдеры из commonMain, `QhTheme`, `LocalToaster` + `ToastHost`, системные бары, `SharedTransitionLayout` + `NavHost`. Размер шрифта аята в корне уже приводится к 22..40.
- Сеттеры корня (использовать их, а не прямую запись в storage): `LocalAppLanguageSetter`, `LocalThemeModeSetter` (выключает «как в системе»), `LocalThemePaletteSetter`, `LocalReadingFontStyleSetter`, `LocalAyahCardStyleSetter`, `LocalAppSettingsSetter`.
- Навигация: экраны получают колбэки (`onOpenReader(Dest.Reader(...))`, `onBack`…). Повторные нажатия во время перехода корень игнорирует сам. `LocalNavAnimatedVisibilityScope` уже провайдится в каждой записи.
- **ViewModel-и.** Общие `HomeViewModel`, `SurahViewModel`, `StatsViewModel` — Koin-синглтоны. **Не получай их через `koinViewModel<HomeViewModel>()`** — это кладёт синглтон в ViewModelStore записи навигации, и при её закрытии `onCleared()` убивает его `viewModelScope` для всего приложения. Правильно: свой Android-VM (`viewModel { ReaderViewModel(get(), get(), …) }` в своём модуле `ui/di/UiModules.kt`), внутри которого общие VM приходят через конструктор (`get()`), а на экране — `koinViewModel<ReaderViewModel>()`. Вне VM (редко) — `koinInject<HomeViewModel>()`. `ProvideProcessViewModelStore { }` (`ui/di/ProcessViewModelStore.kt`) — временный костыль для заглушек со старыми экранами; новым экранам он не нужен, убери его из своей заглушки.
- `UiPrefs` (Koin single, `koinInject()` / `get()`): `reciter: StateFlow<Reciter>` + `setReciter(reciter)` (см. «Звук»), `themeFollowsSystem`, `hapticsEnabled`, `reduceMotion`, `keepScreenOn`, `readerLayout: ReaderLayout (LIST, MUSHAF)`, `hifzHintMode: HifzHintMode (NONE, FIRST_WORD)`, `onboardingGoals: Set<String>` (`OnboardingGoal.READ/LISTEN/MEMORIZE`), `recentSurahs: List<Int>` + `recordRecentSurah(n)`, `pinnedSurahs: Set<Int>` + `togglePin(n)`, `lastReadPosition: StateFlow<LastReadPosition?>` (`surah`, `ayahInSurah?`, `legacyGlobalAyah?` — если есть только старый ключ, номер аята глобальный, переводи через `completeQuran`: `AyahEntity.number` глобальный, `numberInSurah` — в суре), `saveLastRead(surah, ayahInSurah)` (debounce на стороне вызывающего). У каждого значения — `StateFlow` + `setX(...)`.
- `PlaybackController` (Koin single): `nowPlaying: StateFlow<NowPlaying?>` (`surahNumber`, `ayahs: List<ReaderAudioAyah>`), `state: StateFlow<ReaderAudioState>` (`index`, `isPlaying`, `canResume`, `isPreparing`, `pauseRemainingSeconds`, `positionMs`, `durationMs`, `hasError`), `isActiveFor(surah)`, `start(surah: SurahWithAyahs, prefs, practice, fromIndex, autoplay = true)`, `toggle()`, `next()`, `previous()`, `select(index)`, `updateSettings(prefs, practice)`, `stop()`. `start` всегда конфигурирует сессию без автозапуска при открытии, затем `select(fromIndex)` и `toggle()`, если `autoplay`. Таймер сна, чтецы, фоновый сервис, образец чтеца, фокус и ошибки — см. раздел «Звук» ниже (`setSleepTimer`, `pauseBySystem()`, `playSample(source, onFinished)`, `stopSample()`).
- Тесты для androidMain-кода — `composeApp/src/androidUnitTest/kotlin/...` (kotlin.test доступен), запуск `./gradlew :composeApp:testDebugUnitTest`.

## Звук (`ui/playback`, `common/audio`)

Фоновое воспроизведение на Media3 и выбор чтеца (задача A2). Секвенсор повторов/пауз/диапазона — прежний `ReaderAudioSession` из `commonMain` (общий с iOS, поведение iOS не менялось); на Android под ним теперь ExoPlayer.

### Схема

```
UI ──► PlaybackController ──► ReaderAudioSession ──► ExoReaderAudioOutput ──► Media3Engine (ОДИН ExoPlayer на приложение)
            ▲   │                     │ source: AyahAudioSource (файл из кэша / URL, предзагрузка 2 следующих)
            │   └─ nowPlaying/state ──┴──► PlaybackSessionPlayer (SimpleBasePlayer) ──► MediaSession в PlaybackService
            └──────── команды (play/pause, ⏮ ⏭, стоп) из уведомления, экрана блокировки, гарнитуры, Bluetooth
```

- Всё создаётся в `createPlaybackController(context, uiPrefs)` (`PlaybackWiring.kt`), Koin single в `appUiModule`. Конструктор `PlaybackController` — `internal`: в тестах подставляются `ReaderAudioSession` с фейковым `ReaderAudioOutput`, `reciter` (`StateFlow`), `serviceLink`, `extras`.
- **`PlaybackService : MediaSessionService`** (`foregroundServiceType="mediaPlayback"`, разрешения `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`). Создаётся, когда первый `start()` привязывает к нему `MediaController` (`MediaControllerLink`); дальше Media3 сам держит foreground и уведомление, пока играет (на паузе уведомление можно смахнуть). Сессия зеркалит контроллер: плейлист — аяты суры, текущий элемент — текущий аят, «играет» = последовательность идёт (в паузе между аятами тоже), «буферизация» = аят подготавливается, остановка/ошибка показываются как «пауза» (кнопка ▶ в уведомлении повторяет). Метаданные: заголовок «Ан-Наба · 5» (`SurahNames.displayName` по языку интерфейса), исполнитель — чтец, обложка — иконка приложения. Позиция экстраполируется системой, а не обновляется 5 раз в секунду. Малая иконка — `res/drawable/ic_stat_quran.xml` (раскрытый Коран на подставке-рехале; её же берут напоминания), канал `quran_playback`, нажатие открывает приложение (`setSessionActivity`).
- **Команды в сторону контроллера:** play/pause → `toggle()`, ⏮/⏭ → `previous()`/`next()` (⏮ после 3 секунд аята — повтор аята), переход к аяту → `select(i)` + `toggle()`, стоп → `stop()`. Внешние `PlaybackController.pauseBySystem()` вызывает вывод при потере фокуса насовсем и отключении наушников.
- **Смахнули приложение из недавних** (`onTaskRemoved`): `stop()`, отвязка, `stopSelf`; уведомления и звука нет. «Назад» из приложения во время звука — звук продолжается с уведомлением.
- **Wake lock.** ExoPlayer держит `WAKE_MODE_NETWORK`, пока играет; пауза между аятами — это корутинная задержка, поэтому пока `state.isPlaying`, сервис держит `PARTIAL_WAKE_LOCK` (снимается на паузе/остановке/`onDestroy`).

### Фокус и наушники

`AudioAttributes(USAGE_MEDIA, AUDIO_CONTENT_TYPE_SPEECH)`, `handleAudioFocus = true`, `setHandleAudioBecomingNoisy(true)`.

| Событие | Что происходит |
|---|---|
| Короткая потеря фокуса (звонок, навигатор) | ExoPlayer сам встаёт на паузу и сам возвращает звук; `state.isPlaying` остаётся `true` (позиция стоит), UI не «застревает» |
| Фокус потерян насовсем (запустили музыку) | `onPlayWhenReadyChanged(AUDIO_FOCUS_LOSS)` → `pauseBySystem()` → `canResume = true`, кнопки показывают ▶ |
| Отключили наушники | то же, причина `AUDIO_BECOMING_NOISY` |
| Последний аят закончился | `releaseFocus()` (плеер `stop()`), чтобы чужая музыка могла продолжить; на паузе и между аятами фокус держится |
| Образец чтеца в настройках | берёт тот же плеер: звук ридера останавливается на месте (`nowPlaying` остаётся, аят тот же, не «пауза»); после образца сам не продолжается |

### Ошибки сети

Аят, который не удалось сыграть (нет сети, битый файл), `ExoReaderAudioOutput` повторяет **2 раза: через 1 с и через 2 с**, и только потом сессия получает `hasError = true` (повторы — только на Android; в `ReaderAudioSession` ничего не менялось). Файл кэша, который не проигрался после повторов, удаляется (повтор вручную скачает заново). В доке и мини-плеере при `hasError` — «Нет связи · Повторить» (5 языков; в мини-плеере кнопка play превращается в ↻); «Повторить» = `toggle()` — играет тот же аят с начала. Без сети на нескачанном аяте `AyahAudioSource` отдаёт удалённый URL, ExoPlayer пробует его стримить, ошибка попадает в те же повторы.

### Чтецы

- `common/audio/Reciter.kt` (общий код): `Reciter(id, edition, bitrate, имена)`; `reciter.audioUrl(globalAyah)` — **единственное** место, где строится URL (`https://cdn.islamic.network/quran/audio/{bitrate}/{edition}/{n}.mp3`), `reciter.cacheKey(surah, ayahInSurah)`, `name(language)` (полное), `shortName(language)` (для строки в доке и строки настроек). `Reciters.all`, `Reciters.default` (Аль-Афаси), `Reciters.byId(id)` (неизвестный id → по умолчанию).
- Проверено на CDN (`curl -I`, 200, и скачано 112:1 для каждого): `ar.alafasy` **128**, `ar.husary` **128** (муратталь; `ar.husarymujawwad` — муджаввад, не берём), `ar.minshawi` **128** (муратталь; `ar.minshawimujawwad` — муджаввад), `ar.abdulbasitmurattal` **192** (на 128 — 403; издание «Abdul Basit … murattal»; `ar.abdulbasitmujawwad` — муджаввад, не берём). 64 kbit/s есть у аль-Афаси, аль-Хусари и Абдуль-Басита, у аль-Минышави — нет.
- **Ключ кэша:** у Аль-Афаси прежний `ayah_{s}_{a}` (уже скачанное не пропадает), у остальных `{id}_{s}_{a}` (`husary_78_5`).
- Вшитые URL в `HomeViewModel` (оффлайн-пакет, качает Аль-Афаси, его переделает «Загрузки») и в старом `HifzSessionScreen` (общий код) не менялись; `cachedAudioSource()` в `ReaderAudioSession` берёт `Reciters.default.audioUrl(...)` — тот же URL и ключ, что были.
- `UiPrefs.reciter: StateFlow<Reciter>` + `setReciter(reciter)` (SharedPreferences, хранится `id`). Смена чтеца во время звука: аят, который играет (или стоит на паузе посередине), начинается заново новым голосом; без звука — новый голос на следующем `play`.
- Предзагрузка: когда аят готов, `AyahAudioSource` качает в кэш два следующих (`prefetch`), чтобы переход был без щелчка; воспроизведение идёт из файла кэша.
- Настройки → «Звук» → «Чтец» (`ui/settings/SoundSettings.kt`): лист на 4 чтеца со стилем/темпом, «▶ образец» (112:1) и галочкой. Образец играет `PlaybackController.playSample(source, onFinished)`; в самом листе — `rememberReciterPreview()`.

### Кэш аудио

`common/audio/AudioCacheStorage` (Android): файлы `*.mp3` в `noBackupFilesDir/offline_audio`, **лимит 1 ГБ**; когда новый файл выводит общий размер за лимит, удаляются самые давно использованные (время — `lastModified`, обновляется при каждом попадании в кэш) до 90 % лимита; только что записанный файл не удаляется. Файлы оффлайн-пакета вытесняются как все. Размер — `AudioCacheStorage.shared.totalBytes()`, очистка — `clear()` (в настройках «Аудио на устройстве · 230 МБ» с «Очистить» и подтверждением). Правило вытеснения — чистая функция `selectEvictions(...)`, у неё юнит-тесты.

### Таймер сна

`PlaybackController.setSleepTimer(SleepTimer)`; `sleepTimer: StateFlow<SleepTimer>`, `sleepRemainingSeconds: StateFlow<Int>`.

- `SleepTimer.Off`, `SleepTimer.EndOfSurah` (пауза, когда доиграет **последний аят суры**, даже если он повторяется; поставленный без звука действует на следующую запущенную суру), `SleepTimer.Minutes(n)` (обратный отсчёт; `sleepRemainingSeconds` — остаток).
- Сработав, таймер ставит звук на паузу (`canResume`) и сам возвращается в `Off`; `stop()` тоже сбрасывает таймер.
- Отсчёт идёт в `applicationScope` контроллера, поэтому работает при выключенном экране (пока держится wake lock сервиса).

## Хифз (`tj.app.quran_todo.hifz`, commonMain)

Модель заучивания на отрывках, расписание повторений, «Урок дня», журнал (задача B1). Код: `composeApp/src/commonMain/kotlin/tj/app/quran_todo/hifz/` (модель — `model/`, чистые функции — `schedule/`, перенос старых данных — `legacy/`, репозиторий — `HifzRepository.kt` + `HifzRepositoryImpl.kt`), Room — `data/database/entity/hifz/`, `dao/HifzDao.kt`. Тесты: `commonTest/.../hifz/`, `androidUnitTest/.../hifz/RealTextCalibrationTest.kt`, `androidInstrumentedTest/.../hifz/HifzRepositoryTest.kt` и `MigrationTest`.

### Главное правило

**UI пишет прогресс только через `HifzRepository`.** Таблицы `surah_todo` и `ayah_todo` — **зеркало**: каждое действие репозитория в той же транзакции пересчитывает их для затронутых сур, чтобы старые экраны (главный, ридер, прогресс), которые ещё читают их, показывали то же. Новые экраны читают Flow репозитория, а не эти таблицы, и **не пишут в них сами** (`SurahViewModel.updateAyahStatus` и `HomeViewModel.completeReview` на Android переводятся на репозиторий в B2/B3/D2; пока они пишут напрямую, их запись не создаёт отрывков). `ayah_review`, SM-2 (`ReviewStateStore`) и слабые аяты в prefs на Android больше не используются (после переноса prefs очищены, таблица не удалена — её читает iOS).

Получение: `HifzRepository` и `EpochDayTicker` — Koin `single` в `CommonModule`; в своём Android-VM — через конструктор (`get()`), вне VM — `koinInject<HifzRepository>()`. Никогда `koinViewModel` для общих синглтонов (см. «Каркас»).

```kotlin
class LessonViewModel(private val hifz: HifzRepository, private val ticker: EpochDayTicker) : ViewModel() {
    val lesson: StateFlow<LessonPlan?> = hifz.lesson(ticker.day)          // пересчитывается при смене таблиц и дня
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun grade(item: LessonItem, grade: ReviewGrade, hard: Set<Int>) = viewModelScope.launch {
        hifz.review(item.passageId, grade, hard, ticker.day.value)
    }
}
```

### Модель

- **Отрывок** (`Passage`) — непрерывный диапазон аятов **одной суры** (номера **в суре**, `AyahEntity.numberInSurah`; глобальный номер — `QuranFacts.firstAyahNumber(surah) + ayah - 1`). Единица заучивания и повторения. Поля: `id` (0 — ещё не сохранён), `surah`, `startAyah`, `endAyah`, `learnedEpochDay`, `stage`, `nextDueEpochDay` (null у `OLD`), `lastReviewedEpochDay` (null — ни разу не повторялся), `lapses`, `hard: Map<ayahInSurah, clearsAfter>`.
- **Дни — `epochDay: Int`**: календарный день в часовом поясе устройства (`EpochDays.now()`, `EpochDays.of(instant, zone)`, `EpochDays.ofMillis(ms, zone)`). Время суток нигде не участвует: «просрочено» = срок раньше сегодняшнего дня.
- **Таблицы** (БД v11, миграция `MIGRATION_10_11` — только `CREATE TABLE/INDEX`, `schemas/.../11.json`): `hifz_plan` (одна строка `id = 1`; нет плана — нет строки), `hifz_passage`, `passage_hard` (FK на отрывок, `ON DELETE CASCADE`), `review_log` (**без FK**: история переживает удаление/слияние отрывка), `hifz_meta` (`legacy_import = done`).
- **Этап** (`PassageStage`): `R1, R3, R7, R14, R30, OLD` — «следующий раз через N дней» (1, 3, 7, 14, 30); `OLD` — в круге манзиль, без срока.

| Оценка при повторении в лестнице (этап k) | Результат |
|---|---|
| `EASY` | этап k+2 (дальше R30 → `OLD`) |
| `GOOD` | этап k+1 (после R30 → `OLD`) |
| `HARD` | тот же этап, срок = сегодня + интервал(k) |
| `FORGOT` | `R1` (завтра), `lapses + 1` |

Срок всегда считается **от дня повторения**, не от прежнего срока. Новый отрывок: `R1`, срок завтра, `learnedEpochDay` и `lastReviewedEpochDay` = сегодня (даже при `FORGOT`; `lapses` остаётся 0). `OLD` (манзиль): `GOOD/EASY` — остаётся `OLD`, `lastReviewed` = сегодня; `HARD` → `R3` (без `lapses`); `FORGOT` → `R1`, `lapses + 1`.

- **Трудные аяты** (`passage_hard`): отмеченные пользователем аяты получают `clearsAfter = 2` (уже трудный — снова 2). Каждое `GOOD/EASY` уменьшает счётчик всех трудных аятов отрывка, 0 — отметка исчезает; `HARD/FORGOT` счётчики не трогают. Отметки передаются при **любой** оценке и всегда выставляются в 2 (отметка, сделанная в `GOOD`, не уменьшается тут же). Номера вне отрывка игнорируются. При разрезе/слиянии отметки идут за номерами аятов.
- **Слияние старого**: когда отрывок **переходит** в `OLD` (не при повторении уже `OLD`), он сливается со смежными `OLD`-отрывками той же суры, пока вес слитого ≤ `MAX_OLD_PASSAGE_CHARS`; сначала более лёгкий сосед. Выживает строка повторённого отрывка (его `id` в журнале остаётся верным), `lastReviewed` = меньший (никогда не повторявшийся считается самым старым), `lapses` складываются, `learnedEpochDay` — меньший, отметки переходят.
- **Производные статусы**. Аят `LEARNED`, если покрыт любым отрывком (трудный — если есть в `passage_hard`). Сура: `LEARNED` — покрыты все аяты; `LEARNING` — покрыта часть **или** это текущая сура плана; иначе статуса нет. **Текущая сура плана** — первая в `surahOrder`, у которой есть непокрытые аяты.

### Константы (`schedule/HifzConstants.kt`, всё в одном месте)

| Константа | Значение | Откуда |
|---|---|---|
| `CHARS_PER_SECOND` | **5.7** | скорость спокойного чтения; аль-Фатиха с басмалой = 285 символов вшитого текста = **50 с** |
| `NEW_MULTIPLIER` / `REVIEW_MULTIPLIER` | 7 / 2 | новое: 3× по аяту + 2× отрывком + чтение + вспоминание; повторение: вспомнить + сверить |
| `CARD_OVERHEAD_SECONDS` | 15 | на карточку |
| `LESSON_BUDGET_MINUTES` | 25 | потолок урока |
| `CHARS_PER_PAGE` | **1184** | средняя страница мусхафа вшитого текста: 715 000 знаков / 604 страницы = 1183.8 (медиана 1187); публичная, для «≈ N страниц в день» = `oldWeight / CHARS_PER_PAGE / manzilDays` |
| `MAX_OLD_PASSAGE_CHARS` | **1200** | `CHARS_PER_PAGE`, округлено: потолок веса слитого `OLD`-отрывка |
| `HARD_CLEARS_AFTER` | 2 | |
| темп / манзиль | 1..20 (по умолчанию 5) / 3..30 дней (по умолчанию 7) | |

Оценка, секунды: `chars / 5.7 × множитель + 15` (`HifzConstants.newSeconds(chars)`, `reviewSeconds(chars)`). Вес отрывка = число символов (code points) его арабского текста = `LENGTH(text)` в SQLite. Метрики (`TextMetrics`: `surah, ayah, page, chars` на каждый аят) читает репозиторий одним запросом к `ayahs` и кэширует (6236 строк); перед чтением вызывает `QuranRemoteRepository.ensureQuranInstalled()` (вшитый текст ставится оффлайн).

### Расписание (чистые функции, `schedule/`)

- **Манзиль** (`Manzil`): вес отрывка = символы; квота дня = `ceil(сумма весов всех OLD / manzilDays)`. В порцию дня годится только **подходящий** отрывок: ни разу не повторялся или `today - lastReviewed >= manzilDays`. Порция дня: сначала то, что **уже повторено сегодня как OLD** (по `review_log`, засчитывается в квоту — даже если `HARD/FORGOT` вернули отрывок в лестницу), затем подходящие `OLD` по приоритету: `lastReviewed` по возрастанию (null — самые старые), при равенстве — порядок мусхафа; берём, пока квота не набрана (перешагнувший квоту берётся целиком). В уроке порция — по мусхафу. Квота набрана или подходящих нет — блока «Старое» нет. Гарантия: **каждый отрывок повторяется раз в `manzilDays` дней, не чаще** (один `OLD`-отрывок — раз в `manzilDays` дней, а не каждый день; отрывок, который сегодня вышел из лестницы в `OLD`, сегодня снова не предлагается); квота растягивает накопившийся долг (например, сразу после переноса все отрывки «ни разу не повторялись») на следующие дни. Порция не «прыгает» в течение дня; новый `OLD` в середине круга ничего не ломает. Проверено симуляцией на 30 дней: первая очередь — в первые `manzilDays` дней, интервалы между повторениями не меньше `manzilDays` (в симуляции ровно `manzilDays`).
- **Новое** (`NewPassagePlanner`): сура из очереди плана; отрывок от первого непокрытого аята, `dailyNewAyahs` аятов, не переходя покрытый аят и конец суры. Нетронутая сура длиной ≤ `dailyNewAyahs × 1.5` — вся. Если после отрывка до конца суры остаётся ≤ `dailyNewAyahs / 2` непокрытых подряд — забираем и их.
- **Бюджет** (`LessonBuilder`): 25 минут — потолок **повторений**: **Недавнее** (самое просроченное первым) → **Старое** (порция манзиля). Не влезло — отложено **вместе со всем, что идёт после** (префикс приоритета). «Сделано сегодня» (по журналу) всегда в уроке и **первым** списывает бюджет, поэтому завершение карточки ничего не втягивает из отложенного и не выкидывает уже входящее; первое повторение входит всегда. **Новое** откладывается целиком **только если повторения были урезаны** (реальный долг — сначала догоняем); если ни одно повторение не урезано, новое входит в урок, даже если выводит его за 25 минут: человек выбрал такой темп (высокий `dailyNewAyahs` не останавливает заучивание). Поэтому `totalMinutes` может быть больше 25 только без `catchingUp`.
- **Прогноз** (`Forecast`): без срока: `finishEpochDay = start + ceil(остаток / dailyNewAyahs)`, где `start` — сегодня или завтра, если новое сегодня уже сделано; со сроком: `requiredPerDay = ceil(остаток / дней до срока)` (дней ≥ 1), `behindAyahs` = «ожидаемое по прямой от `startedEpochDay` до срока минус фактическое, если > 0» — отсчёт от аятов, которые остались к старту плана (остаток + выученное с тех пор по `NEW` в журнале), так что ранее выученное не маскирует отставание.
- **Серия** (`Streak`): дни подряд с ≥ 1 записью в `review_log`, оканчивающиеся сегодня или вчера (сегодня ещё не занимался — серия жива).

### `HifzRepository`

Чтение — `Flow`, пересчитываются при изменении таблиц (Room invalidation) и, у принимающих `today`, при смене дня. Варианты с `today: Flow<Int>` берут `EpochDayTicker.day`. Результат читается одной read-транзакцией (таблицы согласованы между собой), одинаковые значения подряд не эмитятся.

| Функция | Контракт |
|---|---|
| `lesson(today: Int \| Flow<Int>): Flow<LessonPlan>` | «Урок дня» (ниже) |
| `plan(): Flow<HifzPlan?>` | активный план (`scope`, `surahOrder`, `dailyNewAyahs`, `targetEpochDay?`, `startedEpochDay`, `manzilDays`); null — нет плана |
| `forecast(today): Flow<PlanForecast?>` | `totalAyahs, remainingAyahs, dailyNewAyahs, finished, finishEpochDay?, targetEpochDay?, requiredPerDay?, behindAyahs`; null — нет плана |
| `streak(today): Flow<Int>` | серия дней |
| `surahStatuses(): Flow<Map<Int, SurahHifzStatus>>` | только суры со статусом (`LEARNING`/`LEARNED`); нет в карте — «не начал» |
| `surahCoverage(surah): Flow<SurahCoverage>` | `ayahCount`, `coveredAyahs`, `hardAyahs`, `passages` (по мусхафу), `isCurrent`, `status` |
| `reviewLog(fromDay, toDay): Flow<List<ReviewLogEntry>>` | записи за дни включительно, старые первыми (для итога урока и D2) |
| `oldWeight(): Flow<Int>` | сумма символов всех `OLD`-отрывков (вес круга манзиль); экран плана: «≈ N страниц в день» = `oldWeight / HifzConstants.CHARS_PER_PAGE / manzilDays` |
| `estimateMinutes(surah, startAyah, endAyah, isNew): Int` (`suspend`) | оценка отрывка в минутах, вверх, теми же формулами, что в уроке (`isNew` — учить впервые, иначе одно повторение); неверный диапазон — `IllegalArgumentException` |

Действия — `suspend`, **каждое = одна транзакция Room под одним `Mutex`**: чтение состояния, расчёт чистыми функциями, запись разницы, запись в журнал, зеркало. Любая ошибка откатывает всё. Параметр `today: Int` по умолчанию — `EpochDays.now()`, но экраны должны передавать `ticker.day.value`.

| Функция | Контракт |
|---|---|
| `completeNew(surah, startAyah, endAyah, grade, hardAyahs = {}, today): NewPassageResult` | создаёт отрывок (`R1`, срок завтра) + `NEW` в журнал. Перекрытие с покрытыми аятами — `NewPassageResult.Overlaps` (ничего не пишется), успех — `Created(passageId)`. Неверный диапазон (`IllegalArgumentException`) — ошибка вызывающего |
| `review(passageId, grade, hardAyahs = {}, today): ReviewResult?` | лестница или манзиль по этапу отрывка; журнал `RECENT` (лестница) или `OLD`; при переходе в `OLD` — слияние. Результат: `kind, stage, nextDueEpochDay, merged`; null — отрывка нет (уже слит/удалён) |
| `logCheck(surah, startAyah, endAyah, grade, hardAyahs = {}, today)` | «Проверь себя» (ридер): только `CHECK` в журнал (`passageId` — если один отрывок покрывает диапазон целиком, иначе null) и трудные аяты у покрывающих отрывков; **этапы и сроки не меняются** |
| `markKnown(surah, startAyah = 1, endAyah = LAST_AYAH, today)` | непокрытые аяты диапазона → `OLD`-отрывки, нарезанные по страницам мусхафа (аят — к странице, где начинается), `lastReviewed = null`; покрытые не трогает; журнал не пишется |
| `forget(surah, startAyah = 1, endAyah = LAST_AYAH)` | убирает аяты из отрывков: целиком попавшие исчезают, у края — укорачиваются, из середины — режутся надвое (левая часть сохраняет строку, правая — новая); остатки сохраняют этап, срок, `lapses`, отметки идут за номерами |
| `setPlan(scope, surahOrder, dailyNewAyahs, targetEpochDay = null, manzilDays = 7, today)` | задаёт план; `surahOrder` пустой — берётся пресет `scope`. `dailyNewAyahs` и `manzilDays` приводятся к диапазонам. `startedEpochDay` сохраняется, если очередь та же (меняют только темп/срок), иначе = `today` |
| `learnNext(surah, today)` | «Учу»: нет плана — `CUSTOM` из [сура], темп 5, манзиль 7; есть — сура первой в очереди, `scope = CUSTOM`, остальное сохраняется |
| `clearPlan()` | удаляет план (отрывки остаются) |
| `importLegacyIfNeeded()` | разовый перенос (ниже); при метке `hifz_meta.legacy_import = done` ничего не делает |

Пресеты: `HifzPresets.order(scope)` — `JUZ_30` = 114 → 78, `JUZ_29` = 77 → 67, `FROM_START` = 1 → 114, `CUSTOM` — пусто (список даёт пользователь).

### `LessonPlan` (`schedule/Lesson.kt`)

```kotlin
LessonPlan(
    today, hasPlan, planFinished,      // planFinished: план есть, все его суры покрыты (блок «Новое» при этом может быть DONE за сегодня)
    newBlock: LessonNew?,              // null — учить нечего; LessonNew(surah, startAyah, endAyah, state PENDING|DONE|DEFERRED, grade?, passageId?, estimateSeconds)
    recent: List<LessonItem>,          // сначала сделанные сегодня (в порядке выполнения), затем к выполнению — самое просроченное первым
    old: List<LessonItem>,             // порция манзиля по мусхафу (сделанные и нет)
    totalMinutes, remainingMinutes,    // оценка, округлена вверх; total = всё в уроке, remaining = ещё не сделанное
    catchingUp, deferredCount,         // не влезло в 25 минут: число отложенных (отрывки + новое); catchingUp = deferredCount > 0
    isComplete, isEmpty,               // isComplete: в уроке что-то есть и всё сделано (отложенное не в счёт); isEmpty: в уроке ничего нет
    tomorrow: LessonTomorrow,          // newPassage: AyahRange? (следующий новый по плану), dueCount (отрывков лестницы к сроку завтра или уже просроченных)
)
LessonItem(passageId, surah, startAyah, endAyah, stage, hardAyahs: Set<Int>, done, grade?, estimateSeconds, overdueDays)
```

- «Сделано» — по `review_log` за `today`: отрывок, повторённый сегодня как `RECENT`/`OLD`, остаётся в уроке выполненным с оценкой (даже если `FORGOT` перенёс его на завтра; отрывок, повторённый как `OLD` и ушедший в лестницу, остаётся в блоке «Старое»). `NEW` за сегодня — блок «Новое» `DONE` (второго нового сегодня не предлагаем).
- Отложенные не входят в списки (только в `deferredCount`); отложенное новое — `newBlock.state = DEFERRED`.
- Нет плана, но есть `OLD`-отрывки — порция манзиля всё равно есть (`manzilDays` = 7), по тому же правилу «раз в `manzilDays` дней».

### `EpochDayTicker` (`hifz/EpochDay.kt`)

`ticker.day: StateFlow<Int>` — текущий epochDay, сам переключается в полночь часового пояса устройства (таймер в корутине; `delay` до ближайшей полуночи + 250 мс). `ticker.refresh()` — перечитать часы сразу (**вызывай на `ON_RESUME`**: таймер мог проспать, дата и пояс — измениться). Koin `single`; в тестах — параметры `scope`, `clock`, `zone`.

### Перенос данных старых пользователей (`legacy/LegacyImport.kt`)

Один раз, из `QuranTodoApplication` в фоне (`HifzStartup`, ошибки — `AppTelemetry.logError(context = "hifz_legacy_import_failed")` без текста; метки нет — повторится при следующем запуске). Чистая функция `LegacyImport.run(input, metrics)` + выполнение в репозитории **одной транзакцией** (отрывки, трудные аяты, план, зеркало, метка), после коммита очищаются слабые аяты в prefs. Правила: суры `LEARNED` → `OLD` целиком; аяты `LEARNED` → `OLD`-отрывки; `LEARNING` и аяты с `ayah_review` без статуса → лестница (этап по `intervalIndex` 0..4 → R1..R30, ≥5 → `OLD`; срок — день `nextReviewAt`, может быть в прошлом; без `ayah_review` — `R1` сегодня; в отрывке — минимальные этап и срок); нарезка по страницам; глобальные номера → в суре; слабые ключи `"s:n"` (`n` в глобальном диапазоне суры — глобальный, иначе `1..ayahCount` — в суре, иначе отброшен), внутри отрывка — трудный аят, вне — отброшен; план `CUSTOM` из «заучиваемых» сур (все в 67..114 — по убыванию номера, иначе по возрастанию; темп = старая цель в 1..20, иначе 5). Зеркало при переносе пересчитывает только суры, получившие отрывки, и текущую суру плана: «заучиваемые» суры без покрытия остаются в `surah_todo` как были. Аяты `LEARNING` в старых таблицах после переноса — `LEARNED` (они стали отрывками лестницы).

### Что известно и ещё не сделано

- `ProgressRestoreDao.replaceUserProgress` (восстановление копии) пишет только старые таблицы и не знает об отрывках — его нужно перевести на репозиторий (D2/настройки), иначе после восстановления старые таблицы и отрывки разойдутся.
- Пока старый UI пишет `surah_todo`/`ayah_todo` напрямую, эти записи после переноса не создают отрывков; B2/B3/D2 переводят их на `HifzRepository`.
- Аналитика не добавлена (B2).

