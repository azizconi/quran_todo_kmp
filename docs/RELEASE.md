# Выпуск Android-версии

Как подписать и собрать релиз, что делать с ключом и что проверить перед публикацией. Версия приложения — `versionCode` / `versionName` в `composeApp/build.gradle.kts`.

## 1. Подпись релиза

Ключ и пароли **не лежат в репозитории**. Gradle читает их из `local.properties` (файл в `.gitignore`) или из переменных окружения (для CI):

| `local.properties` | переменная окружения | что это |
|---|---|---|
| `release.storeFile` | `RELEASE_STORE_FILE` | путь к `.jks` (абсолютный или от корня проекта) |
| `release.storePassword` | `RELEASE_STORE_PASSWORD` | пароль хранилища |
| `release.keyAlias` | `RELEASE_KEY_ALIAS` | алиас ключа |
| `release.keyPassword` | `RELEASE_KEY_PASSWORD` | пароль ключа |

Пример `local.properties` (значения свои; файл остаётся только на вашем компьютере):

```properties
sdk.dir=/Users/you/Library/Android/sdk
release.storeFile=/Users/you/keys/quran-hifz-upload.jks
release.storePassword=<пароль хранилища>
release.keyAlias=<алиас>
release.keyPassword=<пароль ключа>
```

Если задано не всё или файла ключа нет, релиз собирается **неподписанным** (`composeApp-release-unsigned.apk`); конфигурация Gradle при этом не падает, а при отсутствующем файле ключа печатает предупреждение. Неподписанный APK на устройство не установится: для проверки на телефоне подпишите сборку своим ключом (задайте свойства выше).

Новый ключ загрузки:

```bash
keytool -genkeypair -v -keystore quran-hifz-upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Хранить `.jks` и пароли — вне репозитория (менеджер паролей, резервная копия на отдельном носителе). Потеря ключа загрузки лечится сбросом в Play Console, потеря ключа подписи приложения без Play App Signing — нет.

## 2. Сборка

```bash
./gradlew :composeApp:assembleRelease   # APK: composeApp/build/outputs/apk/release/
./gradlew :composeApp:bundleRelease     # AAB для Google Play: composeApp/build/outputs/bundle/release/composeApp-release.aab
```

В Play Console загружается **AAB**. Сборка release включает R8 (`isMinifyEnabled`, `isShrinkResources`); правила — `composeApp/proguard-rules.pro`. Файл сопоставления имён (`composeApp/build/outputs/mapping/release/mapping.txt`) плагин Crashlytics выгружает в Firebase сам (задача `uploadCrashlyticsMappingFileRelease` входит в сборку release; нужен интернет). Руками: `./gradlew :composeApp:uploadCrashlyticsMappingFileRelease`. Play Console деобфусцирует ANR и сбои по `mapping.txt` из AAB.

### R8: что смотреть

R8 сам не сообщил ни об одной отсутствующей зависимости: Room, kotlinx.serialization, Ktor, Firebase и AndroidX несут собственные правила (`build/outputs/mapping/release/configuration.txt`). Свои правила — три: читаемые трассировки сбоев, имена классов исключений (идут в событие `non_fatal_error`) и явное сохранение маршрутов навигации `Dest` (они `@Serializable`: без сериализаторов каждый `navigate()` падает только в release). При обновлении библиотек или добавлении кода с рефлексией (`Class.forName`, `ServiceLoader`, JSON-классы без `@Serializable`) **прогоните release на устройстве**: холодный старт, первый запуск без данных, все экраны, звук, смена языка и темы, переключатель аналитики в настройках. Если что-то падает, смотрите `adb logcat` и расшифровывайте трассу через `mapping.txt` (`retrace`).

## 3. Что делать с ключом в git

В репозитории исторически лежат `composeApp/src/androidMain/keystoreq.jks` и `composeApp/release/composeApp-release.aab`. Теперь `.gitignore` закрывает `*.jks`, `*.keystore`, `*.aab`, `*.apk` и `composeApp/release/`, но **`.gitignore` не убирает уже отслеживаемые файлы**.

1. Сделайте копию `keystoreq.jks` вне репозитория (и убедитесь, что помните пароли).
2. Уберите файлы из индекса, не удаляя с диска: `git rm --cached composeApp/src/androidMain/keystoreq.jks composeApp/release/composeApp-release.aab`, затем коммит. Обычный `git rm` удалит файл с диска, а слияние такого коммита удалит ключ у каждого, кто его заберёт.
3. Положите путь и пароли в `local.properties` (раздел 1).

### Если репозиторий публичный или когда-либо был публичным

Ключ в истории git считайте скомпрометированным: его мог скачать кто угодно. **Все шаги ниже — только по решению владельца.**

1. Посмотрите в Play Console (Setup > App signing), включён ли **Play App Signing**. Если да (для AAB это норма), `keystoreq.jks` — всего лишь **ключ загрузки**, а подписывает приложение для пользователей ключ, который хранит Google: пользователи в безопасности, нужно заменить ключ загрузки.
2. Создайте новый ключ загрузки (команда `keytool` выше) и запросите **сброс ключа загрузки** в Play Console (Setup > App signing > Request upload key reset), приложив сертификат нового ключа. Пока сброс не подтверждён, старый ключ по-прежнему может подписать загрузку: не откладывайте.
3. Если Play App Signing **не** включён, `keystoreq.jks` — это ключ подписи самого приложения: сменить его нельзя иначе, чем через перевод на Play App Signing (Play Console предлагает «upgrade» для старых приложений). Разберитесь в этом до публикации обновления.
4. Вычистите ключ и `.aab` из истории: `git filter-repo --invert-paths --path composeApp/src/androidMain/keystoreq.jks --path composeApp/release/composeApp-release.aab`, затем принудительная отправка во все ветки и теги и повторное клонирование у всех, кто работает с репозиторием. Это **переписывание истории**: делается только с согласия владельца. Форки и кэши хостинга эта очистка не затрагивает; пароли, которыми защищался старый ключ, тоже считаются скомпрометированными (не используйте их повторно).

## 4. Конфиденциальность и Google Play

- Политика: `play_store_assets/privacy-policy-ru.md` и `-en.md`. Вместо e-mail разработчика в каждом файле политики и в `ui/settings/SettingsLinks.kt` (`ContactEmail`) стоит метка в двойных фигурных скобках (имя `CONTACT_EMAIL`) — замените её адресом; дату вступления в силу обновить при публикации. После публикации записать адрес в `PrivacyPolicyUrl` (там же): на экране «Конфиденциальность» появится кнопка «Полный текст», а в карточке магазина укажите тот же адрес.
- Play Console > App content: **Advertising ID** — «нет, приложение не использует рекламный идентификатор» (разрешение `AD_ID` удалено из манифеста, в метаданных выключен сбор Firebase); **Data safety** — аналитика Firebase и Crashlytics (события использования, диагностика и сбои, идентификатор установки; данные не продаются; можно отказаться в настройках). Опирайтесь на https://firebase.google.com/docs/android/play-data-disclosure.
- Микрофон приложению не нужен: разрешения `RECORD_AUDIO` в манифесте нет, на Android распознавание чтения отключено.
- Источники текста Корана, переводов, аудио и шрифтов, их авторы и условия перечислены на экране «Источники» (`ui/about/SourcesData.kt`); изменили состав переводов, чтецов или шрифтов — поправьте файл. Условие Tanzil: текст не изменяется, источник указывается со ссылкой.

## 5. Резервное копирование Android

Правила в `composeApp/src/androidMain/res/xml/backup_rules.xml` (Android 11 и ниже) и `data_extraction_rules.xml` (Android 12+, облачная копия и перенос на новый телефон). Копируются база `quran_todo.db` (с журналом `-wal`) и все SharedPreferences, кроме файлов Firebase и сервисов Google Play; скачанное аудио, кэши и `filesDir` не копируются. Обновляйте оба файла вместе: lint (`FullBackupContent`) проверяет их при сборке release. Если появится другая база или файлы, которые нужно переносить, добавьте их в `include`.
