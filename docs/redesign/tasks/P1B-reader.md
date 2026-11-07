# Задача 1B — ридер и плеер

Исполнитель: Sonnet. Ветка: твой worktree от `redesign/android` (фаза 0 слита). Параллельно идут 1A (главный) и 1C (настройки + онбординг).

## Окружение worktree

`local.properties` не в git: перед первой сборкой `cp /Users/azizjon/projects/quran_todo_kmp/local.properties .` в корне worktree. Собирай только `./gradlew` из корня worktree. Основной checkout не трогай. Эмулятор не используй — визуальную приёмку делает оркестратор и пришлёт замечания.

## Прочитай сначала

1. `docs/redesign/ANDROID_REDESIGN.md` — разделы 1, 2, 3, 4 (особенно **4.4**), **6.4** (это твой экран), 7, 8, 10.
2. `docs/redesign/API.md` — API дизайн-системы и каркаса.
3. Текущий ридер (переносишь поведение, не код): `commonMain/.../presentation/surah/SurahScreen.kt`, `ReaderPlayerSheet.kt`, `commonMain/.../common/audio/ReaderAudioSession.kt` (+ тесты в `commonTest`), `common/settings/SurahReaderPreferences.kt`, `ReaderPracticeSettings.kt`, `common/i18n/PlayerFeatureCopy.kt`, `presentation/surah/SurahViewModel.kt`.

## Зона

`ui/reader/**`, `ui/playback/PlaybackController.kt` (ты единственный, кто его меняет в этой фазе), `ui/i18n/ReaderCopy.kt`, `readerUiModule` в `ui/di/UiModules.kt`, тесты `composeApp/src/androidUnitTest/kotlin/tj/app/quran_todo/ui/reader/**`. Нужен общий компонент, которого нет, — локально в `ui/reader/components/`, упомяни в отчёте.

## Что сделать

### Состояние — `ReaderViewModel`
Android-ViewModel с параметром `Dest.Reader` (Koin `viewModel { params -> ... }`):
- `SurahWithAyahs` — из `HomeViewModel.uiState.completeQuran`; аяты отсортированы по `numberInSurah`.
- имена — `SurahViewModel.chapterNames` (+ `loadChapterNames(language)`), переводы — `SurahViewModel.loadTranslations` / `ayahTranslations`, если включены.
- статус суры — `SurahViewModel.surahStatus(n)` + `setSurahStatus(...)` с оптимистичным pending и откатом при ошибке (логика есть в старом `SurahScreen`).
- статусы аятов, заметки, ревью — **напрямую из DAO** (`AyahTodoDao.getBySurahNumber`, `AyahNoteDao.getBySurahNumber`, `AyahReviewDao.getBySurahNumber`) в `viewModelScope` этого VM. Не вызывай `SurahViewModel.observeNotes/observeReviews` — они плодят коллекторы в синглтоне. Изменения — через `SurahViewModel.updateAyahStatus` / `clearAyahStatus` / `upsertNote`.
- слабые аяты — `HomeViewModel.uiState.weakAyahKeys` (`"$surah:$ayahNumber"`, ayahNumber — глобальный номер).
- `UiPrefs.recordRecentSurah(n)` при открытии; позиция чтения — `UiPrefs.saveLastRead(surah, ayahInSurah)` с debounce 400мс по первому видимому аяту.

### Bismillah — `Bismillah.kt`
Чистая функция `splitBismillah(surahNumber, ayahNumberInSurah, text): String` — для сур 2–114, кроме 9, у 1-го аята отрезает первые 4 слова, если первое слово после удаления харакатов равно «بسم»; иначе текст без изменений. Unit-тест на реальные строки (возьми пример Аль-Бакары 2:1 из БД-формата `quran-uthmani`: «بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ الٓمٓ») и на суры 1 и 9.

### Плеер — `PlaybackController` по 4.4
- Ридер суры X: если `isActiveFor(X)` — показывает состояние контроллера; иначе — свой «готов» (индекс = позиция чтения или `route.ayah`), а при игре другой суры — чип «Играет: <сура>» над доком (нажатие → открыть ту суру, `origin = Origin.PLAYER`). Play/выбор аята в таком состоянии → `start(X, …, fromIndex)`.
- Добавь в контроллер **таймер сна**: `setSleepTimer(SleepTimer.Off | EndOfSurah | Minutes(15|30))`, `sleepTimer: StateFlow`, по срабатыванию — пауза. Не трогай `ReaderAudioSession`, кроме добавления публичных методов без изменения поведения; `testDebugUnitTest` должен проходить.
- Выход из ридера звук **не** останавливает.

### Экран — по разделу 6.4
- Список: шапка-картуш (`SurahCartouche`, арабское имя, название, мета, `StatusPill` → лист статуса), `OrnamentDivider`, Bismillah-строка (кроме сур 1 и 9), аяты, карточка «Следующая сура» (→ `onOpenReader(Dest.Reader(n + 1, origin = Origin.NEXT))`).
- Компактная шапка появляется, когда картуш уходит под неё; «Аят N / M» (`AnimatedCounter`) — по первому видимому аяту; прогресс чтения 2dp; действия «Aa» и «⋮» (в «⋮»: статус суры, «Перейти к аяту…» с `AyahWheel` в листе). Кнопку режима заучивания пока **не** показывай (это 2A), но параметр `route.hifz`/диапазон используй для начальной прокрутки к `rangeStart`.
- Аяты: `AyahRosette` (кольцо статуса аята, точка — слабый), текст `quranBody` (размер из `LocalAppSettings.readingFontSize`, межстрочный 1.85×), перевод, hairline. Текущий аят — фон `readerHighlight` + золотая полоса слева (анимированно), аяты диапазона повтора — золотая скобка справа.
- Нажатие на аят — выбрать / перейти; долгое нажатие или «⋯» → лист аята (слушать отсюда, повторять этот аят, статус аята, заметка с полем ввода, копировать, поделиться через `Intent.ACTION_SEND`).
- Автопрокрутка за играющим аятом (~30% высоты), пауза следования при ручном скролле + пилюля «К текущему аяту».
- Начальная позиция без «прыжка»: индекс до первой композиции — `route.ayah` → `route.rangeStart` → позиция чтения этой суры → 0. Подсветить `route.ayah` 1.5с при открытии.
- Док плеера свёрнутый/развёрнутый (`AnchoredDraggable`), содержимое — как в 6.4: повтор отрывка (два `AyahWheel` + сегменты 1×/3×/5×/∞), пауза между аятами, скорость, таймер сна. Сохранение — `SurahReaderPreferences.save()` и `ReaderPracticeSettings.save(surah)`, затем `PlaybackController.updateSettings(...)`. Кольцо отсчёта паузы вокруг play (`pauseRemainingSeconds`). Ошибка звука — «Нет связи · Повторить».
- Подсказка-гайд для повтора/паузы — один раз (`UserSettingsStorage.isPlayerGuideSeen/savePlayerGuideSeen`), как тост/карточка с `player_guide_art` (атлас из 3 кадров — см. `PlayerGuideIllustration` в старом листе).
- Лист «Aa»: превью аята, размер 22–40 (`LocalAppSettingsSetter` с новым значением `readingFontSize`; корень приложения приводит значение к диапазону — оркестратор расширит его до 22–40 при слиянии), карусель шрифтов (`LocalReadingFontStyleSetter`), перевод (`SurahReaderPreferences` + `SettingsChangeNotifier.notifyChanged()`), тема (свет/тьма через `LocalThemeModeSetter`), «Не гасить экран» (`UiPrefs.keepScreenOn`).
- `keepScreenOn`: `LocalView.current.keepScreenOn` пока ридер открыт и `UiPrefs.keepScreenOn` или идёт воспроизведение.
- Shared elements: картуш — `sharedBoundsKey(surahContainer(origin, n), Remeasure)`, звезда/розетка номера в картуше — `sharedElementKey(surahBadge(origin, n))` (покажи маленький `SurahNumberBadge` над арабским именем), арабское имя и название — `sharedBoundsKey(..., Scale)`; док плеера — `sharedBoundsKey(PlayerDock)` и кнопка play — `sharedElementKey(PlayButton)`, только если `origin == Origin.PLAYER`. Остальной контент — fade + slideUp 24dp с задержкой 120мс.
- Производительность: позиция/длительность аудио читаются только внутри дока; элементы списка стабильны; `derivedStateOf` для видимого аята.

### Копирайт
`ui/i18n/ReaderCopy.kt` — 5 языков; переиспользуй формулировки `surahReaderCopy` и `PlayerFeatureCopy`.

## Проверка
1. `./gradlew :composeApp:assembleDebug` и `./gradlew :composeApp:testDebugUnitTest` (включая тест Bismillah и существующие тесты аудио-сессии).
2. Критерии раздела 8 по коду.

## Отчёт
Файлы; изменения `PlaybackController` (новые сигнатуры); отклонения от спецификации; что проверить визуально в первую очередь. Один коммит `redesign: reader and player (1B)` с последней строкой `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`; в отчёте — путь worktree и ветка.
