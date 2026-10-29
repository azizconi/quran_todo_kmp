# Задача 5 — убрать звёзды, добавить каллиграфию

Исполнитель: Sonnet. Ветка: твой worktree от `redesign/android`. Ты единственный активный исполнитель.

## Зачем

Владелец: восьмиконечная звезда из двух квадратов (и любые звёзды вообще) в иллюстрациях недопустима — она читается как звезда Давида. **В приложении не должно остаться ни одной звезды**: ни шести-, ни восьми-, ни пятиконечной, ни «искр». Орнаментальный язык теперь: арабская каллиграфия (шрифт Amiri Quran), арка-михраб (как на иконке приложения), лепестковый медальон, мавританская (огивальная) решётка, ромб.

## Окружение

1. Worktree может быть создан от `master`: первым делом `git merge --ff-only redesign/android`.
2. `cp /Users/azizjon/projects/quran_todo_kmp/local.properties .`
3. Собирай только `./gradlew` из корня worktree. Основной checkout не трогай. Эмулятор не нужен (оркестратор проверит).

## Прочитай

`ui/components/Ornaments.kt` (всё), `docs/redesign/API.md` (раздел дизайн-системы). Как шрифты из `composeResources` берутся в androidMain — посмотри `commonMain/.../common/utils/Utils.kt` (`Res.font.amiri_quran`) и места в `ui/reader`/`ui/settings`, где строится `FontFamily`.

## Новые компоненты (в `ui/components/Ornaments.kt`)

Удали всю звёздную геометрию: `NotchRatio`, `starSquare`, `starSilhouette`, `drawStar`, `EightPointStar`, `AnimatedEightPointStar`, `StarLattice`. Шапку файла перепиши: мотив — каллиграфия, михраб, медальон, огивальная решётка; звёзд нет.

### 1. `CalligraphyText`
```kotlin
object Calligraphy {
    const val Bismillah = "﷽"           // в Amiri Quran это одна каллиграфическая лигатура ﷽
    const val Iqra = "ٱقْرَأْ"
    const val Alhamdulillah = "ٱلْحَمْدُ لِلَّهِ"
    const val Hifz = "حِفْظ"
}

@Composable
fun CalligraphyText(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,            // кегль в dp: это иллюстрация, системный масштаб шрифта не применяется
    color: Color = QhTheme.colors.gold,
    revealMillis: Int = 1_400,   // 0 — без проявления
    revealDelayMillis: Int = 0,
    shimmer: Boolean = true,
)
```
- Шрифт — Amiri Quran (`Res.font.amiri_quran`), `remember`-нуть `FontFamily`. `TextDirection.Rtl`, `TextAlign.Center`, одна строка, `softWrap = false`. Высота строки ≈ 1.7× кегля, `LineHeightStyle(Alignment.Center, Trim.None)` — огласовки и хвосты лигатуры не должны обрезаться.
- **Вписывание по ширине**: `BoxWithConstraints` + `rememberTextMeasurer()`; если ширина текста при `size` больше `maxWidth`, кегль уменьшается пропорционально (﷽ в Amiri ≈ 9× кегля в ширину).
- **Проявление справа налево, как чернила**: `Animatable` 0→1, `tween(revealMillis, revealDelayMillis, FastOutSlowInEasing)`. Слой `graphicsLayer { compositingStrategy = Offscreen }` + `drawWithContent`: после `drawContent()` маска `drawRect(Brush.horizontalGradient(listOf(Transparent, Black), startX = e, endX = e + f), blendMode = BlendMode.DstIn)`, где `f = 0.18 * width`, `e = lerp(width + f, -f, progress)` — видна правая часть, край мягкий.
- **Блик** (если `shimmer`): после проявления один проход за 900 мс справа налево полосой шириной `0.22 * width`: градиент `Transparent → lerp(color, Color.White, 0.55f) → Transparent`, `BlendMode.SrcAtop` (только по буквам). Один раз, без бесконечной анимации.
- `LocalReducedMotion` — сразу полностью, без блика.
- Семантика декоративная: `clearAndSetSemantics { }`.

### 2. `MihrabCalligraphy(modifier, width: Dp = 88.dp, word: String = Calligraphy.Iqra)`
Арка-михраб, которая рисуется по контуру, и слово внутри. Высота `h = width * 7 / 6`. Путь арки для отступа `i` (px), `w` — ширина:
```
L = 0.12w + i;  R = 0.88w - i;  top = 0.04h + 1.2i;  shoulder = 0.45h;  bottom = h - i
M(L, bottom) L(L, shoulder)
C(L, 0.22h) (0.30w, 0.12h + 0.6i) (0.5w, top)
C(0.70w, 0.12h + 0.6i) (R, 0.22h) (R, shoulder)
L(R, bottom)
```
- Внешняя арка `i = 0`: золото, 1.5dp, `StrokeCap.Round`, рисуется trim 0→1 за 900 мс (переиспользуй `drawPartialPath`). Внутренняя `i = 6dp`: золото @ 50%, 1dp, стартует на 150 мс позже. Основание: линия от `0.04w` до `0.96w` по низу, 1.5dp, появляется вместе с внешней аркой.
- Слово: `CalligraphyText(word, size = width * 0.36f, revealMillis = 700, revealDelayMillis = 450, shimmer = false)`, по центру, базовая линия ≈ `0.74h`.
- Reduced motion — всё сразу.

### 3. `Medallion` и бейдж суры
```kotlin
private fun medallionPath(center: Offset, radius: Float, lobes: Int = 12): Path
```
12 лепестков: точки `P_k` на окружности `0.90 * radius` под углами `-π/2 + k·2π/12`; между соседними точками — `quadraticBezierTo(control, P_{k+1})`, контрольная точка на `1.06 * radius` в середине угла. Внешний край кривой ≈ `0.97 * radius`.

`@Composable fun Medallion(modifier, size: Dp = 40.dp, color = gold, strokeWidth: Dp = 1.5.dp, fill = Transparent)` — заливка силуэта (если `fill.alpha > 0`), затем обводка; `radius = (size/2 - stroke) / 0.97`.

`SurahNumberBadge` — та же логика статусов, что сейчас, но вместо звезды медальон: радиус `lerp(0.44, 0.38, ringAlpha) * size` (при LEARNING медальон чуть меньше, снаружи кольцо прогресса), обводка `0.034 * size`, заливка `learned` с `fillAlpha`. Кольцо прогресса, «поп», цифры — без изменений. KDoc: «медальон», не «звезда».

### 4. `ArabesqueLattice` (вместо `StarLattice`, те же параметры: `modifier, color, alpha = 0.07f, cell = 28.dp, scrollOffset, parallax = 0.2f`)
Огивальная решётка из двух зеркальных синусоид на колонку. Вертикальный период `P = 1.5 * cell`. Для колонок `k = -1..cols+1`, `x0 = k * cell`: две ломаные `x = x0 ± (cell / 2) * sin(2π·y / P)`, `y` от `-P` до `height + P`, шаг `P / 16`. Точки радиусом 1.3dp в местах касания `(x0 + cell/2, j·P + 0.75P)`, тот же цвет. Путь строится в `drawWithCache`; параллакс как сейчас, но по модулю `P`. Линия 1dp.

### 5. `SurahCartouche` — вместо звёзд по бокам «ушки»-медальоны
В центре каждой боковой стороны: круг r = 10dp с заливкой `background` и золотой обводкой 1.5dp; внутри круг r = 5.2dp, золото @ 55%, 1dp; в центре точка r = 1.8dp золотом. Отступ рамки по горизонтали — по радиусу ушка (10dp).

### 6. `OrnamentDivider`
Hairline с градиентом (как сейчас) — точка r = 1.4dp — ромб (полудиагональ 4.5dp, залит) — точка — hairline; точки на ±11dp от центра; всё цветом `line` (золото @ 60%). Центр — `Canvas` 36×10dp.

## Замены по коду (`ui/**`)

| Где | Было | Стало |
|---|---|---|
| `onboarding/WelcomeRosette.kt` | две звезды | переименуй в `WelcomeCalligraphy.kt` / `WelcomeCalligraphy()`: мягкое золотое радиальное свечение (как сейчас, появляется через 900 мс) и `CalligraphyText(Calligraphy.Bismillah, size = 44.dp, revealMillis = 1_600)`, ширина до 320dp (вписывание по ширине). Без «дыхания». |
| `onboarding/OnboardingSteps.kt:174,192` | `StarLattice`, `WelcomeRosette()` | `ArabesqueLattice(alpha = 0.09f)`, `WelcomeCalligraphy()`; комментарий про «star lattice» поправь |
| `components/Surfaces.kt:116` | `StarLattice` | `ArabesqueLattice` |
| `components/EmptyState.kt` | `AnimatedEightPointStar(88.dp)` | `MihrabCalligraphy(width = 88.dp)`; KDoc |
| `reader/components/NextSurahCard.kt:127` | `AnimatedEightPointStar(72.dp, breathing)` | `CalligraphyText(Calligraphy.Alhamdulillah, size = 44.dp, revealMillis = 1_200)` |
| `home/components/TopBarActions.kt:87` | залитая звезда при 0% | `Medallion(size = 15.dp, color = gold, strokeWidth = 1.25.dp, fill = gold @ 30%)`; KDoc |
| `progress/components/RingHero.kt:95` | звезда 18dp над процентом | `CalligraphyText(Calligraphy.Hifz, size = 20.dp, revealMillis = 700, shimmer = false)`; содержимое должно по-прежнему помещаться в кольцо (уменьши отступ/высоту строки при необходимости); KDoc |
| `settings/ReaderPreviewCard.kt:83` | звезда 14dp | `Medallion(size = 14.dp, strokeWidth = 1.dp)` |
| `progress/components/QuranMap.kt:358,376` | `drawStar` в выученной клетке | `drawCheck`: галочка по точкам (0.28, 0.53) → (0.44, 0.68) → (0.73, 0.36) от размера клетки, толщина `0.11 * cell`, круглые концы, `onLearned @ 0.72`; KDoc |
| `components/Chips.kt:141-143`, `home/components/SurahSheet.kt`, `reader/components/SurahSheet.kt`, `reader/components/AyahSheet.kt` | `StarBorder` / `StarHalf` / `Star` | `RadioButtonUnchecked` / `Contrast` (полузалитый круг; если в нашей версии material-icons-extended его нет — `Timelapse`) / `CheckCircle`; KDoc «контур, половина, звезда» → «круг, полукруг, галочка» |
| `home/components/TodaySection.kt:391,434` | `AutoAwesome` (искры) | карточка «Новое» — `Icons.AutoMirrored.Rounded.MenuBook`; приглашение — `Icons.Rounded.Lightbulb` (не `TipsAndUpdates`: в нём искры) |
| `settings/SettingsScreen.kt:594` | `StarRate` | `Icons.Rounded.ThumbUp` |
| `catalog/CatalogScreen.kt` | образцы звёзд и `StarLattice` | образцы `Medallion` (24/48/72, с заливкой), `CalligraphyText` (Bismillah, Iqra, Alhamdulillah), `MihrabCalligraphy`, `ArabesqueLattice` |
| `res/drawable/ic_stat_quran.xml` | звезда | Коран на подставке-рехале, см. ниже |

Иконка уведомления (24×24, один путь, `fillType="evenOdd"`, белый). Комментарий: «Monochrome status-bar icon: an open Quran on a rehal (book stand)…»:
```
M11.25,7.3 C9.3,5.7 6.3,5.2 2.5,5.6 L2.5,14.6 C6.3,14.2 9.3,14.8 11.25,16.4 Z
M12.75,7.3 C14.7,5.7 17.7,5.2 21.5,5.6 L21.5,14.6 C17.7,14.2 14.7,14.8 12.75,16.4 Z
M6.4,15.9 L7.7,15.2 L18.3,21.2 L17.0,21.9 Z
M17.6,15.9 L16.3,15.2 L5.7,21.2 L7.0,21.9 Z
```

## Документация

- `docs/redesign/ANDROID_REDESIGN.md`: строка 50 (мотив), 126–129 (компоненты), 306 (Hero), 318 (EmptyState), 327 (`◇ — звезда-бейдж` → медальон), 337 (онбординг: каллиграфия ﷽ вместо розетки), 350, 383 («контур / полузвезда / звезда»), 487 («бейдж-звезда»). Добавь в раздел мотива правило: **никаких звёзд в любом виде**.
- `docs/redesign/API.md`: строка 52 (новые сигнатуры), 78 (иконка уведомления — рехаль).

## Проверка

- `grep -rniE "star|звезд" composeApp/src/androidMain` — остаются только `start`/`restart` и подобное; ни одного рисунка звезды. Проверь и строки в `ui/i18n/**` (TalkBack-описания не должны говорить «звезда»/«star»/«yıldız»/«yulduz»/«ситора»).
- `./gradlew :composeApp:assembleDebug` и `./gradlew :composeApp:testDebugUnitTest`.

## Отчёт

Что сделано по пунктам (файл:строка), отклонения. Один коммит `redesign: calligraphy and arabesque instead of stars (5)` с последней строкой `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`; путь worktree и ветка.
