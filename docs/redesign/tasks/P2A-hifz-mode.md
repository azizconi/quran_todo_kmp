# Задача 2A — режим заучивания в ридере

Исполнитель: Sonnet. Ветка: твой worktree от `redesign/android` (фаза 1 слита). Параллельно идёт 2B (экран «Прогресс»).

## Окружение worktree

1. Worktree может быть создан от `master`: первым делом `git merge --ff-only redesign/android`.
2. `cp /Users/azizjon/projects/quran_todo_kmp/local.properties .`
3. Собирай только `./gradlew` из корня worktree. Основной checkout не трогай. Эмулятор не используй.

## Прочитай сначала

1. `docs/redesign/ANDROID_REDESIGN.md` — разделы 1, 3, **6.5** (это твоя задача), 6.4 (ридер, в который ты встраиваешься), 8, 10.
2. `docs/redesign/API.md`.
3. Код ридера: `ui/reader/**`. Точки встраивания:
   - `ReaderScreen.kt` → `ReaderScreen` (входная точка, `route.hifz`/`rangeStart`/`rangeEnd`), `ReaderContent` (состояние экрана, листы, док), `ReaderList` (элементы списка, `onSelect`/`onMore`, `markedIndex`, `passage`);
   - `components/AyahRow.kt` → `AyahRow(model: AyahRowModel, …)` — сюда передаётся состояние скрытия/раскрытия и слот под кнопки режима (добавь один параметр-модель режима, например `hifz: HifzRowState?`, и слот `hifzControls: @Composable (() -> Unit)?`; при `null` строка ведёт себя как сейчас);
   - `components/ReaderTopBar.kt` → `ReaderTopBar(...)` — добавь параметр-слот для кнопки «👁» и вариант заголовка/фона режима (или отдельный `HifzTopBar` в `ui/hifz`, который ридер показывает вместо обычной шапки);
   - `components/PlayerDock.kt` / `PlayerDockState.kt` — запрет раскрытия дока в режиме;
   - `ReaderViewModel.kt` — уже даёт аяты, статусы, ревью, `PlaybackController`; состояние сессии держи в своём `HifzViewModel` (`ui/hifz`), а не в `ReaderViewModel`.
4. Логика, которую переносишь: `commonMain/.../presentation/hifz/HifzSessionScreen.kt` (раскрытие, оценки, переход к следующему), `presentation/surah/SurahViewModel.kt` (`completeReview`, `updateAyahStatus`), `presentation/review/Sm2Scheduler.kt`, `ReviewQuality.kt`.

## Зона

`ui/hifz/**` (вся логика и UI режима), `ui/i18n/HifzCopy.kt`, `hifzUiModule`. В `ui/reader/**` — только точечные хуки: кнопка «👁» в компактной шапке, передача состояния режима в строку аята, запуск режима по `route.hifz`. Каждый хук — минимальный (параметр/слот), основная логика — в `ui/hifz`.

## Что сделать

### Состояние — `HifzSession`
- Сессия = упорядоченный список аятов (глобальные номера) + индекс текущего + результаты (`ReviewQuality` по аяту). Источник:
  - `route.hifz = true` + `rangeStart..rangeEnd` → аяты диапазона (задача «Сегодня»);
  - кнопка «👁» в ридере → от текущего выбранного/первого видимого аята до конца суры (или до конца диапазона повтора, если он включён);
  - «Повторить трудные» → аяты с HARD/FORGOT из завершённой сессии.
- Сохраняется при повороте (`SavedStateHandle` в своём VM или `rememberSaveable` со своим `Saver`).
- Оценка: `SurahViewModel.completeReview(ayahNumber, surahNumber, quality)`. Для аятов без статуса — дополнительно `updateAyahStatus(..., AyahTodoStatus.LEARNING, scheduleNextReview = false)` (ревью уже запланирует `completeReview`). `EASY` на аяте `LEARNING` с интервалом ≥ 2 — предложить «Отметить выученным?» не надо; просто оставь как есть (SM-2 сам растянет интервал).

### UI — по 6.5
- Шапка ридера в режиме: фон `goldContainer`, заголовок «Заучивание · 4 / 10» (`AnimatedCounter`), полоса прогресса сессии, «✕» — выйти из режима (с подтверждением, если есть неоценённые аяты после хотя бы одной оценки).
- Скрытый текст: API 31+ — `Modifier.blur(14.dp)` + альфа 0.35 (анимируются к 0 при раскрытии за 320мс, `EmphasizedDecelerate`); ниже 31 — плейсхолдер «строки» (2–4 скруглённые полосы `surfaceStrong`, ширины по длине текста). Подсказка «первое слово» (`UiPrefs.hifzHintMode == FIRST_WORD`) — первое слово видно чётко.
- Только текущий аят интерактивен; остальные приглушены (альфа 0.5), уже оценённые — показаны раскрытыми с маленькой меткой оценки на розетке.
- Удержание текущего аята — «подсмотреть» (раскрытие, пока палец на аяте, пружина `snappy()`, `LongPress`-вибрация в начале).
- Под текущим аятом: «🎧 Послушать» (через `PlaybackController`, проигрывает только этот аят: `start(..., fromIndex)`, затем пауза по окончании — используй повтор аята = 1 и выключенный автопереход для этого проигрывания, восстанови настройки после) и `QhButton Primary` «Показать».
- После раскрытия выезжает ряд из 4 оценок (`QhButton` Tonal/Primary, цвета: Забыл — `due`, Трудно — `learning`, Хорошо — `primary`, Легко — `learned`), `Reject`-вибрация на «Забыл», `Tick` на остальных. Выбор → аят сворачивается (`animateContentSize`), список доезжает к следующему (`animateScrollToItem`, держать на ~30% высоты).
- Конец сессии: `QhSheet` с `ProgressRing` (доля Хорошо+Легко), разбивка по оценкам `AnimatedCounter`-ами, `CelebrationBurst` + `Success`-вибрация, кнопки «Повторить трудные» (если есть) и «Готово» (выход из режима).
- Во время режима док плеера свёрнут и не раскрывается.

### Копирайт
`ui/i18n/HifzCopy.kt` — 5 языков; формулировки из `hifzSessionCopy` и глоссария.

## Проверка
`./gradlew :composeApp:assembleDebug` и `./gradlew :composeApp:testDebugUnitTest`; unit-тест построения сессии (диапазон, «трудные») в `androidUnitTest`.

## Отчёт
Файлы; какие хуки добавлены в `ui/reader` (файл:строка); отклонения. Один коммит `redesign: memorize mode (2A)` с последней строкой `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`; путь worktree и ветка.
