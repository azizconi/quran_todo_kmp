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
| Орнамент | `EightPointStar(modifier, size, color, strokeWidth, fill)`, `AnimatedEightPointStar(…, drawMillis = 900, breathing = false)`, `AnimatedCheckmark(…)`, `SurahNumberBadge(number, status: SurahTodoStatus?, learnedFraction, modifier, size = 40.dp)`, `StarLattice(modifier, color, alpha = 0.07f, cell, scrollOffset: () -> Float, parallax = 0.2f)`, `OrnamentDivider()`, `SurahCartouche(modifier, background = readerBg) { BoxScope }`, `AyahRosette(number, modifier, status: AyahTodoStatus? = null, weak = false, size = 34.dp)` |

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
- **`PlaybackService : MediaSessionService`** (`foregroundServiceType="mediaPlayback"`, разрешения `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`). Создаётся, когда первый `start()` привязывает к нему `MediaController` (`MediaControllerLink`); дальше Media3 сам держит foreground и уведомление, пока играет (на паузе уведомление можно смахнуть). Сессия зеркалит контроллер: плейлист — аяты суры, текущий элемент — текущий аят, «играет» = последовательность идёт (в паузе между аятами тоже), «буферизация» = аят подготавливается, остановка/ошибка показываются как «пауза» (кнопка ▶ в уведомлении повторяет). Метаданные: заголовок «Ан-Наба · 5» (`SurahNames.displayName` по языку интерфейса), исполнитель — чтец, обложка — иконка приложения. Позиция экстраполируется системой, а не обновляется 5 раз в секунду. Малая иконка — `res/drawable/ic_stat_quran.xml` (восьмиконечная звезда; её же берут напоминания), канал `quran_playback`, нажатие открывает приложение (`setSessionActivity`).
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

