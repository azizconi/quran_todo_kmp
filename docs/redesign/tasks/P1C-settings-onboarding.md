# Задача 1C — настройки и онбординг

Исполнитель: Sonnet. Ветка: твой worktree от `redesign/android` (фаза 0 слита). Параллельно идут 1A (главный) и 1B (ридер).

## Окружение worktree

`local.properties` не в git: перед первой сборкой `cp /Users/azizjon/projects/quran_todo_kmp/local.properties .` в корне worktree. Собирай только `./gradlew` из корня worktree. Основной checkout не трогай. Эмулятор не используй — визуальную приёмку делает оркестратор и пришлёт замечания.

## Прочитай сначала

1. `docs/redesign/ANDROID_REDESIGN.md` — разделы 1, 2, 3, 4, **6.1–6.2, 6.6** (твои экраны), 8, 10.
2. `docs/redesign/API.md` — API дизайн-системы и каркаса.
3. Текущие экраны (переносишь поведение): `androidMain/.../presentation/settings/NativeSettingsScreen.kt`, `common/i18n/NativeSettingsCopy.kt`, `commonMain/.../presentation/onboarding/LanguageOnboardingScreen.kt`, `FontOnboardingScreen.kt`, `GoalsOnboardingScreen.kt`, `ui/QuranHifzApp.kt` (какие `Local*Setter` доступны и как корень решает, показывать ли онбординг).

## Зона

`ui/settings/**`, `ui/onboarding/**`, `ui/i18n/SettingsCopy.kt`, `ui/i18n/OnboardingCopy.kt`, `settingsUiModule`/`onboardingUiModule` в `ui/di/UiModules.kt`. Нужен общий компонент, которого нет, — локально в своей папке, упомяни в отчёте.

## Настройки — по разделу 6.6

- Все изменения — через корневые сеттеры (`LocalAppLanguageSetter`, `LocalThemeModeSetter`, `LocalThemePaletteSetter`, `LocalReadingFontStyleSetter`, `LocalAppSettingsSetter`) и `UiPrefs`, а не прямой записью в storage, чтобы весь UI обновлялся сразу. Настройки ридера (`SurahReaderPreferences`) — `save()` + `SettingsChangeNotifier.notifyChanged()`.
- Тема «Системная» — `UiPrefs.themeFollowsSystem` (см. как это сделано в корне).
- Палитры: три круга с превью цвета `primary` каждой палитры (из `qhColors(mode, palette)`), выбранный — кольцо + галочка (`bouncy()`), `Segment`-вибрация.
- Лист типографики: превью аята (Аль-Фатиха 1:2) на `readerBg`, слайдер 22–40, карусель шрифтов (`ReadingFontStyle.entries`, название + описание из `NativeSettingsCopy`, «بِسْمِ ٱللَّهِ» этим шрифтом).
- Звук: скорость и повтор аята — чипы прямо в строке (не отдельный лист).
- Заучивание: «Аятов в день» — шаговый счётчик (1…50) → `AppSettings.dailyGoal`; «Подсказка первого слова» → `UiPrefs.hifzHintMode`.
- Данные: оффлайн-пакет (`HomeViewModel.startOfflinePackage(language)`, прогресс из `offlineDownloadDone/Total`, встроенная полоса прогресса), «Резервная копия» (`syncProgressToCloud(текущие AppSettings)` — не хардкодить значения, как в старом экране), «Восстановить» (диалог, `restoreProgressFromCloud()`), `syncStatusMessage` — тостом.
- Удобство: тактильный отклик (`UiPrefs.hapticsEnabled`), «Меньше анимаций» (`UiPrefs.reduceMotion`).
- О приложении: версия (`BuildConfig.VERSION_NAME` или `PackageManager`), политика конфиденциальности — найди URL в репозитории (`grep -ri privacy` по проекту, store-ассетам и документам); если не найден — строку не показывай и напиши в отчёте; «Оценить» — `market://details?id=tj.app.quran_todo` с fallback на https.
- Заголовок: `QhLargeTopBar`, иконка шестерёнки в заголовке — `sharedElementKey(SharedKeys.SettingsIcon)`.
- Смена темы — пока обычная (круговое раскрытие сделает 2C); смена языка — кроссфейд контента.

## Онбординг — по разделу 6.2

- Один экран с шагами (`AnimatedContent`, shared-axis X), индикатор шагов (точки → пилюля), системный «назад» возвращает на предыдущий шаг (`BackHandler`), на первом шаге — выход из приложения как обычно.
- Шаг 1: рисующаяся розетка (`EightPointStar` с анимацией trim контура 0→1 за 1.2с, затем «дыхание»), заголовок, 5 карточек языков (монограмма + родное имя). Выбор — через `LocalAppLanguageSetter`, тексты экрана меняются кроссфейдом.
- Шаг 2: превью (Ар-Рахман 1–4 — возьми текст из `HomeViewModel.uiState.completeQuran`, если загружен; иначе вшитая строка из старого `FontOnboardingScreen`), карусель шрифтов (`HorizontalPager` или `LazyRow` со снапом), слайдер размера 22–40 со `Step`-вибрацией.
- Шаг 3: тема (Светлая / Тёмная / Системная) + 3 палитры, превью мини-карточки hero.
- Шаг 4 (можно пропустить): чипы «Читать / Слушать / Заучивать» → `UiPrefs.onboardingGoals`; при «Заучивать» — счётчик «аятов в день» (1/3/5/10) → `AppSettings.dailyGoal`.
- Финиш: язык и шрифт должны быть сохранены (сеттеры сохраняют сами — проверь), телеметрия `onboarding_completed` по шагам как в старом `App()`, затем `onFinished()`.

## Копирайт
`ui/i18n/SettingsCopy.kt` и `OnboardingCopy.kt` — 5 языков; переиспользуй `NativeSettingsCopy` и `AppStrings` (онбординговые строки) где подходят.

## Проверка
1. `./gradlew :composeApp:assembleDebug` и `./gradlew :composeApp:testDebugUnitTest`.
2. Критерии раздела 8 по коду.

## Отчёт
Файлы; отклонения от спецификации; найден ли URL политики; что проверить визуально в первую очередь. Один коммит `redesign: settings and onboarding (1C)` с последней строкой `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`; в отчёте — путь worktree и ветка.
