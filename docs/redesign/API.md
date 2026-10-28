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
- `UiPrefs` (Koin single, `koinInject()` / `get()`): `themeFollowsSystem`, `hapticsEnabled`, `reduceMotion`, `keepScreenOn`, `readerLayout: ReaderLayout (LIST, MUSHAF)`, `hifzHintMode: HifzHintMode (NONE, FIRST_WORD)`, `onboardingGoals: Set<String>` (`OnboardingGoal.READ/LISTEN/MEMORIZE`), `recentSurahs: List<Int>` + `recordRecentSurah(n)`, `pinnedSurahs: Set<Int>` + `togglePin(n)`, `lastReadPosition: StateFlow<LastReadPosition?>` (`surah`, `ayahInSurah?`, `legacyGlobalAyah?` — если есть только старый ключ, номер аята глобальный, переводи через `completeQuran`: `AyahEntity.number` глобальный, `numberInSurah` — в суре), `saveLastRead(surah, ayahInSurah)` (debounce на стороне вызывающего). У каждого значения — `StateFlow` + `setX(...)`.
- `PlaybackController` (Koin single): `nowPlaying: StateFlow<NowPlaying?>` (`surahNumber`, `ayahs: List<ReaderAudioAyah>`), `state: StateFlow<ReaderAudioState>` (`index`, `isPlaying`, `canResume`, `isPreparing`, `pauseRemainingSeconds`, `positionMs`, `durationMs`, `hasError`), `isActiveFor(surah)`, `start(surah: SurahWithAyahs, prefs, practice, fromIndex, autoplay = true)`, `toggle()`, `next()`, `previous()`, `select(index)`, `updateSettings(prefs, practice)`, `stop()`. `start` всегда конфигурирует сессию без автозапуска при открытии, затем `select(fromIndex)` и `toggle()`, если `autoplay`.
- Тесты для androidMain-кода — `composeApp/src/androidUnitTest/kotlin/...` (kotlin.test доступен), запуск `./gradlew :composeApp:testDebugUnitTest`.
