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

### Эхо-режим «Повторяю за чтецом» (F1b)

Чистый план в `common/audio/ReaderPlaybackSequence.kt` (`EchoPlan`, `nextReaderPlaybackStep`, `freshStep`), настройка — `ReaderPracticeSettings.echo: Boolean` и `echoRepeats ∈ {1, 2, 3}` (по суре, как пауза; старые сохранённые настройки читаются с `echo = false`).

- **План шагов.** Чтец читает аят → тишина `max(2 с, длительность × 1,2)`, при скорости ≠ 1 берётся то время, что аят занимает (`EchoPlan.silenceMs(durationMs, speed)`) → это один круг; на аят `echoRepeats` кругов, потом следующий. Тишина **заменяет** обычную паузу (`pauseSeconds`) и идёт **и после последнего аята** (человек ещё повторяет), только потом последовательность кончена. С повтором отрывка круги — на каждый аят, отрывок повторяется как прежде; без отрывка круги заменяют повтор каждого аята (бесконечный повтор остаётся бесконечным). Явный сценарий урока (`playScript`, «каждый аят ×3, потом отрывок ×2») своих кругов не имеет: тишина идёт после **каждого** чтения сценария. «Стоп после аята» (таймер сна) обрывает сразу, без тишины.
- **Сессия.** Тишина — та же корутинная задержка, что обычная пауза, поэтому `isPlaying` остаётся `true`: уведомление Media3 не пропадает и не мигает, `PARTIAL_WAKE_LOCK` сервиса держится, звук и тишина идут с выключенным экраном. `ReaderAudioState.pauseRemainingSeconds` — осталось, `pauseTotalSeconds` — вся тишина (кольцо вокруг ⏯ полное в начале и пустое в конце; `EchoRing` в «Послушай» и в листе «Слушать старое»; при Reduce Motion кольцо без анимации, шагает раз в секунду, число секунд — текстом «Ваша очередь · 4 сек»). ⏯ ставит тишину на паузу и снимает с остатка; ⏭/⏮ пропускают тишину и идут к соседнему аяту.
- **Где включается.** В доке ридера — секция «Эхо» (Нет / 1× / 2× / 3×) рядом с повтором отрывка и паузой (`ReaderViewModel.setEcho`), в строке состояния дока «… · Эхо 2×»; в «Послушай» урока — переключатель «Повторяю за чтецом» в листе настроек прослушивания (`UiPrefs.lessonEcho`, `PlaybackController.playSequence(…, echo)`); в «Слушать старое» — «Один раз / С эхом» (`UiPrefs.listenEcho`).

### Слушать старое: очередь (F1b)

`PlaybackController.startListenQueue(items, loadSurah, prefs, echo)`; состояние — `listenQueue: StateFlow<ListenQueue?>` (`items: List<ListenItem(passageId, surah, startAyah, endAyah)>`, `position`, `echo`, `heard`, `finished`).

- **Очередь = отрывки разных сур подряд тем же чтецом и скоростью.** Каждый отрывок играется режимом «отрывок» сессии (`rangeEnabled`, один проход, при `echo` — тишины); конец отрывка сессия сообщает через `ReaderAudioSession.onSequenceEnd` (после тишины последнего аята), контроллер в том же вызове привязывает следующую суру и запускает её, не отпуская фокус (`queueSwitching` гасит `releaseFocus`), поэтому уведомление не гаснет между отрывками. Плейлист медиа-сессии на время очереди — **аяты всех отрывков подряд** (`QueuePlaylist` в `PlaybackSessionPlayer`, элемент подписан своей сурой: «Ан-Наба · 5»), поэтому у ⏭ уведомления, экрана блокировки и гарнитуры всегда есть куда идти, в том числе с последнего аята суры (обычный плейлист — аяты одной суры — там упирался бы в конец, и ⏭ молчал). Media3 не различает ⏭ и двойное нажатие гарнитуры (обе — `SEEK_TO_NEXT`), поэтому ⏭ — следующий аят, а **на последнем аяте отрывка — первый аят следующего отрывка**; ⏮ на первом аяте отрывка — к предыдущему отрывку (на первом — тот же аят снова); кнопки «Предыдущий/Следующий отрывок» — в листе. Позицию «Продолжить» очередь не запоминает (`ReaderAudioSession.savesPosition = false`): старые отрывки — не место, откуда продолжать.
- **Чужие настройки не ломают отрывок:** `updateSettings` читателя во время очереди берёт только скорость. `start`/`playSequence` другого экрана и `stop` сбрасывают очередь; открытие ридера её не трогает, док ридера этой суры показывает «Старое 2/5 · чтец · Эхо 2×». Звонок и отключение наушников — как в ридере (`pauseBySystem`).
- **Лист `ui/listen/ListenOldSheet`** (`ListenOldViewModel`): отрывки порции `Manzil.portion` (из урока), «~N мин», «Прослушано ✓» / «Вспомнено ✓», текущий отрывок и аят, ⏯, предыдущий/следующий отрывок, «Один раз / С эхом», «Готово». Очередь играет и при закрытом листе. В конце (или по «Готово») — «Отметить: повторил на слух» → `HifzRepository.logListened` для отрывков, дослушанных **до конца** (`ListenQueue.heard`). Кнопка «Слушать старое · ~N мин» — на карточке «Урок дня» (`LessonCardUi.oldListen`, видна, если в порции есть отрывки) и в карточке «Старого» урока.

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

- `common/audio/Reciter.kt` (общий код): `Reciter(id, source, recitationId, group, имена)`; `source` — `ReciterSource.IslamicNetwork(edition, bitrate)` (`https://cdn.islamic.network/quran/audio/{bitrate}/{edition}/{globalAyah}.mp3`) или `ReciterSource.QuranCom(pathTemplate)` (шаблон `Sudais/mp3/{sss}{aaa}.mp3` → `https://verses.quran.com/…`; начинающийся с `//` — другой хост, `https:` добавляется: `//mirrors.quranicaudio.com/everyayah/…`). `recitationId` — номер записи в quran.com (`/resources/recitations`, 1..12): **тайминги слов** берутся по нему. `group` — `ReciterGroup.Teaching | Murattal | Mujawwad`. `reciter.audioUrl(surah, ayahInSurah)` и `reciter.audioUrl(globalAyah)` — **единственное** место, где строится адрес файла; `reciter.cacheKey(surah, ayah)`; `name(language)` (полное), `shortName(language)` (для строки в доке и строки настроек). `Reciters.all` (в порядке групп: «Для заучивания», затем муратталь с Аль-Афаси первым, затем муджаввад), `Reciters.inGroup(group)`, `Reciters.default` (Аль-Афаси), `Reciters.byId(id)` (неизвестный id → по умолчанию).
- **12 чтецов (F1a, 2026-10-30).** Первые четыре остались на CDN Islamic Network как были; остальные восемь — файлы quran.com:

| id | Чтец | Группа | Файлы | `recitationId` |
|---|---|---|---|---|
| `husary_muallim` | аль-Хусари «Для заучивания» (Muallim) | Teaching | `mirrors.quranicaudio.com/everyayah/Husary_Muallim_128kbps` | 12 |
| `alafasy` | Мишари Аль-Афаси (по умолчанию) | Murattal | Islamic Network 128, `ar.alafasy` | 7 |
| `husary` | Махмуд Халиль аль-Хусари | Murattal | Islamic Network 128, `ar.husary` | 6 |
| `minshawi` | Мухаммад Сиддик аль-Минышави | Murattal | Islamic Network 128, `ar.minshawi` | 9 |
| `abdulbasit` | Абдуль-Басит Абдус-Самад | Murattal | Islamic Network 192, `ar.abdulbasitmurattal` | 2 |
| `sudais` | ас-Судейс | Murattal | `verses.quran.com/Sudais/mp3` | 3 |
| `shatri` | аш-Шатри | Murattal | `verses.quran.com/Shatri/mp3` | 4 |
| `rifai` | ар-Рифаи | Murattal | `verses.quran.com/Rifai/mp3` | 5 |
| `shuraym` | аш-Шурейм | Murattal | `verses.quran.com/Shuraym/mp3` | 10 |
| `tablawi` | ат-Таблави | Murattal | `mirrors.quranicaudio.com/everyayah/Mohammad_al_Tablaway_128kbps` | 11 |
| `abdulbasit_mujawwad` | Абдуль-Басит (муджаввад) | Mujawwad | `verses.quran.com/AbdulBaset/Mujawwad/mp3` | 1 |
| `minshawi_mujawwad` | аль-Минышави (муджаввад) | Mujawwad | `verses.quran.com/Minshawi/Mujawwad/mp3` | 8 |

- **Решение по четырём прежним чтецам** (проверено 2026-10-30: файлы 112:1, 2:255, 18:10, 1:1 с нашего CDN и с quran.com, `afinfo` и посэмплово): Аль-Афаси, Аль-Минышави (`ar.minshawi` ↔ запись 9) и Абдуль-Басит (`ar.abdulbasitmurattal` ↔ запись 2) — **те же файлы байт в байт**, длительности равны (Аль-Афаси 2,978 / 51,958 / 23,380 / 6,087 с; Аль-Минышави 3,213 / 67,892 / 17,633 / 4,963 с; Абдуль-Басит 3,161 / 54,518 / 18,207 / 4,362 с). Аль-Хусари (`ar.husary` 128 кбит/с ↔ запись 6 `Husary_64kbps`) — **та же запись с другим битрейтом**: после декодирования длительности отличаются на 0–26 мс (112:1 — 5,825 против 5,799 с), начала слов совпадают в пределах ±15 мс — тайминги подходят. Поэтому у всех четырёх `id`, файлы, адреса и ключи кэша **не менялись**, загрузки людей сохранены; тайминги берутся по `recitationId` quran.com.
- **Ключ кэша:** у Аль-Афаси прежний `ayah_{s}_{a}` (уже скачанное не пропадает), у остальных `{id}_{s}_{a}` (`husary_78_5`, `husary_muallim_78_5`).
- Вшитые URL в `HomeViewModel` (оффлайн-пакет, качает Аль-Афаси, его переделает «Загрузки») и в старом `HifzSessionScreen` (общий код) не менялись; `cachedAudioSource()` в `ReaderAudioSession` берёт `Reciters.default.audioUrl(...)` — тот же URL и ключ, что были.
- `UiPrefs.reciter: StateFlow<Reciter>` + `setReciter(reciter)` (SharedPreferences, хранится `id`). Смена чтеца во время звука: аят, который играет (или стоит на паузе посередине), начинается заново новым голосом; без звука — новый голос на следующем `play`.
- Предзагрузка: когда аят готов, `AyahAudioSource` качает в кэш два следующих (`prefetch`), чтобы переход был без щелчка; воспроизведение идёт из файла кэша.
- Настройки → «Звук» → «Чтец» (`ui/settings/SoundSettings.kt`): лист на 12 чтецов тремя группами («Для заучивания» с пояснением «медленно, с паузами для повтора», «Чтение (муратталь)», «Распевное чтение (муджаввад)»), в каждой карточке — стиль/темп (`AudioCopy.styleOf`), «▶ образец» (112:1) и галочка. Образец играет `PlaybackController.playSample(source, onFinished)`; в самом листе — `rememberReciterPreview()`; работает у всех 12.
- **Размер в «Загрузках»:** `DownloadSize` держит коэффициент «байт на символ» для каждого из 12 (замерены `Content-Length` у восьми новых: весь Джуз 30 и каждый 9-й аят остального Корана, 1194 файла на чтеца; таблица в KDoc). Джуз 30: Шурейм 30 МБ … аль-Минышави (муджаввад) 119 МБ.

### Подсветка слов (F1a)

- **Тайминги** (`common/audio/timing`): `WordTimings(recitationId, surah, ayahs)` — на аят список `WordSpan(wordIndex, startMs, endMs)`; `WordTimingsFormat.parseQuranCom(...)` читает ответ quran.com `GET /recitations/{id}/by_chapter/{surah}?fields=segments&per_page=300` (ОДИН запрос на суру), `encode/decode` — наша компактная копия (`{"v":1,"r":7,"s":112,"a":{"1":[слово,начало,конец,…]}}`). Сегмент quran.com — `[первое слово с 0, последнее слово позиция с 1, начало мс, конец мс]`; у ас-Судейса числа приходят строками; объединённые сегменты (`[1,4,…]`) режутся поровну на слова; слова без сегмента остаются без отрезка; «конец раньше начала» и откат начал исправляются на разборе.
- **Слова аята** — `displayWords(surah, ayah, text)` в `ui/reader/Bismillah.kt`: показываемый текст (`splitBismillah`: без Бисмилляха первого аята, без ۞) и диапазоны символов слов (`wordRanges`: токены между пробелами, в которых есть буква; отдельные знаки остановки ۖ ۚ не слова). Проверено на всём Коране: 77 430 слов, как у quran.com; из 6236 аятов совпадение с таймингами у 6233, исключения — 10:1 и 13:1 (у quran.com знак после الٓر в тайминге считается словом) и 50:34 (тайминг обрывается на 3-м слове из 5). `usableSpans(spans, wordCount)`: если отрезки ссылаются на слово, которого нет в аяте, или покрывают меньше 60 % слов — подсветки у этого аята нет; `wordAt(spans, positionMs)` — слово последнего начавшегося отрезка (в паузах подсветка не мигает).
- **Хранение** (`WordTimingsStore.shared`, androidMain): память (4 суры) → закреплённая копия `downloads/timings_{rid}_{surah}.json` (кладёт загрузка суры, `DownloadRunner` через `TimingsSource`; удаляется вместе с загрузками) → кэш `offline_audio/timings_{rid}_{surah}.json` (рядом с аудио, чистится «Очистить кэш») → сеть (в кэш). Нет сети и копии — `load` = null, подсветки нет, ошибок на экран не выводим. Неудачный запрос не повторяется 30 с (`FailureThrottle`), загрузки этим не задерживаются. Схема Room не менялась.
- **Часы:** `PlaybackController.positionMs()` читает позицию у плеера прямо сейчас (в `state.positionMs` она обновляется 5 раз в секунду — мало для слов), `positions(intervalMs = 60)` — поток раз в 60 мс (~16 раз в секунду). Подсветку рисует только текущий аят (`FollowedArabicText` в `ui/playback/WordHighlight.kt`): опрос идёт только пока играет и экран виден (`repeatOnLifecycle(STARTED)`), позиция живёт в состоянии, которое читает только рисование (`drawBehind`), поэтому список не перекомпоновывается. Скорость не требует поправок: позиция плеера — позиция в записи.
- **Перемотка:** `PlaybackController.seekInAyah(ms)`, `playAyahFrom(index, ms)` (select + toggle + «следующий аят начать с ms»: `OutputExtras.startNextAt`, применяется один раз, сбрасывается `stop()`); у `ExoReaderAudioOutput` перемотка не действует, пока аят не загружен, после его конца и на образце чтеца. `ReaderViewModel.onWordTap(index, startMs)`: звучит аят — перемотка на слово; пауза посередине — перемотка и продолжение; пауза между аятами, другой аят или тишина — аят играет с этого слова.
- **Где видно:** ридер (`AyahRow(follow = …)` у играющего / стоящего на паузе / выбранного аята), шаг «Послушай» и «Прочитай» урока (`PassageList(follow = …)`), карточка «Сейчас играет» дока. Настройка «Подсветка слов при слушании» — `UiPrefs.highlightWords` (по умолчанию вкл.), переключатель в листе «Aa». Подложка слова — `primary` с прозрачностью 0,20 (светлая) / 0,34 (тёмная тема), перекрёстное затухание 100 мс, без перехода при «Меньше движения».
- **С эхом и очередью (проверено на эмуляторе, As-Sudais, 112, «Эхо 2×»).** Часы читают позицию у плеера, а `ExoReaderAudioOutput.currentPositionMs()` отдаёт 0, когда аят закончился (у вывода нет источника): в тишине эха подсветка гаснет (перекрёстное затухание), при повторе аята начинается с нуля и идёт с первого слова. Очередь «Слушать старое» играет тем чтецом, что выбран (любым из 12), через тот же `AyahAudioSource` (файлы `sudais_102_N.mp3` в кэше), уведомление показывает суру и чтеца; подсветки в очереди нет (у неё нет текста на экране). Тап по слову во время тишины между аятами/эха запускает аят заново с этого слова (`ReaderViewModel.onWordTap` смотрит на `pauseRemainingSeconds`).

### Кэш аудио

`common/audio/AudioCacheStorage` (Android): файлы `*.mp3` в `noBackupFilesDir/offline_audio` — **кэш прослушивания**, **лимит 1 ГБ**; когда новый файл выводит общий размер за лимит, удаляются самые давно использованные (время — `lastModified`, обновляется при каждом попадании в кэш) до 90 % лимита; только что записанный файл не удаляется. Файлы старого оффлайн-пакета остались здесь и вытесняются как все. Размер — `AudioCacheStorage.shared.totalBytes()`, очистка — `clear()` (экран «Загрузки и хранилище» → «Кэш прослушивания · 230 МБ · Очистить» с подтверждением; в настройках звука строки «Аудио на устройстве» больше нет). Правило вытеснения — чистая функция `selectEvictions(...)`, у неё юнит-тесты. Кэш **не трогает** загрузки: они лежат в другой папке.

**Порядок поиска файла аята** (`common/audio/AudioFileStore`, его берёт `actual class AudioCache`, а через него `AyahAudioSource`, предзагрузка, образец чтеца): 1) **загрузки** (`noBackupFilesDir/downloads/`, `DownloadStorage`), 2) кэш (попадание обновляет время использования), 3) сеть — файл скачивается **в кэш**. Воспроизведение никогда не пишет в загрузки. `AudioCache.clear(key)` (файл не проигрался) удаляет и загрузку, и кэш. `prefetch` не качает то, что уже есть в загрузках.

### Таймер сна

`PlaybackController.setSleepTimer(SleepTimer)`; `sleepTimer: StateFlow<SleepTimer>`, `sleepRemainingSeconds: StateFlow<Int>`.

- `SleepTimer.Off`, `SleepTimer.EndOfSurah` (пауза, когда доиграет **последний аят суры**, даже если он повторяется; поставленный без звука действует на следующую запущенную суру), `SleepTimer.Minutes(n)` (обратный отсчёт; `sleepRemainingSeconds` — остаток).
- Сработав, таймер ставит звук на паузу (`canResume`) и сам возвращается в `Off`; `stop()` тоже сбрасывает таймер.
- Отсчёт идёт в `applicationScope` контроллера, поэтому работает при выключенном экране (пока держится wake lock сервиса).

## Загрузки и хранилище (`ui/downloads`, `common/audio/DownloadStorage`, задача D3)

Экран `Dest.Downloads` (вход — одна строка «Загрузки и хранилище · 230 МБ» в настройках, вместо «Скачать для офлайн» и «Аудио на устройстве»; `HomeViewModel.startOfflinePackage` остался для iOS, Android его больше не зовёт). Скачивает аудио чтеца из настроек звука (`UiPrefs.reciter`) и, по переключателю, перевод на язык интерфейса — тем же путём, что ридер (`GetAyahTranslationsUseCase`, сохраняется в Room).

- **Хранилище.** `DownloadStorage` (`noBackupFilesDir/downloads/`): файлы `reciter.cacheKey(surah, ayah)` + `.mp3`, пишутся как `*.mp3.download` и переименовываются (`commit`); `snapshot()` — ключ → байты; `revision` меняется при каждом изменении набора файлов. LRU-кэш (A2) сюда не заглядывает: загрузки не вытесняются, удаляет их только человек.
- **Выбор → аяты** (`DownloadSelection`, чистая): `surah(n)`, `juz(n)` (`JuzIndex`; крайние суры частичные), `planSurahs(plan, statuses)` («Мой план»: суры очереди плана, которые учатся или выучены, плюс первая невыученная — чтобы свежий план предлагал первую суру; плана нет — раздел скрыт). `uniqueKeys(requests)` — без дублей при пересечении (джуз + сура внутри него + план).
- **Размер заранее** (`DownloadSize`, чистая): `байты = символы × коэффициент чтеца`; символы — как в `TextMetrics` (`LENGTH(text)`), без Басмалы перед первым аятом суры (в записи её нет). Коэффициенты измерены по `Content-Length` файлов CDN: весь Джуз 30 (564 аята) и каждый 9-й аят остального Корана у каждого из 4 чтецов; у Джуз 30 свой коэффициент (суры 78–114 читаются медленнее). Таблица и точность — в KDoc `DownloadSize`. Джуз Амма у Аль-Афаси — 51 МБ; весь Коран ≈ 1,6 ГБ.
- **Состояние строки** (`rowState(keys, files)`): `NotDownloaded` / `Partial(done, total, bytes)` / `Complete` — только по файлам на диске.
- **Очередь** (`DownloadQueue`, файл `noBackupFilesDir/downloads_state/queue.json`, атомарная запись): `QueueState(requests, paused, failures)`; запрос — `DownloadRequest(id, kind PLAN|JUZ|SURAH, title, reciterId, language?, segments)`, целиком, поэтому после смерти процесса воркеру ничего больше не нужно. Действия: `enqueue` (тот же id не ставится дважды; упавший — повтор), `pause`/`resume` («Пауза всего»), `cancel(id)`, `cancelAll`, `complete`, `fail(id, ayahs, translations)`, `retryFailed`. Упавший запрос остаётся в очереди с `RequestFailure`, воркер его пропускает до «Повторить».
- **Воркер** (`DownloadWorker : CoroutineWorker`, уникальная работа `quran-downloads`, `ExistingWorkPolicy.REPLACE`; `WorkManagerScheduler`): ограничение `UNMETERED` («Только по Wi-Fi», по умолчанию) или `CONNECTED`; backoff экспоненциальный от 30 с, до 5 попыток, потом запрос помечается упавшим. Внутри `DownloadRunner` (без Android): перевод суры → аудио; скачанное пропускается; перед каждым файлом читает очередь (пауза и отмена действуют сразу); аят пробуется 2 раза; 3 неудачи подряд → `NO_NETWORK` (воркер ждёт сети и повторяется). `setForeground` с уведомлением «Загрузка: Джуз Амма · 120 из 564» + «Отменить» (канал `downloads`, малая важность, иконка `ic_stat_quran`, язык приложения); тип FGS `dataSync` (`FOREGROUND_SERVICE_DATA_SYNC` + `SystemForegroundService` с `tools:node="merge"` в манифесте); если Android не даёт стартовать FGS из фона — работа идёт без него и перезапускается. Кнопка «Отменить» — `DownloadActionReceiver` (не exported) → `DownloadsManager.cancelAll()`. Разрешение на уведомления просится один раз, при первой загрузке.
- **Koin.** `downloadsUiModule`: `single { DownloadsManager.create(androidContext()) }` (очередь, хранилище, переключатели `DownloadsPrefs`, планировщик; воркер берёт его из Koin) и `viewModel { DownloadsViewModel(...) }` — общие `HomeViewModel`/`HifzRepository` приходят через конструктор.
- **Аналитика** (без свободного текста): `download_started` (`kind` = plan|juz|surah, `size_mb_bucket` = lt_1|1_10|10_50|50_100|100_500|500_plus), `download_finished` (`result` = done|failed|cancelled), `downloads_deleted`.
- **Тесты** (`androidUnitTest`): `ui/downloads/*` (выбор, размер на реальном тексте, состояние строки, очередь, `DownloadRunner`: пропуск скачанного, продолжение после «смерти процесса», пауза, отмена, перевод до аудио, повторы), `common/audio/AudioFileStoreTest` (загрузки → кэш → сеть, LRU не трогает загрузки), `DownloadStorageTest`, `ui/i18n/DownloadsCopyTest`.

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
- **Таблицы** (БД v11, миграция `MIGRATION_10_11` — только `CREATE TABLE/INDEX`, `schemas/.../11.json`): `hifz_plan` (одна строка `id = 1`; нет плана — нет строки), `hifz_passage`, `passage_hard` (FK на отрывок, `ON DELETE CASCADE`), `review_log` (**без FK**: история переживает удаление/слияние отрывка), `hifz_meta` (`legacy_import = done`; `plan_pauses` и `plan_start_base` — пауза плана, F2b, см. «Пауза плана»).
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
| `LESSON_BUDGET_MINUTES` | 25 | потолок блока **«Недавнее»** (лестницы R1…R30); «Старое» и «Новое» он не режет (D4) |
| `OLD_TARGET_MINUTES` | 10 | сколько минут в день должно стоить «Старое»: `Manzil.suggestedDays` подбирает под это длину круга (D4) |
| `CHARS_PER_PAGE` | **1184** | средняя страница мусхафа вшитого текста: 715 000 знаков / 604 страницы = 1183.8 (медиана 1187); публичная, для «≈ N страниц в день» = `oldWeight / CHARS_PER_PAGE / manzilDays` |
| `MAX_OLD_PASSAGE_CHARS` | **1200** | `CHARS_PER_PAGE`, округлено: потолок веса слитого `OLD`-отрывка |
| `HARD_CLEARS_AFTER` | 2 | |
| темп / манзиль | 1..20 (по умолчанию 5) / 3..30 дней (по умолчанию 7) | |

Оценка, секунды: `chars / 5.7 × множитель + 15` (`HifzConstants.newSeconds(chars)`, `reviewSeconds(chars)`). Вес отрывка = число символов (code points) его арабского текста = `LENGTH(text)` в SQLite. Метрики (`TextMetrics`: `surah, ayah, page, chars` на каждый аят) читает репозиторий одним запросом к `ayahs` и кэширует (6236 строк); перед чтением вызывает `QuranRemoteRepository.ensureQuranInstalled()` (вшитый текст ставится оффлайн).

### Расписание (чистые функции, `schedule/`)

- **Манзиль** (`Manzil`): вес отрывка = символы; квота дня = `ceil(сумма весов всех OLD / manzilDays)`. В порцию дня годится только **подходящий** отрывок: ни разу не повторялся или `today - lastReviewed >= manzilDays`. Порция дня: сначала то, что **уже повторено сегодня как OLD** (по `review_log`, засчитывается в квоту — даже если `HARD/FORGOT` вернули отрывок в лестницу), затем подходящие `OLD` по приоритету: `lastReviewed` по возрастанию (null — самые старые), при равенстве — порядок мусхафа; берём, пока квота не набрана (перешагнувший квоту берётся целиком). В уроке порция — по мусхафу. Квота набрана или подходящих нет — блока «Старое» нет. Гарантия: **каждый отрывок повторяется раз в `manzilDays` дней, не чаще** (один `OLD`-отрывок — раз в `manzilDays` дней, а не каждый день; отрывок, который сегодня вышел из лестницы в `OLD`, сегодня снова не предлагается); квота растягивает накопившийся долг (например, сразу после переноса все отрывки «ни разу не повторялись») на следующие дни. Порция не «прыгает» в течение дня; новый `OLD` в середине круга ничего не ломает. Проверено симуляцией на 30 дней: первая очередь — в первые `manzilDays` дней, интервалы между повторениями не меньше `manzilDays` (в симуляции ровно `manzilDays`). **Порция идёт в урок целиком**: бюджет её не режет (D4).
- **Длина круга** (`Manzil`, чистые функции, D4): `minutesPerDay(oldWeightChars, days)` — оценка минут в день: день = `ceil(вес / days)` символов, это карточки по странице (`CHARS_PER_PAGE`), каждая стоит `HifzConstants.reviewSeconds`; округление до ближайшей минуты, ≥ 1, 0 при весе 0 (Джуз Амма 22 102 знака: 19 / 10 / 5 мин при 7 / 14 / 30 днях). `suggestedDays(oldWeightChars)` — длина круга, при которой день ближе всего к `OLD_TARGET_MINUTES` = 10, в пределах 7…30 (при равенстве — короче), нет старого — 7. Только предложение: сохранённый план никогда не меняется молча.
- **Новое** (`NewPassagePlanner`): сура из очереди плана; отрывок от первого непокрытого аята, `dailyNewAyahs` аятов, не переходя покрытый аят и конец суры. Нетронутая сура длиной ≤ `dailyNewAyahs × 1.5` — вся. Если после отрывка до конца суры остаётся ≤ `dailyNewAyahs / 2` непокрытых подряд — забираем и их.
- **Бюджет** (`LessonBuilder`, D4): 25 минут режут **только «Недавнее»** (лестницу R1…R30): самое просроченное первым, не влезло — на следующие дни вместе со всем, что идёт после. «Сделано сегодня» (по журналу) всегда в уроке и **первым** списывает бюджет Недавнего, поэтому завершение карточки ничего не втягивает из отложенного и не выкидывает уже входящее; первое повторение входит всегда. Бюджет Недавнего **не зависит** от «Старого» и «Нового» (выучил новое — Недавнее не сжалось). **«Старое» (порция манзиля) идёт в урок целиком**, сделанное и несделанное: это обещание «ничего не забыть», его размер держит длина круга (`manzilDays`, `Manzil.suggestedDays`), а не бюджет. **«Новое» откладывается целиком только если было урезано недавнее** (реальный долг — сначала догоняем); иначе оно в уроке, даже если урок вышел за 25 минут: старое больше не заставляет новое ждать. `catchingUp` и `deferredCount` считают только срез недавнего и отложенное из-за него новое; старое их не трогает. `totalMinutes` может быть больше 25 и при `catchingUp`, и без него.
- **Прогноз** (`Forecast`): без срока: `finishEpochDay = start + ceil(остаток / dailyNewAyahs)`, где `start` — сегодня или завтра, если новое сегодня уже сделано; со сроком: `requiredPerDay = ceil(остаток / дней до срока)` (дней ≥ 1), `behindAyahs` = «ожидаемое по прямой от `startedEpochDay` до срока минус фактическое, если > 0» — отсчёт от аятов, которые остались к старту плана (остаток + выученное с тех пор по `NEW` в журнале), так что ранее выученное не маскирует отставание. **В паузе (F2b)** `Forecast.compute(..., pause, learnedFromDay)` без срока считает старт от `until + 1`, со сроком — дни до срока и прошедшие дни тоже от `until + 1`; срок и старт плана уже сдвинуты паузой, а выученное считается с настоящего старта плана (`plan_start_base`), иначе первые уроки выпали бы из счёта и пауза выглядела бы отставанием.
- **Серия** (`Streak`): дни подряд с ≥ 1 записью **занятия** (`NEW/RECENT/OLD/CHECK`, не `LISTEN`) в `review_log`, оканчивающиеся сегодня или вчера (сегодня ещё не занимался — серия жива). Дни паузы (F2b) — **мост**: день паузы без записи не добавляет к серии и не рвёт её (занимался 1–3, пауза 4–6, сегодня 7 без занятий — серия 3, жива; занятие 7 — серия 4). `Streak.compute(days, today, pauses)`.

### `HifzRepository`

Чтение — `Flow`, пересчитываются при изменении таблиц (Room invalidation) и, у принимающих `today`, при смене дня. Варианты с `today: Flow<Int>` берут `EpochDayTicker.day`. Результат читается одной read-транзакцией (таблицы согласованы между собой), одинаковые значения подряд не эмитятся.

| Функция | Контракт |
|---|---|
| `lesson(today: Int \| Flow<Int>): Flow<LessonPlan>` | «Урок дня» (ниже) |
| `plan(): Flow<HifzPlan?>` | активный план (`scope`, `surahOrder`, `dailyNewAyahs`, `targetEpochDay?`, `startedEpochDay`, `manzilDays`); null — нет плана |
| `forecast(today): Flow<PlanForecast?>` | `totalAyahs, remainingAyahs, dailyNewAyahs, finished, finishEpochDay?, targetEpochDay?, requiredPerDay?, behindAyahs`; null — нет плана |
| `forecast(plan: HifzPlan, today: Int): Flow<PlanForecast>` | прогноз **черновика** плана (экран «Мой план» показывает его вживую): как `forecast(today)`, но по переданному плану; ничего не сохраняет, `startedEpochDay` берётся из черновика |
| `streak(today): Flow<Int>` | серия дней (дни паузы — мост) |
| `pause(today: Int \| Flow<Int>): Flow<PlanPause?>` | пауза плана (F2b), которая идёт или ещё впереди; прошедшая — null. Сегодняшний день внутри паузы — `lesson(today).paused != null` |
| `surahStatuses(): Flow<Map<Int, SurahHifzStatus>>` | только суры со статусом (`LEARNING`/`LEARNED`); нет в карте — «не начал» |
| `coveredCounts(): Flow<Map<Int, Int>>` | сколько аятов каждой суры покрыто отрывками (в карте только суры хотя бы с одним аятом): одна подписка на весь список сур («выучено 23 из 40» в строке) вместо 114 `surahCoverage` |
| `surahCoverage(surah): Flow<SurahCoverage>` | `ayahCount`, `coveredAyahs`, `hardAyahs`, `passages` (по мусхафу), `isCurrent`, `status` |
| `reviewLog(fromDay, toDay): Flow<List<ReviewLogEntry>>` | записи **занятия** (`NEW/RECENT/OLD/CHECK`) за дни включительно, старые первыми (для итога урока и D2). `LISTEN` сюда **не попадает** (фильтр в SQL) |
| `listenLog(fromDay, toDay): Flow<List<ReviewLogEntry>>` | записи `LISTEN` («повторил на слух», F1b) за дни включительно: «Прослушано ✓» на карточке и строка «Прослушано: N» в «Прогрессе». Больше их никто не читает |
| `oldWeight(): Flow<Int>` | сумма символов всех `OLD`-отрывков (вес круга манзиль); экран плана: «≈ N страниц в день» = `oldWeight / HifzConstants.CHARS_PER_PAGE / manzilDays`, «≈ N минут в день» = `Manzil.minutesPerDay(oldWeight, manzilDays)`; онбординг: `manzilDays = Manzil.suggestedDays(oldWeight)` |
| `estimateMinutes(surah, startAyah, endAyah, isNew): Int` (`suspend`) | оценка отрывка в минутах, вверх, теми же формулами, что в уроке (`isNew` — учить впервые, иначе одно повторение); неверный диапазон — `IllegalArgumentException` |

Действия — `suspend`, **каждое = одна транзакция Room под одним `Mutex`**: чтение состояния, расчёт чистыми функциями, запись разницы, запись в журнал, зеркало. Любая ошибка откатывает всё. Параметр `today: Int` по умолчанию — `EpochDays.now()`, но экраны должны передавать `ticker.day.value`.

| Функция | Контракт |
|---|---|
| `completeNew(surah, startAyah, endAyah, grade, hardAyahs = {}, today): NewPassageResult` | создаёт отрывок (`R1`, срок завтра) + `NEW` в журнал. Перекрытие с покрытыми аятами — `NewPassageResult.Overlaps` (ничего не пишется), успех — `Created(passageId)`. Неверный диапазон (`IllegalArgumentException`) — ошибка вызывающего |
| `review(passageId, grade, hardAyahs = {}, today): ReviewResult?` | лестница или манзиль по этапу отрывка; журнал `RECENT` (лестница) или `OLD`; при переходе в `OLD` — слияние. Результат: `kind, stage, nextDueEpochDay, merged`; null — отрывка нет (уже слит/удалён) |
| `logListened(passageIds, today)` | «Слушать старое» → «Отметить: повторил на слух» (F1b): по записи `LISTEN` (оценка `GOOD` — нейтральная, её никто не читает) на каждый существующий отрывок (неизвестный id пропускается). **Ничего больше не меняется**: ни этап, ни срок, ни `lastReviewed`, ни трудные аяты, ни зеркало. Оценку «Помню / Трудно» по-прежнему даёт только «Вспомни» |
| `logCheck(surah, startAyah, endAyah, grade, hardAyahs = {}, today)` | «Проверь себя» (ридер): только `CHECK` в журнал (`passageId` — если один отрывок покрывает диапазон целиком, иначе null) и трудные аяты у покрывающих отрывков; **этапы и сроки не меняются** |
| `markKnown(surah, startAyah = 1, endAyah = LAST_AYAH, today)` | непокрытые аяты диапазона → `OLD`-отрывки, нарезанные по страницам мусхафа (аят — к странице, где начинается), `lastReviewed = null`; покрытые не трогает; журнал не пишется |
| `forget(surah, startAyah = 1, endAyah = LAST_AYAH)` | убирает аяты из отрывков: целиком попавшие исчезают, у края — укорачиваются, из середины — режутся надвое (левая часть сохраняет строку, правая — новая); остатки сохраняют этап, срок, `lapses`, отметки идут за номерами |
| `setPlan(scope, surahOrder, dailyNewAyahs, targetEpochDay = null, manzilDays = 7, today)` | задаёт план; `surahOrder` пустой — берётся пресет `scope`. `dailyNewAyahs` и `manzilDays` приводятся к диапазонам. `startedEpochDay` сохраняется, если очередь та же (меняют только темп/срок), иначе = `today` |
| `learnNext(surah, today)` | «Учу»: нет плана — `CUSTOM` из [сура], темп 5, манзиль 7; есть — сура первой в очереди, `scope = CUSTOM`, остальное сохраняется |
| `clearPlan()` | удаляет план (отрывки остаются) |
| `markHard(surah, ayahs, today)` | «Проверка» (F2b): аяты (номера в суре) становятся трудными (`clearsAfter = 2`) у покрывающих отрывков (то же правило, что `logCheck`, но без записи в журнал); непокрытые пропускаются; одна транзакция, зеркало суры. **Ничего больше не меняется**: ни этап, ни срок, ни `lastReviewed`, ни журнал, ни серия. Неизвестная сура — `IllegalArgumentException` |
| `pausePlan(days, today)` | пауза плана на 1..14 дней с сегодня (F2b), одна транзакция: сдвигает на `days` `nextDueEpochDay` лестницы, `lastReviewedEpochDay` отрывков, у которых он есть (`null` остаётся), `targetEpochDay` и `startedEpochDay` плана; пишет паузу в `hifz_meta`. Идущая пауза **продлевается**: новый конец `today + days − 1` (не дальше `today + 13`), сдвиг — на разницу концов |
| `resumePlan(today)` | «Продолжить сейчас» (F2b): сдвигает всё назад на оставшиеся дни (`until − today + 1`), пауза заканчивается вчера; началась сегодня — удаляется целиком. Сделанное сегодня остаётся. Нет паузы — ничего |
| `importLegacyIfNeeded()` | разовый перенос (ниже); при метке `hifz_meta.legacy_import = done` ничего не делает |

Пресеты: `HifzPresets.order(scope)` — `JUZ_30` = 114 → 78, `JUZ_29` = 77 → 67, `FROM_START` = 1 → 114, `CUSTOM` — пусто (список даёт пользователь).

### `LessonPlan` (`schedule/Lesson.kt`)

```kotlin
LessonPlan(
    today, hasPlan, planFinished,      // planFinished: план есть, все его суры покрыты (блок «Новое» при этом может быть DONE за сегодня)
    newBlock: LessonNew?,              // null — учить нечего; LessonNew(surah, startAyah, endAyah, state PENDING|DONE|DEFERRED, grade?, passageId?, estimateSeconds)
    recent: List<LessonItem>,          // сначала сделанные сегодня (в порядке выполнения), затем к выполнению — самое просроченное первым
    old: List<LessonItem>,             // порция манзиля по мусхафу целиком (сделанные и нет); бюджет её не режет
    totalMinutes, remainingMinutes,    // оценка, округлена вверх; total = всё в уроке (может быть больше 25), remaining = ещё не сделанное
    catchingUp, deferredCount,         // не влезло в 25 минут Недавнего: число отложенных повторений лестницы + новое, отложенное за ними; старое не считается; catchingUp = deferredCount > 0
    isComplete, isEmpty,               // isComplete: в уроке что-то есть и всё сделано (отложенное не в счёт); isEmpty: в уроке ничего нет
    tomorrow: LessonTomorrow,          // newPassage: AyahRange? (следующий новый по плану), dueCount (отрывков лестницы к сроку завтра или уже просроченных)
    paused: PlanPause?,                // F2b: сегодня внутри паузы плана; урок тогда пуст (isEmpty), tomorrow — первый день после паузы
)
LessonItem(passageId, surah, startAyah, endAyah, stage, hardAyahs: Set<Int>, done, grade?, estimateSeconds, overdueDays)
```

- «Сделано» — по `review_log` за `today`: отрывок, повторённый сегодня как `RECENT`/`OLD`, остаётся в уроке выполненным с оценкой (даже если `FORGOT` перенёс его на завтра; отрывок, повторённый как `OLD` и ушедший в лестницу, остаётся в блоке «Старое»). `NEW` за сегодня — блок «Новое» `DONE` (второго нового сегодня не предлагаем).
- Отложенные не входят в списки (только в `deferredCount`); отложенное новое — `newBlock.state = DEFERRED`.
- Нет плана, но есть `OLD`-отрывки — порция манзиля всё равно есть (`manzilDays` = 7), по тому же правилу «раз в `manzilDays` дней».
- Как работает бюджет (D4): урок = Недавнее (≤ 25 минут, остальное — на следующие дни) + Старое (порция целиком) + Новое (откладывается только за срезанным Недавним).

### `EpochDayTicker` (`hifz/EpochDay.kt`)

`ticker.day: StateFlow<Int>` — текущий epochDay, сам переключается в полночь часового пояса устройства (таймер в корутине; `delay` до ближайшей полуночи + 250 мс). `ticker.refresh()` — перечитать часы сразу (**вызывай на `ON_RESUME`**: таймер мог проспать, дата и пояс — измениться). Koin `single`; в тестах — параметры `scope`, `clock`, `zone`.

### Перенос данных старых пользователей (`legacy/LegacyImport.kt`)

Один раз, из `QuranTodoApplication` в фоне (`HifzStartup`, ошибки — `AppTelemetry.logError(context = "hifz_legacy_import_failed")` без текста; метки нет — повторится при следующем запуске). Чистая функция `LegacyImport.run(input, metrics)` + выполнение в репозитории **одной транзакцией** (отрывки, трудные аяты, план, зеркало, метка), после коммита очищаются слабые аяты в prefs. Правила: суры `LEARNED` → `OLD` целиком; аяты `LEARNED` → `OLD`-отрывки; `LEARNING` и аяты с `ayah_review` без статуса → лестница (этап по `intervalIndex` 0..4 → R1..R30, ≥5 → `OLD`; срок — день `nextReviewAt`, может быть в прошлом; без `ayah_review` — `R1` сегодня; в отрывке — минимальные этап и срок); нарезка по страницам; глобальные номера → в суре; слабые ключи `"s:n"` (`n` в глобальном диапазоне суры — глобальный, иначе `1..ayahCount` — в суре, иначе отброшен), внутри отрывка — трудный аят, вне — отброшен; план `CUSTOM` из «заучиваемых» сур (все в 67..114 — по убыванию номера, иначе по возрастанию; темп = старая цель в 1..20, иначе 5). Зеркало при переносе пересчитывает только суры, получившие отрывки, и текущую суру плана: «заучиваемые» суры без покрытия остаются в `surah_todo` как были. Аяты `LEARNING` в старых таблицах после переноса — `LEARNED` (они стали отрывками лестницы).

### Перенос и копия (`hifz/backup/`, задача B5)

Резервная копия — **файл** `quran-hifz-ГГГГ-ММ-ДД.json`, который человек сохраняет или отправляет себе (Telegram, Google Диск) и импортирует на этом или другом телефоне. Старая «облачная» копия в prefs (`common/sync`, `HomeViewModel.syncProgressToCloud/restoreProgressFromCloud`) осталась только для iOS; Android-экран настроек её не вызывает.

- **Формат** (`BackupFormat.kt`): `format = "quran-hifz-backup"`, `formatVersion = 1`, `appVersion`, `exportedAt` (мс), `dbVersion = 11`; `hifz { plan?, passages[], hard[], reviewLog[], meta{} }`, `notes[]` (`surah` + `ayah` **в суре**), `settings{}` (все поля необязательны: язык, тема, режим «как в системе», палитра, шрифт и размер, `dailyGoal`, настройки ридера, раскладка ридера, чтец, подсказка первого слова, тактильность, меньше анимаций; повторы урока — `lessonAyahRepeats`/`lessonPassageRepeats` (`UiPrefs`, подключены в `TransferSettings`); **аналитика не переносится**). Дни — epochDay, моменты — мс, enum — по имени. Старые таблицы (`surah_todo`, `ayah_todo`, `ayah_review`) в файл не пишутся: после импорта `surah_todo`/`ayah_todo` пересчитывает зеркало по всем сурам. Новое поле можно добавить без повышения версии (читатель игнорирует неизвестные); несовместимое изменение поднимает `VERSION`, старое приложение откажет с `NewerVersion`.
- **Чистые функции** (`BackupCodec`): `encode(file)`, `decode(text): BackupParse` (`Ok(VerifiedBackup)` / `Failed(BackupError)`), `verify(file)`. Ошибки по видам: `NotABackup` (не наш файл / не JSON), `NewerVersion(version)`, `Corrupted` (обрезан, нет поля, дубли id, пересекающиеся отрывки, трудный аят без отрывка), `OutOfRange` (сура вне 1..114, аят вне суры по `QuranFacts`, неизвестный этап/вид/оценка, темп и дни вне диапазона). Настройки не валят импорт: неверное значение отбрасывается (`BackupSettings.sanitized()`). `VerifiedBackup` — единственное, что принимает `replaceWithBackup`. Предпросмотр: `VerifiedBackup.preview(): BackupPreview` (числа: план, отрывки, трудные аяты, заметки, записи журнала); слова и склонения — в `ui/i18n/TransferCopy.kt`. Имя файла — `BackupFormat.fileName(exportedAt, zone)`.
- **`HifzRepository`**: `exportBackupData(): BackupData` (одна транзакция чтения: план, отрывки, трудные аяты, журнал, `hifz_meta`, заметки) и `replaceWithBackup(VerifiedBackup)` — **одна** транзакция под тем же `Mutex`: очистка таблиц хифза и заметок, вставка (id отрывков и записей журнала сохраняются), зеркало по всем 114 сурам, `hifz_meta.legacy_import = done`. Ошибка в любом месте откатывает всё. Запись журнала, чей отрывок в файле отсутствует (удалён или слит), теряет `passageId` (иначе новый отрывок на чистом телефоне получил бы тот же id и «унаследовал» чужую историю). `ayah_review` и prefs слабых аятов при импорте не трогаются.
- **Android** (`ui/settings/`): `SettingsViewModel.exportToFile(uri)` / `exportForShare()` / `importFrom(uri)` / `confirmReplace()`; файлы — `TransferIo` (чтение с лимитом 32 МБ, запись `"wt"`, временный файл `cache/transfer/` через `FileProvider` `${applicationId}.transferprovider`, удаляется при следующем экспорте); настройки — `TransferSettings.collect` / `BackupSettings.applyTo(TransferSettingsTarget)`: язык, режим темы, палитра, шрифт и размер применяются через сеттеры корня (`LocalAppLanguageSetter`, `LocalThemeModeSetter`, …), остальное — через `UiPrefs` и `SettingsViewModel`; «как в системе» ставится **после** режима темы (сеттер режима его выключает). Экспортируемая копия сначала проходит `BackupCodec.verify`: приложение не пишет файл, который само бы не приняло. Дата последней копии — `UiPrefs.lastExportAt`.

### UI на модели хифза: главный и «Мой план» (B3)

- **Главный** (`ui/home`): одна подписка на `lesson(ticker.day)`, `surahStatuses()`, `coveredCounts()` и `plan()` в `HomeUiViewModel`; из старых таблиц, `dueReviews` и слабых аятов главный больше ничего не читает. Карточка «Урок дня» — `LessonCardBuilder.build(lesson, nameOf)` → `LessonCardUi` (состояния `Start`, `Continue`, `Done`, `NoPlan`, `PlanFinished`; строки Новое/Недавнее/Старое; `catchingUp`; `tomorrow`). Переход в урок — `sharedBounds` с `SharedKeys.LessonCard`.
- **Лист суры** (главный; тот же смысл должен быть в листе суры ридера) — `SurahStatusChange`: «Знаю наизусть» = `markKnown(surah)`; «Учу» = `learnNext(surah)` (у выученной суры сначала `forget(surah)`); «Не начал» = `forget(surah)` и, если сура в очереди плана, `setPlan(CUSTOM, очередь без суры, …)` (`clearPlan()`, если очередь опустела) — иначе сура осталась бы «Учу» через план. Если из повторений уходят выученные аяты (`lostAyahs > 0`), спрашивают: «Убрать N выученных аятов из повторений?». Строка под статусом: `surahCoverage(surah)` → «Выучено 23 из 40 аятов · 2 трудных».
- **Мой план** (`Dest.Plan`, `ui/plan`): черновик `PlanDraft` (пресет/свои суры, темп или дата, круг старого), правки — чистые функции; сохранение по кнопке `setPlan`, «Удалить план» с подтверждением — `clearPlan`. Прогноз — `forecast(draftPlan, today)`, оценка нового отрывка — `NewPassagePlanner` по покрытию текущей суры + `estimateMinutes(…, isNew = true)`, «≈ N страниц в день» — `oldWeight()`. **Круг старого (D4)**: рядом со страницами — «≈ N минут в день» (`Manzil.minutesPerDay`); если день дольше `PlanMath.OLD_HINT_MINUTES` = 15 мин и есть более длинный круг — мягкая подсказка «Это около 27 минут в день. Круг в 20 дней — около 10 минут» (`PlanMath.oldHint` → `OldCircleHint`) и кнопка-чип «20 дней», которая только ставит ползунок (сохранение — кнопкой «Сохранить»; `PlanViewModel.applySuggestedManzilDays`). Для **нового** плана ползунок стоит на `Manzil.suggestedDays(oldWeight)` (`PlanDraft.of(null, suggested)`), и это не «правка» (`edited`); сохранённый план показывает свой круг, молча он не меняется.
- День: `QuranHifzApp` вызывает `EpochDayTicker.refresh()` на `ON_RESUME` активити.

### «Слушать старое» в журнале (F1b)

`ReviewKind.LISTEN` лежит в том же `review_log` (`kind` хранится **именем строки**, схема Room v11 не менялась, миграции нет). Прослушивание — помощь, а не проверка, поэтому запись `LISTEN` **ничего не двигает**. Кто её игнорирует и чем это доказано:

| Читатель журнала | Как игнорирует `LISTEN` | Тест |
|---|---|---|
| Урок дня: «сделано сегодня», `catchingUp`, `deferredCount`, минуты, «Завтра» | `HifzDao.getLog` (`kind != 'LISTEN'`) + явный фильтр `kind.countsAsStudy` в `LessonBuilder.build` | `ListenRecordsTest` (урок с `LISTEN` == урок без него, `==` по data class) |
| Круг манзиль: порция дня, «повторено сегодня как OLD», квота | `Manzil.portion` читает только `OLD`; `lastReviewed` пишет только `review` | `ListenRecordsTest` |
| Лестница (этапы, сроки, `lapses`, трудные аяты) | пишется только `review/completeNew/logCheck`; `logListened` отрывки не трогает | `ListenRecordsTest`, `HifzRepositoryTest.listeningIsWrittenToTheListenLogOnlyAndMovesNothing` (устройство) |
| Серия | `HifzDao.getLogDays` (`kind != 'LISTEN'`) | `HifzRepositoryTest.aDayOfListeningAloneIsNotADayOfTheStreak` (устройство) |
| Прогноз плана | читает только `NEW` (`getNewLogSince`, `Forecast.compute`) | `ListenRecordsTest` |
| Напоминание об уроке (B4) | смотрит на урок дня, а он не меняется | `ListenReminderTest` |
| «Прогресс» (D2): удержание, столбики, активные дни, пустое состояние | берёт `reviewLog` (без `LISTEN`) + явный `kind.countsAsStudy` в `ProgressBuilder` | `ListenProgressTest` |
| Отчёт для устаза | то же | `ListenProgressTest` |
| Трудные аяты | живут в `passage_hard`, журнал не читают | — |

`LISTEN` читают только: `listenLog` → карточка «Урок дня» («Прослушано ✓» у «Старого»), карточка «Старое» урока и строка «Прослушано: N отрывков» в «Прогрессе» (отдельной строкой в активности дня и в пузыре дня, не в столбиках).

**Перенос (B5):** запись экспортируется как `"kind":"LISTEN"`, версия формата остаётся `1` (новое значение существующего поля; релизов с переносом ещё не было, старого читателя в природе нет). Файл без `LISTEN` читается как раньше (`BackupListenTest`, `BackupCodecTest`). Импорт сохраняет `passageId`, если отрывок есть в файле.

### Урок и «Проверь себя» (B2)

- **Экран «Урок»** (`Dest.Lesson`, `ui/lesson/`): `LessonViewModel` держит `ui: StateFlow<LessonUi>` (`Loading`, `NoPlan`, `NothingToday`, `Working(plan, card, progress, surah, ayahs)`, `Summary(plan, closedNow)`) из `hifz.lesson(ticker.day)` + текст Корана из `HomeViewModel`. Каждая оценка пишется сразу (`completeNew` / `review`), поэтому выход посреди урока ничего не теряет, а возврат продолжает с первого невыполненного отрывка. Только положение **внутри** карточки (`CardProgress`: фаза и сессия «Вспомни») живёт в VM и дублируется строкой в `rememberSaveable` (переживает поворот и смерть процесса; применяется только к карточке с тем же ключом). Чистая логика — `LessonFlow.kt` (`LessonCards.current/steps/completeBlocks`, `CardPhase`, `CardProgress` + кодек, `ListenPlan`), тесты `LessonFlowTest`.
- **Карточки**: новое — «Послушай» → «Прочитай» → «Вспомни» (сегменты вверху переключают фазу); недавнее и старое — карточка отрывка («🎧 послушать сначала») → «Вспомни». Переход между карточками — горизонтальный слайд `AnimatedContent`. Итог — кольцо, шаги, серия, прогноз, «Завтра: …»; `CelebrationBurst` только если урок закрылся в этом заходе (`closedNow`: был не завершён и стал завершён за время жизни VM). Корень экрана несёт `sharedBoundsKey(SharedKeys.LessonCard)`. Выход из «Вспомни» с ответами — вопрос «Выйти из урока? Сделанное сохранено».
- **Компонент «Вспомни»** (`ui/hifz/`, общий у урока и ридера): `HifzSession` (аяты отрывка, индекс, `revealed`, у каждого аята `AyahMark` `REMEMBERED`/`HARD`), элементы — `HifzAyahContent` (размытие, подсказка первого слова, удержание = подсмотреть), `RecallControlsHost(host: RecallHost, inSurah)` («Послушать», «Показать», затем **две** кнопки «Помню» / «Трудно»), `RecallPassage` (список строк для урока), `RecallGradeSheet` (4 оценки с предложенной, трудные аяты — чипы). Предложение оценки — `RecallGrading.suggest(total, hard)`: нет трудных → `GOOD`, есть → `HARD`, трудных половина и больше → `FORGOT`. Пока человек не выбрал оценку сам, выбор следует за чипами. Ничего не пишется до листа оценки.
- **«Проверь себя» в ридере** (`HifzViewModel`, `HifzMode`, `HifzOverlays`): сессия — выбранный диапазон (`Dest.Reader(hifz = true, rangeStart, rangeEnd)`) или «отсюда» (не больше `HifzSessions.MaxFromHere` = 10 аятов, если не задан отрывок повтора). В конце `CheckRouting.choose(lesson, range)`: диапазон **в точности** совпадает с невыполненным отрывком недавнего/старого сегодняшнего урока → `review(passageId, grade, hard)`, иначе `logCheck(surah, start, end, grade, hard)` (этапы и сроки не меняются). Событие `check_completed` (`grade`).
- **Ридер на модели хифза**: `ReaderViewModel` читает `hifz.surahCoverage(surah)` (кольца аятов — покрытые аяты, «трудные» — `hardAyahs`, статус суры — производный) и больше не читает `ayah_todo`/`ayah_review`/слабые аяты prefs. Лист суры: «Не начал / Учу / Знаю наизусть» с теми же правилами, что на главном (`ui/home/SurahStatusChange`: одна копия правил; исполнение шагов продублировано в `ReaderViewModel.chooseSurah`). Лист аята: строка состояния (трудный · «Повторение через N дн.» / «Пора повторить» / «В круге старого»), «Знаю этот аят» / «Убрать из выученных» (`markKnown`/`forget` одного аята), «Проверь себя отсюда».
- **Звук урока**: `PlaybackController.playSequence(surah, ayahIndices, prefs)` играет аяты в **точно заданном порядке** (каждый аят N раз, затем отрывок M раз) на том же плеере и сессии: уведомление, мини-плеер, фокус — как при обычном прослушивании; индекс состояния остаётся индексом аята. Порядок задаёт `ReaderAudioSession.playScript(indices)` (аддитивно, в `common/audio`: пока он задан, позиция в нём едет в `repeatsRemaining`, повторы/диапазон из настроек не применяются; `select`/другая сура его сбрасывают). Настройки ридера человека не трогаются: нигде не пишутся, а `RecallAudio` возвращает прежние настройки сессии, когда звук кончился, экран закрыт или VM очищена. Повторы урока — `UiPrefs.lessonAyahRepeats` (1..5, по умолчанию 3) и `lessonPassageRepeats` (1..4, по умолчанию 2), маленький лист настроек в фазе «Послушай»; формат копии принимает 1..20.
- **Аналитика** (без текста): `lesson_started`, `lesson_block_done` (`block`=new|recent|old), `lesson_completed` (`minutes_bucket`=le5|le10|le15|le25|gt25), `check_completed` (`grade`).

### Напоминание об уроке (B4, Android)

- **Настройки**: `UiPrefs.reminderEnabled` (по умолчанию выкл) и `reminderMinuteOfDay` (1200 = 20:00); `UiPrefs.readReminder(context)` читает prefs напрямую для приёмников. Раздел «Напоминание» — `ui/reminder/ReminderSection` (разрешение `POST_NOTIFICATIONS` спрашивается в момент включения; заблокированные уведомления — строка «Уведомления выключены / Открыть настройки»). Включение и время — через `ReminderController`.
- **Планирование**: `common/reminder/ReminderSync.sync(context)` — единая точка (старт приложения, настройки, `ReminderSystemReceiver` после `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / `TIME_SET` / `TIMEZONE_CHANGED`; `LOCALE_CHANGED` переименовывает канал). Два разовых будильника (дневной и «Через час»), каждый поставлен дважды на один момент: `setWindow` с окном 10 мин (вовремя, пока телефон не в глубоком Doze; один неточный будильник на сутки вперёд может опоздать до часа) и `setAndAllowWhileIdle` (приходит и в Doze); первую копию берёт `ReminderAlarms.claim` по `EXTRA_PLANNED_AT`, вторая отбрасывается. Разрешение на точные не нужно. Момент — чистая `ReminderTime.nextTriggerMillis(now, minuteOfDay, zone)`.
- **Срабатывание**: `ReminderReceiver` сначала ставит завтрашний будильник, затем (`goAsync`, таймаут 8 с) `ReminderNotifier` читает `hifz.lesson(EpochDays.now()).first()`; `ReminderNotices.build(...)` решает текст или тишину (`paused != null` → **тишина**, ни про урок, ни «Продолжить чтение»; `isComplete` → ничего, нет урока → «Продолжить чтение» по `lastReadPosition`). Канал `lesson_reminder`, имя на языке приложения; старый `hifz_reminders` удаляется.
- **Ссылка**: `ReminderLink` (extras `open=lesson|reader`, `surah`, `ayah`) → `MainActivity` (`singleTop`, extras потребляются) → `ReminderOpenRequests` → `AppNavigation.openFromReminder` (поверх «Главного», `popUpTo<Home>`). Тексты — `ui/i18n/ReminderCopy.kt`.
- **Копия**: `BackupSettings.reminderEnabled`, `reminderMinuteOfDay` (0..1439); импорт разрешение не спрашивает.
- Старый `expect ReminderScheduler` на Android только зовёт `ReminderSync.sync` (аргументы не используются).
- **Аналитика**: `reminder_enabled` (`hour`), `reminder_shown` / `reminder_opened` (`kind`=lesson|reading), `reminder_snoozed`, `reminder_timeout`.

### Онбординг v2 и «Новое» (C1, Android)

- **Кто видит онбординг**: `needsOnboarding()` → `OnboardingGate.resolve()`. Чистое решение `OnboardingGate.decide(completed, languageSaved, fontSaved, v2Started)`:
  - пройден → нет;
  - язык и шрифт сохранены прошлой версией, а v2 не начинался → существующий пользователь: нет, и сразу помечается пройденным;
  - иначе → да.

  Флаги в `UiPrefs`: `onboarding_completed`, `onboarding_v2_started` (ставит `OnboardingViewModel` при создании), `whats_new_shown`. Читаются без Koin: `UiPrefs.readOnboarding(context)` / `writeOnboardingCompleted(context)`. Их зовут сплэш и `MainActivity`.
- **Шаги**: `OnboardingFlow` — чистая машина шагов. Путь «Заучивать»: язык → 3 слайда (один сегмент) → цель → что знаю → что учу → темп → напоминание → чтение → «План готов». Путь «Читать»: язык → слайды → цель → чтение → итог. Состояние (шаг и выбор) — в `SavedStateHandle`: `OnboardingViewModelFactory` создаёт его из extras записи навигации и передаёт в Koin как параметр.
- **Запись**:
  - язык, шрифт, тема и палитра — сразу, через сеттеры корня;
  - цель → `UiPrefs.onboardingGoals` при уходе с шага цели;
  - напоминание — на своём шаге, через `ReminderController`;
  - прогресс — только по последней кнопке: `OnboardingFinisher.finish` — `markKnown` по каждой отмеченной суре, затем `setPlan` (с `manzilDays = Manzil.suggestedDays(oldWeight().first())`, вес читается **после** `markKnown`; D4), затем флаг. Повторное нажатие ничего не дублирует.

  Первый урок и прогноз на шагах считаются без записи (`OnboardingPlanMath` поверх `NewPassagePlanner`, `Forecast`, `PlanMath`).
- **Выход**: `OnboardingExit.Lesson | Reader | Later` → главный, поверх него урок или ридер Аль-Фатихи (назад — на главный).
- **«Новое: Урок дня»** (`ui/whatsnew/`): `WhatsNewHost` после NavHost. `WhatsNewGate.pending` = `completed && !v2Started && !whatsNewShown`. Лист показывается один раз над главным; если план есть — кнопка «Открыть урок», иначе «Составить план». Ссылка из напоминания откладывает лист до следующего запуска.
- **Аналитика**: `onboarding_step` (`step`), `onboarding_story_skipped`, `onboarding_completed` (`goal`, `preset`, `pace`, `known`, `reminder`, `action`), `whats_new_shown`, `whats_new_action`.

### Подсказки-спотлайты (C2, `ui/components/spotlight/`)

- **Корень**:
  - `SpotlightHost(controller) { AppNavigation() }` — слой над навигацией и мини-плеером, в том же окне. Даёт `LocalSpotlight`, контроллер создаёт `rememberSpotlightController()`.
  - `SpotlightNavigationEffect(route, lifecycle)` сообщает, какой экран сверху и в покое ли он (запись в RESUMED).
- **Цель**: `Modifier.spotlightTarget(id, shape = Rounded(16.dp), reveal = false, inset = 0.dp)` — после `padding`, до `clip`/`background`. Узел хранит только координаты (при прокрутке ничего не считается). Если у нескольких элементов один id, берётся самый видимый. `reveal = true` — цель сама прокручивается в видимую область (`BringIntoViewRequester`). Id — в `SpotlightTargets`.
- **Тур**:
  - экран зовёт `SpotlightTourEffect(SpotlightTours.X, ready)`; `ready` — данные загружены и цель на экране;
  - `SpotlightTour(id, screen, steps, settleMillis = 350)`, шаг — `SpotlightStep(targetId, tip, shape?, position)`, тексты — `SpotlightCopy`;
  - туры: `Home` (4 шага, `settleMillis` 1,5 с), `Reader` (5), `LessonHeader`, `LessonPeek`, `LessonGrade`, `Player` (2).
- **Когда стартует** (`SpotlightGate`):
  - экран тура сверху и в покое, это не онбординг;
  - нет открытого `QhSheet` (счётчик `sheetShown`/`sheetHidden`; `WhatsNewHost` держит его и пока лист «Новое» ждёт показа);
  - окно приложения в фокусе (нет диалога, меню, шторки);
  - другой тур не идёт.

  Затем ожидание, пока границы целей перестанут меняться. Шаги без видимой цели пропускаются; если ни одного — тур не отмечается.
- **Флаги**: свой файл `quran_hifz_spotlights`, ключи `seen_<tourId>`; ставятся по окончании или пропуске, не при старте. `resetAll()` — строка «Показать подсказки снова» в «Удобстве». В копию B5 флаги не входят.
- **Вид и ввод**:
  - затемнение 70 % (в тёмной теме — затемнённый фон), вырез = цель + 8 dp (`Offscreen` + `DstOut`);
  - вырез и карточка перетекают `QhMotion.bounds()`, золотое кольцо пульсирует;
  - касание вне карточки — следующий шаг, элемент под вырезом не срабатывает; «Назад» — пропуск;
  - тактильный `Tick` на шаг, текст — polite live region, содержимое под затемнением скрыто от TalkBack;
  - Reduce Motion — без перетекания и пульса.
- **Аналитика**: `spotlight_shown` (`tour`), `spotlight_finished` (`tour`, `result` = done|skipped, `step`).

### Джузы и метки мусхафа (D1)

- **Общая таблица**: `ui/common/JuzIndex`:
  - 30 джузов по границам колонки `juz` вшитого текста (`JuzIndexTest` сверяет все 6236 аятов);
  - `range(juz)` — `JuzRange` с `ayahsOf(surah)`, `juzOf(surah, ayah)`, `ayahCount(juz)`;
  - `coveredCount(juz, covered)` по `HifzRepository.coveredAyahs()` (покрытые аяты по сурам, одна подписка);
  - `arabicName(juz)` — традиционные названия.
- **Главный** (`ui/home/JuzRows.kt`, `components/JuzListRow.kt`):
  - переключатель «Суры · Джузы» (`HomeTab`, `rememberSaveable`); фильтр статусов только у сур;
  - строка джуза: медальон с долей выученного, «Джуз 30», арабское название, «Ан-Наба 1 — Ан-Нас 6 · 564 аята», «Выучено N из M»;
  - поиск понимает «30», «джуз 30», «2:255», арабское название и суры джуза;
  - касание → `Dest.Reader(startSurah, startAyah, origin = JuzRows.ORIGIN)`;
  - `HomeUiViewModel` читает `coveredAyahs()` вместо `coveredCounts()`: счётчики по сурам выводятся из него.
- **Ридер** (`ui/reader/MushafMarks.kt`, `components/MushafMarkViews.kt`, тексты — `ui/i18n/MushafCopy.kt`):
  - `MushafMarks.build` считает метки один раз на суру: `AyahMarks` — джуз, четверть хизба, страница, саджда; `MushafPlace` — текущее место;
  - шапка: «Джуз 2 · Хизб 3 · стр. 22» по верхнему видимому аяту (`derivedStateOf`);
  - тихая строка над аятом, где начинается джуз, четверть хизба (`¼/½/¾ хизба 3`) или страница;
  - саджда: значок-арка (`SajdaArch`, `Canvas`) и «Саджда · обязательная / желательная», касание — пояснение.
  - Знак ۞ (U+06DE) в интерфейсе не рисуется (D4, решение владельца). Он стоит в начале 199 аятов вшитого текста (начала четвертей хизба; всегда первый знак и пробел после него). **Скрыт только при показе**, текст в базе не меняется: `splitBismillah(surah, ayah, text)` (`ui/reader/Bismillah.kt`) = `cutBismillah` (срез Бисмиллях первого аята) + `hideRubSign` (убирает знак и пробелы после него). Все места показа аята идут через неё: ридер, плеер-док, «Проверь себя» (`RecallPassage`), «Новое» урока (`LessonNewCard`), лист аята и превью шрифта (`AppearanceSheet`, `ReaderPreviewCard`, слайды онбординга — `hideRubSign`). Копирование и «поделиться аятом» берут `cutBismillah(…)` исходного текста: знак в них остаётся. Начало четверти показывает тихая строка D1.

### Пауза плана (F2b)

Пауза останавливает уроки на 1–14 дней, сдвигает календарь плана и не рвёт серию. Код: `hifz/schedule/Pause.kt` (`PlanPauses`, чистые функции), `hifz/model` (`PlanPause`), `HifzRepository.pausePlan/resumePlan/pause`. Схема Room **не менялась**.

- **Модель.** `PlanPause(fromEpochDay, untilEpochDay)` — включительно; уроки возвращаются в `until + 1`. Список пауз (текущая и история за 400 дней: серии нужны прошедшие) лежит в `hifz_meta` по ключу `plan_pauses` строкой `"from,until;from,until"` (`PlanPauses.encode/decode`, повреждённая часть отбрасывается, остальное читается). Второй ключ `plan_start_base` — день, когда план **на самом деле** начался, до сдвигов паузы: ставится первой паузой плана, сбрасывается, когда старт плана ставится заново (`setPlan` с другой очередью, `learnNext` без плана, `clearPlan`).
- **Сдвиг.** `start(pauses, days, today)` / `resume(pauses, today)` возвращают `PauseStep(pauses, shiftDays, pause)`; `shiftPassages` двигает срок и `lastReviewed` (null остаётся null), `shiftPlan` — срок и старт плана. Продление: `from` остаётся, конец `today + days − 1`, сдвиг = разница концов (может быть и отрицательной — сокращение). «Продолжить сейчас»: конец `today − 1`, сдвиг назад на оставшиеся дни; началась сегодня — пауза удаляется, всё как до неё.
- **Расписание.** `LessonInput.pause` (репозиторий передаёт паузу, внутри которой `today`): `LessonBuilder` отдаёт пустой урок (`isEmpty`, нет нового/недавнего/старого, 0 минут) с `paused`; после паузы урок тот же, что был бы без неё, сдвинутый на N дней (тест сравнивает уроки `==` для N = 1, 3, 7, 14). Прогноз и серия — см. «Расписание».
- **Переносы.** `hifz_meta` уходит в копию B5 как есть: пауза и `plan_start_base` переживают экспорт и импорт (`PlanPauseTest.thePauseSurvivesAnExportAndAnImport`, `HifzRepositoryTest.thePauseSurvivesABackupRoundTrip`).
- **UI (Android).** «Мой план»: строка «Пауза» → лист (`ui/plan/PlanPause.kt`: чипы 1, 3, 7, 14, шаг ±1, «Уроки вернутся 12 ноября. Повторения сдвинутся на 7 дней, серия сохранится.», «Поставить на паузу»); в паузе — «На паузе до 11 ноября», «Продолжить сейчас» (с подтверждением) и «Продлить» (кнопки одна под другой; нижняя граница дней — оставшиеся, недоступно, когда конец уже на пределе). Пауза не часть черновика плана и не идёт через «Сохранить». Главный: `LessonCardState.Paused` («План на паузе до 11 ноября», «Продолжить сейчас» с подтверждением, касание карточки ведёт в «Мой план», где пауза продлевается; в строке-приглашении — «На паузе до …»). Экран урока в паузе — то же сообщение вместо «Сегодня всё». Напоминание — тишина. Даты — `formatDay`, тексты — `ui/i18n/PauseCopy.kt`.
- **Аналитика** (без текста): `plan_paused` (`days`), `plan_resumed_early`.

### Проверка (F2b)

Тесты по выученному: пять видов вопросов, построитель в `commonMain/hifz/quiz/`, экран `Dest.Quiz` в `ui/quiz/`. Тест — помощь, а не урок: **в журнал повторений ничего не пишется**, серия, удержание, лестница и круг его не видят. Единственный след — «Не знал» отмечает аят трудным (`HifzRepository.markHard`).

- **Пул.** Только покрытые отрывками аяты (`coveredAyahs()`); вход виден от `QuizBuilder.MIN_POOL` = 5 покрытых аятов (`HomeUi.quizReady`, кнопка итога урока).
- **Построитель** — чистая функция `QuizBuilder.build(covered, words, count = 5, random)`; слова аята считает вызывающий (`words(surah, ayah)` — через `displayWords` ридера, басмала срезана). Виды (`QuizKind`):

| Вид | Что видно | Ответ | Годится, если |
|---|---|---|---|
| `CONTINUE` | первые ⌈n/3⌉ слов, остальное скрыто | «Показать» → «Знал / Не знал» | в аяте ≥ 4 слов |
| `NEXT` | аят целиком | следующий аят → «Знал / Не знал» | следующий аят той же суры покрыт |
| `START` | название суры (имя и арабское) | первый аят суры → «Знал / Не знал» | первый аят покрыт |
| `ORDER` | 3–4 подряд идущих покрытых аята, перемешаны (никогда не в исходном порядке) | нажимать по порядку → «Проверить» | есть 3+ подряд покрытых |
| `WHERE` | аят целиком (не первый аят суры) | 1 из 4 сур: верная + 3 покрытых других (меньше трёх — соседние по номеру) | аят ≥ 2 |

  В пяти вопросах — не меньше трёх видов, если пул позволяет; разные суры, где можно; один аят (что вопрос показывает или выдаёт: у «Дальше» оба) — не больше одного раза за тест. Малый пул даёт меньше пяти вопросов (5 аятов одной суры — как правило пять: одиночные виды). Строится до 24 раз, остаётся лучший набор (больше вопросов, потом видов, потом сур); следующий вопрос не съедает аятов, нужных остальным.
- **След ответов.** `QuizAnswers`: «Не знал» в «Продолжи» / «Дальше» / «С чего начинается» отмечает аят-ответ; ошибка в «Расставь» — только аяты, поставленные не на своё место; ошибка в «Где» не отмечает ничего (знание места, а не текста). `markHard` вызывается в момент ответа (выход посреди теста сохраняет отмеченное), по одному разу на суру-ответ.
- **Экран** (`QuizViewModel`, `QuizSession`, `QuizScreen`, `QuizPages`, `QuizVeil`). Сессия — неизменяемая `QuizSession` (шаги `reveal/answerSelf/pick/clearPicks/checkOrder/choose/next`, тесты `QuizSessionTest`) в `StateFlow` VM. Прогресс «2 из 5» вверху, переходы между вопросами — горизонтальный слайд, как карточки урока (Reduce Motion — затухание). Арабский текст — шрифт и размер ридера («Aa»: `getQuranFontFamily`, `readingFontSize`). Скрытая часть — свой `QuizVeiledText`: размытие на Android 12+, полоски на словах ниже; «Показать» растворяет завесу за 320 мс. «Расставь»: карточки, нажатие ставит номер, повторное снимает (остальные смыкаются), «Проверить» — зелёная или мягкая красная рамка, правильный порядок. «Где»: 4 варианта, после выбора верный отмечен галочкой и рамкой. Итог: кольцо и «Знал 4 из 5», список «Не знал» с «Открыть в ридере» (`Dest.Reader(surah, ayah, origin = "quiz")`), «Ещё 5 вопросов», «Готово»; без звёзд и конфетти.
- **Входы.** Главный: строка «Проверка · 5 вопросов по выученному» под карточкой «Урок дня» (`ui/home/components/QuizRow.kt`; есть и в паузе); итог урока — кнопка «Проверить себя · 5 вопросов». Тексты — `ui/i18n/QuizCopy.kt`.
- **Аналитика** (без текста): `quiz_completed` (`known` 0..5, `types` — число видов).

### Что известно и ещё не сделано

- `ProgressRestoreDao.replaceUserProgress` (восстановление старой копии из prefs) пишет только старые таблицы и не знает об отрывках: на Android его больше никто не вызывает (экран настроек работает через `replaceWithBackup`), на iOS он остаётся.
- Пока старый UI пишет `surah_todo`/`ayah_todo` напрямую, эти записи после переноса не создают отрывков; ридер (B2), главный (B3) и «Прогресс» (D2) уже на `HifzRepository`.

