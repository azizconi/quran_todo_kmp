# Задача 0A — дизайн-система Android

Исполнитель: Sonnet. Ветка: твой worktree от `redesign/android`. Параллельно идёт задача 0B (каркас приложения) — не трогай её файлы.

## Окружение worktree

`local.properties` не в git: перед первой сборкой выполни `cp /Users/azizjon/projects/quran_todo_kmp/local.properties .` в корне своего worktree. Сборка — только `./gradlew` из корня worktree. Не трогай основной checkout `/Users/azizjon/projects/quran_todo_kmp`.

## Прочитай сначала

- `docs/redesign/ANDROID_REDESIGN.md` — разделы 1, 2, 3, 5, 8 целиком. Это спецификация; токены и поведение бери оттуда дословно.
- Уже лежащие контракты (не менять сигнатуры): `ui/motion/SharedKeys.kt`, `ui/motion/SharedElements.kt`, `ui/motion/ReducedMotion.kt`, `ui/navigation/Dest.kt`.
- Для стиля кода: `composeApp/src/androidMain/kotlin/tj/app/quran_todo/presentation/settings/NativeSettingsScreen.kt`, `commonMain/.../common/theme/AppTheme.kt`.

Корень пакетов: `composeApp/src/androidMain/kotlin/tj/app/quran_todo/ui/`.

## Твоя зона (создаёшь/меняешь только это)

- `ui/theme/` — `QhColors.kt`, `QhTypography.kt`, `QhShapes.kt`, `QhSpacing.kt`, `QhTheme.kt`
- `ui/motion/` — `QhMotion.kt`, `Pressable.kt`, `Stagger.kt`, `AnimatedCounter.kt`, `Celebration.kt` (контрактные файлы из списка выше не меняй; если без правки никак — опиши в отчёте)
- `ui/haptics/Haptics.kt`
- `ui/components/` — все файлы из таблицы раздела 5
- `ui/catalog/CatalogScreen.kt` — витрина
- `composeApp/src/androidMain/res/font/` — Manrope
- `gradle/libs.versions.toml`, `composeApp/build.gradle.kts` — только если нужна новая зависимость (по возможности обойдись без них)

## Что сделать

### 1. Шрифт
Скачай Manrope (OFL) — статические TTF весов 400/500/600/700/800 (или variable TTF) из `https://github.com/google/fonts/tree/main/ofl/manrope` (raw-ссылки) в `res/font/manrope_regular.ttf` и т. д. (имена файлов — только `[a-z0-9_]`). Положи рядом `res/raw/`? — нет, лицензию положи в `docs/redesign/fonts/OFL-Manrope.txt`. Если скачать не получается — используй `FontFamily.Default` и напиши это в отчёте; остальную работу не блокируй.

### 2. Тема
- `QhColors` — data class со всеми токенами таблицы 2.1 (+ `readerHighlight`, `scrim`, `isDark`). Функция `qhColors(mode: ThemeMode, palette: ThemePalette): QhColors` для 3 палитр × 2 режимов. Общие enum-ы `ThemeMode`/`ThemePalette` из commonMain не менять.
- `QhTypography` — все стили таблицы 2.2 как `TextStyle` (Manrope); `quran*` стили — фабрики, принимающие `FontFamily` и размер.
- `QhShapes`, `QhSpacing` — по 2.3.
- `QhTheme(mode, palette, reducedMotion: Boolean, hapticsEnabled: Boolean, content)`:
  - провайдит `LocalQhColors`, `LocalQhTypography`, `LocalQhShapes`, `LocalHaptics`, `LocalReducedMotion`;
  - оборачивает в M3 `MaterialTheme` со схемой, смапленной из токенов (primary, onPrimary, primaryContainer, background=bg, surface, surfaceVariant=surfaceMuted, outline=hairline, outlineVariant, error=danger, surfaceContainer* — из surface/surfaceMuted/surfaceStrong), и M3 `Typography` из наших стилей, чтобы M3-компоненты (ModalBottomSheet, Slider, Switch) выглядели нашими;
  - внутри дополнительно вызывает старый `tj.app.quran_todo.common.theme.AppTheme(mode, palette)` — пока не все экраны переписаны, старые экраны встраиваются внутрь и должны выглядеть как раньше.
  - доступ из кода: `QhTheme.colors`, `QhTheme.type`, `QhTheme.shapes` (object с `@Composable get()`).

### 3. Motion и тактильность
По разделу 3: `QhMotion` (токены), `Modifier.pressable(...)`, `Modifier.staggeredEnter(...)` (с `rememberSaveable`-флагом «уже показан» на уровне экрана — сделай `rememberStaggerState()`), `AnimatedCounter`, `CelebrationBurst`. `Haptics` по таблице 3.6 через `LocalView.current.performHapticFeedback`, с fallback для старых API, выключаемый флагом. Всё уважает `LocalReducedMotion`.

### 4. Компоненты
Всё из таблицы раздела 5 + орнамент из 2.4 (`Ornaments.kt`: `EightPointStar`, `SurahNumberBadge`, `StarLattice`, `OrnamentDivider`, `SurahCartouche`, `AyahRosette`). Требования:
- Публичное API — простое, с `modifier: Modifier = Modifier` первым необязательным параметром, в стиле Compose. KDoc одной строкой на компонент.
- Никаких хардкод-цветов в компонентах — только `QhTheme.colors`.
- Все кликабельные — через `pressable` (вибрация + пружина).
- `SurahNumberBadge(number: Int, status: SurahTodoStatus?, learnedFraction: Float)` — статус из commonMain `SurahTodoStatus`.
- `AyahWheel` — переработай существующее колесо из `commonMain/.../presentation/surah/ReaderPlayerSheet.kt` (не меняя тот файл): `Step`-вибрация на смене значения, затухание краёв (`drawWithContent` + градиентная маска), снап.
- `QhToast` + `LocalToaster` + `ToastHost(modifier)` — хост кладётся в корень приложения задачей 0B; ты даёшь `ToastHost` и `rememberToaster()`.
- `QhSheet` поверх M3 `ModalBottomSheet`.
- `CircularReveal.kt` не делай — это задача 2C.

### 5. Витрина
`CatalogScreen(onBack: () -> Unit)` — прокручиваемая страница с разделами: цвета (свотчи токенов), типографика, кнопки (все варианты/размеры/disabled/loading), чипы, сегменты, поиск, статусы, бейджи-звёзды во всех статусах и с прогрессом 0/0.4/1, орнаменты, розетки, прогресс-кольцо/полоса (с кнопкой «анимировать»), счётчик (кнопки +/−), колесо, строки настроек, слайдер, скелетон, пустое состояние, тост (кнопки «успех»/«ошибка»), лист, празднование (кнопка). Сверху переключатели тема/палитра/Reduce Motion, локальные для витрины (оберни содержимое в `QhTheme` с выбранными значениями). Задача 0B подключит маршрут `Dest.Catalog` к этому экрану.

## Проверка

1. `./gradlew :composeApp:assembleDebug` — зелёный.
2. `./gradlew :composeApp:testDebugUnitTest` — зелёный.
3. Эмулятор не используй — он занят задачей 0B. Визуальную приёмку витрины делает оркестратор после слияния.

## Отчёт (последнее сообщение)

- Список файлов и публичное API (сигнатуры) каждого компонента — коротко, это будут читать исполнители следующих фаз.
- Что не сделано или сделано иначе и почему.
- Скачан ли шрифт; проверены ли таджикские буквы.
- Закоммить всё в своей ветке одним коммитом `redesign: design system foundation (0A)`.
