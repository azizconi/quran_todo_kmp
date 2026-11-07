# Задача 2B — экран «Прогресс»

Исполнитель: Sonnet. Ветка: твой worktree от `redesign/android` (фаза 1 слита). Параллельно идёт 2A (режим заучивания).

## Окружение worktree

1. Worktree может быть создан от `master`: первым делом `git merge --ff-only redesign/android`.
2. `cp /Users/azizjon/projects/quran_todo_kmp/local.properties .`
3. Собирай только `./gradlew` из корня worktree. Основной checkout не трогай. Эмулятор не используй.

## Прочитай сначала

1. `docs/redesign/ANDROID_REDESIGN.md` — разделы 1, 2, 3, **6.7** (это твой экран), 7, 8, 10.
2. `docs/redesign/API.md`.
3. Данные: `commonMain/.../presentation/stats/StatsViewModel.kt` (что считает), `presentation/home/HomeViewModel.kt` (`todoSurahs`, `ayahTodos`, `dueReviews`, `weakAyahKeys`, `completeQuran`, `chapterNames`, `surahList`), мёртвый `presentation/stats/StatsScreen.kt` (идеи подачи), главный экран `ui/home/**` (как он берёт имена сур по языку — переиспользуй подход, не копируй UI).

## Зона

`ui/progress/**`, `ui/i18n/ProgressCopy.kt`, `progressUiModule`. Тесты — `androidUnitTest/.../ui/progress/**`.

## Что сделать

- `ProgressViewModel` (свой Android-VM, общие VM через `get()`, не `koinViewModel`): итог (выученные аяты по `ayahTodos` LEARNED + все аяты сур со статусом LEARNED без дублей; в работе; процент от 6236; выученные суры; суры в работе), карта 114 сур (статус + доля выученных аятов), плитки (дней подряд — из `StatsViewModel`, к повторению сегодня — `dueReviews.size`, слабых — `weakAyahKeys.size`, аятов в работе), активность 14 дней (`StatsViewModel.last14DailyCounts`), слабые места по сурам (сура, число слабых, первые номера аятов в суре). Если `StatsViewModel` требует явного обновления — найди метод и вызови при открытии экрана. Подсчёт итогов — чистая функция с unit-тестом.
- Экран по 6.7:
  - `QhLargeTopBar` с «назад»; крупное `ProgressRing` 160dp — `sharedElementKey(SharedKeys.ProgressRing)` (на главном то же кольцо маленькое), внутри процент `AnimatedCounter` + «%», под кольцом строка итогов.
  - Карта Корана: `LazyVerticalGrid` не нужен — 114 ячеек в `FlowRow`/своей раскладке по 10 в ряд, ячейка — скруглённый квадрат: нет статуса — `surfaceMuted`, заучиваю — `learningContainer` с заливкой снизу вверх по доле аятов `learning`, выучено — `learned`. При появлении выученные проходят «волной» золотого блика (задержка по индексу 12мс, однократно, не при Reduce Motion). Нажатие — тултип/поповер над ячейкой (номер, имя, «N из M аятов», кнопка «Открыть» → `onOpenReader(Dest.Reader(n, origin = Origin.MAP))`). Подпись-легенда под картой.
  - Плитки 2×2 (`QhCard`): иконка, `AnimatedCounter`, подпись.
  - Активность 14 дней: столбики на Canvas, растут при появлении (стаггер 20мс), сегодняшний — `primary`, остальные — `primaryContainer`; нажатие — значение и дата над столбиком; подписи дней недели по языку.
  - Слабые места: список строк «сура · N слабых · аяты 3, 7, 12» с кнопкой «Повторить» → `onOpenReader(Dest.Reader(n, ayah = первый, origin = Origin.MAP, hifz = true, rangeStart = мин, rangeEnd = макс))`.
  - Пустое состояние (ничего не начато): `EmptyState` с приглашением и кнопкой «К сурам» (назад).
  - Стаггер секций при первом показе.

## Копирайт
`ui/i18n/ProgressCopy.kt` — 5 языков, глоссарий раздела 10.

## Проверка
`./gradlew :composeApp:assembleDebug` и `./gradlew :composeApp:testDebugUnitTest`.

## Отчёт
Файлы, отклонения, что проверить визуально. Один коммит `redesign: progress screen (2B)` с последней строкой `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`; путь worktree и ветка.
