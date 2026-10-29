# Задача 1A — главный экран

Исполнитель: Sonnet. Ветка: твой worktree от `redesign/android` (фаза 0 слита). Параллельно идут 1B (ридер) и 1C (настройки + онбординг).

## Окружение worktree

`local.properties` не в git: перед первой сборкой `cp /Users/azizjon/projects/quran_todo_kmp/local.properties .` в корне worktree. Собирай только `./gradlew` из корня worktree. Основной checkout не трогай. Эмулятор не используй — визуальную приёмку делает оркестратор и пришлёт замечания.

## Прочитай сначала

1. `docs/redesign/ANDROID_REDESIGN.md` — разделы 1, 2, 3, 4, **6.3** (это твой экран), 7, 8, 10.
2. `docs/redesign/API.md` — публичное API дизайн-системы и каркаса (что уже есть: тема, компоненты, motion, `UiPrefs`, `PlaybackController`). Используй его, не изобретай своё.
3. Логика, которую переносишь: `commonMain/.../presentation/hifz/HifzHomeScreens.kt` (`buildTodayTasks`, `buildQuranRows`), `commonMain/.../presentation/hifz/QuranLibraryScreen.kt` (`buildLibraryRows`, `buildContinueReading`, копирайт), `commonMain/.../presentation/home/HomeViewModel.kt` (состояние, `setSurahStatus`, `refreshQuran`).

## Зона

Только `composeApp/src/androidMain/kotlin/tj/app/quran_todo/ui/home/**`, `ui/i18n/HomeCopy.kt`, `homeUiModule` в `ui/di/UiModules.kt` (только содержимое своего модуля), тесты в `composeApp/src/androidUnitTest/kotlin/tj/app/quran_todo/ui/home/**`. Нужен общий компонент, которого нет, — сделай локальный в `ui/home/components/` и упомяни в отчёте. `PlaybackController` только используешь (его меняет 1B).

## Что сделать

### Состояние — `HomeUiViewModel`
Android-ViewModel (Koin `viewModel { }` в `homeUiModule`), собирает `HomeUi` из `HomeViewModel.uiState`, `UiPrefs`, `PlaybackController`, `AppSettings.dailyGoal`, языка:
- `continueItem` — позиция чтения `UiPrefs.lastRead` (fallback на старую аудио-позицию; глобальный номер аята → номер в суре через `completeQuran`); для нового пользователя — Аль-Фатиха, аят 1, флаг `isFresh`.
- `today: TodayUi?` — задачи из `TodayPlanner` (чистая функция, перенос `buildTodayTasks`, типы New / Review / Weak; Review объединяет recent+old) + `doneToday`/`goal`: `doneToday` = число разных аятов, у которых сегодня `AyahTodoEntity.updatedAt` или `AyahReviewEntity.lastReviewedAt` (возьми `AyahReviewDao.getAll()` при изменениях `ayahTodos`/`dueReviews`, не на каждом кадре); `goal` = `AppSettings.dailyGoal`. Блок виден по правилам 6.3 (включая `UiPrefs.onboardingGoals` содержит `memorize`).
- `recent` — `UiPrefs.recentSurahs` + `pinnedSurahs` (закреплённые первыми).
- `rows` — 114 строк: номер, имя по языку (как `buildLibraryRows`: RU — русское имя из `surahList`, иначе транслитерация), перевод имени, арабское имя, число аятов, статус (с учётом `pendingSurahStatuses`), `learnedFraction` (выученные аяты / все; LEARNED → 1), `canOpen`, `isPinned`.
- поиск и фильтр — состояние в VM (`SavedStateHandle` или `rememberSaveable` на экране, на выбор), фильтрация вне UI-потока при вводе (debounce 120мс).
- `nowPlaying` для мини-плеера: сура, номер аята в суре, скорость, `isPlaying`, прогресс аята.

### Поиск — `SurahSearch.kt`
Чистые функции + unit-тесты: нормализация (нижний регистр, без дефисов/апострофов/«ʻ’'», без префиксов «ал-/аль-/al-/an-/ash-/at-…», латиница/кириллица как есть, арабский без харакатов U+064B–U+0652, U+0670, татвиля), совпадение по номеру, по подстроке и по началу слов. `parseAyahReference("2:255" | "2 255" | "2.255")` → `(surah, ayah)` с проверкой диапазонов по числу аятов.

### Экран — по разделу 6.3
- `QhLargeTopBar`: надзаголовок «Ассаляму алейкум» (на всех языках — транслитерация в местной графике; EN «Assalamu alaikum»), заголовок «Коран»; действия — мини-`ProgressRing` (sharedElement `SharedKeys.ProgressRing`, → `onOpenProgress`) и шестерёнка (sharedElement `SharedKeys.SettingsIcon`, поворот на 90° при нажатии, → `onOpenSettings`). Долгое нажатие на заголовок → `onOpenCatalog` только в debug (параметр уже есть в заглушке, сохрани).
- Hero, Сегодня, Недавние, поиск + сегменты (sticky вместе), список, мини-плеер, лист суры, празднование, тосты — ровно как в 6.3. Мини-плеер плавает поверх списка; у списка нижний отступ = высота мини-плеера + навигационная полоса, когда он виден.
- Shared elements: hero (`origin = Origin.HERO`), строки (`LIST`), недавние (`RECENT`), сегодня (`TODAY`), результаты поиска (`SEARCH`), мини-плеер (`PlayerDock`, `PlayButton`, `origin = PLAYER`). Для строки: контейнер — `sharedBoundsKey(surahContainer, Remeasure)`, бейдж — `sharedElementKey(surahBadge)`, название и арабское имя — `sharedBoundsKey(..., Scale)`. Ридер (1B) применит те же ключи в шапке.
- Навигация в ридер — `onOpenReader(Dest.Reader(surah, ayah, origin, hifz, rangeStart, rangeEnd))`; для задач «Сегодня» `hifz = true` и диапазон задачи (номера аятов в суре).
- «Слушать» в hero: `PlaybackController.start(...)` с `SurahReaderPreferences.load()` и `ReaderPracticeSettings.load(surah, ayahCount)`, индекс — с аудио-позиции (`UserSettingsStorage.getLastPlaybackSurah/AyahNumber`), если сура совпадает, иначе позиция чтения. Если контроллер уже играет эту суру — кнопка «Пауза» с эквалайзером и вызывает `toggle()`.
- Смена статуса — `HomeViewModel.setSurahStatus`; ошибка (`statusUpdateError`) — тост-ошибка, затем `clearStatusUpdateError()`.
- Стаггер при первом показе, `animateItem()` у строк, скелетон при загрузке, `EmptyState` при ошибке (`refreshQuran(withLocalAction = false)`) и пустом поиске.
- Производительность: строки списка — отдельные `@Composable` со стабильными параметрами (data class/`@Immutable`), ключи `surahNumber`; прогресс мини-плеера читается только внутри мини-плеера.

### Копирайт
`ui/i18n/HomeCopy.kt` — все строки экрана на 5 языков по образцу `quranLibraryCopy` (глоссарий — раздел 10 спецификации). Склонения: «1 аят / 2 аята / 5 аятов» для RU и TG-эквивалент — сделай маленькую функцию `ayahCountLabel(n, language)`.

## Проверка
1. `./gradlew :composeApp:assembleDebug` и `./gradlew :composeApp:testDebugUnitTest` (включая твои тесты `TodayPlanner` и `SurahSearch`).
2. Пройди глазами критерии раздела 8 по коду (Reduce Motion, TalkBack-подписи, `pressable`).

## Отчёт
Файлы; что сделано не по спецификации и почему; какие компоненты пришлось сделать локально; что стоит проверить визуально в первую очередь. Один коммит `redesign: home screen (1A)` с последней строкой `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`; в отчёте — путь worktree и ветка.
