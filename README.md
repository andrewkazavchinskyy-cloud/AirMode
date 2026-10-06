# AirMode

**Русский · [English below](#english)**

Бесплатное Android-приложение для заряда и режимов шума AirPods4/5. Kotlin, Jetpack Compose Material3 Expressive, системные цвета и тёмная тема. Без аккаунта, рекламы, подписки, сервера и INTERNET permission.

**Статус0.1.1: установочная предварительная версия. Пользователь подтвердил переключение режимов на Pixel10Pro CP41.260831.007.A3 с AirPods5; заряд и popup в0.1.0 не работали. Полная аппаратная приёмка остаётся открытой.** Результаты сборки и эмуляторов: [VERIFICATION](docs/VERIFICATION.md). Не считать APK гарантией переключения звука.

## Установить APK

1. Откройте [GitHub Releases](https://github.com/andrewkazavchinskyy-cloud/AirMode/releases), скачайте `AirMode-0.1.1.apk`. Репозиторий приватный: нужна учётная запись GitHub с доступом. Самому приложению аккаунт не нужен.
2. На телефоне разрешите установку из этого источника, если Android попросит, затем откройте APK и нажмите «Установить».
3. Спарьте AirPods через **системные настройки Bluetooth**, двойным тапом по передней части кейса. AirMode не выполняет спаривание.
4. Откройте AirMode, разрешите Bluetooth, по желанию уведомления, нажмите «Готово». Отказ в уведомлениях не блокирует заряд и плитку.
5. В настройках AirMode нажмите «Добавить плитку в шторку». Если система отказала: откройте шторку полностью → карандаш/«Изменить» → перетащите AirMode в активные плитки. Долгое нажатие открывает приложение.

## Модели

| Модель | Номера | Режимы |
|---|---|---|
| AirPods4 | A3053,A3050,A3054 | Только заряд |
| AirPods4 ANC | A3056,A3055,A3057 | Выкл/Шумодав/Прозрачность/Адаптивный |
| AirPods5 | A3531,A3532,A3533 | Все четыре |
| AirPods5, беспроводной кейс | A3439,A3440,A3441 | Все четыре |

Имя не доказывает поколение. Пока протокол не подтвердил модель, команды режима заблокированы. Pro/Max/AirPods1–3/Beats не поддерживаются. Долгое нажатие на ножку переключает доступные режимы без AirMode; доступные режимы зависят от настройки самих наушников.

## Android и ограничения

- Заряд: Android12/API31+. Реальные три компонента приходят из протокола или корректно сопоставленной BLE рекламы; неизвестное — прочерк. BLE проценты округлены до десятков. Один общий процент не выдаётся за три отдельных.
- Управление: рассчитано на исправленный стек Pixel Android16QPR3 с актуальным Google Play system update либо Android17. Но фактический успех зависит от доступности классического канала и прошивки, а не только номера Android.
- Pixel10Pro/11Pro, stable и beta входят в целевую матрицу. Будущие beta не гарантируются; exact build результаты — в VERIFICATION. Последние официальные [QPR3Beta1 notes](https://developer.android.com/about/versions/17/qpr3/release-notes) изучены, это не означает их физический тест.
- Используется узкий generic Android API bridge, Apache2.0; это не Bluetooth-библиотека и не установленный Xposed. Root и изменение VendorID не нужны. Android может заблокировать внутренний API; тогда появляется короткая ошибка с объяснением, заряд продолжается там, где есть достоверный источник.
- Системные метаданные могут быть недоступны обычному приложению. Случайный BLE адрес нельзя безопасно привязать по близости или имени; AirMode не показывает чужую батарею. Если кейс неизвестен, откройте его рядом с телефоном; если канал недоступен, используйте ножку и проверьте обновления телефона.
- Подтверждённый режим меняется только после ответа; через1.5s без нужного ответа показана ошибка. Запрос максимум один повтор через700ms, интервал отправки≥400ms.
- Android требует минимальное уведомление службы при активном канале. Постоянный заряд в нём по умолчанию выключен; включается в настройках. Popup8s по умолчанию включён, без звука.
- Фоновые ограничения телефона могут отклонить автозапуск; открытие приложения запускает разрешённую попытку. После отключения сокет закрывается сразу, служба останавливается через30s.

## Собрать в Android Studio

Откройте корневую папку проекта. Установите бесплатные Android SDK Platform36, Build Tools36.0.0 и JDK17. Gradle сам загрузит закреплённые зависимости; платные ключи не нужны. Для быстрой установки используйте Run или debug APK.

```bash
./gradlew testDebugUnitTest lintRelease assembleDebug assembleRelease
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Без переменных подписи release output unsigned; в Android Studio используйте **Build → Generate Signed Bundle/APK → APK → Create new key**. Для CLI задайте `AIRMODE_KEYSTORE`, `AIRMODE_STORE_PASSWORD`, `AIRMODE_KEY_ALIAS=airmode`, `AIRMODE_KEY_PASSWORD`. Ключи/пароли не хранить в Git. Опубликованный APK подписан отдельным release-ключом, который сохранён вне репозитория. Собственный ключ не обновит опубликованную установленную сборку — сначала удалить приложение, потеряв только локальные настройки.

CI проверяет unit tests/lint и собирает APK; CI unsigned release не заменяет подписанный GitHub Release. [Протокол и источники](docs/PROTOCOL.md), [решения](docs/DECISIONS.md), [физический чеклист](docs/DEVICE_TEST.md), [лицензии зависимостей](NOTICE).

AirMode не связан с Apple. AirPods — товарный знак Apple Inc. Лицензия приложения Apache-2.0.

## English

AirMode is a free offline Android battery and noise-mode companion for **AirPods4/5 only**. Kotlin, one Compose Material3 Expressive Activity, system dynamic colors and dark mode. No account, ads, analytics, subscription, server or INTERNET permission.

**0.1.1 is an installable prerelease. The user reported working noise control on Pixel10Pro CP41.260831.007.A3 with AirPods5, but missing battery and popup in0.1.0. Full hardware acceptance remains open.** See [verification evidence](docs/VERIFICATION.md); successful compilation/socket construction is not audible noise-mode control.

Download `AirMode-0.1.1.apk` from [Releases](https://github.com/andrewkazavchinskyy-cloud/AirMode/releases), allow installation from your browser if prompted, and install. This private repository requires authorized GitHub access for downloads; the app itself needs no account. Pair earbuds through Android Bluetooth settings using a double tap on the case front. Open AirMode, grant Nearby devices/Bluetooth, optionally notifications, and finish onboarding. Add the tile using AirMode Settings or the expanded shade → Edit/pencil → drag AirMode. Long-press opens the app.

Supported numbers are in the table above. AirPods4 withoutANC are battery-only. Pro/Max/olderAirPods/Beats are excluded. Names never authorize control: protocol model confirmation is mandatory. Long-pressing the stem still controls available modes without this app.

Minimum Android12/API31. Noise control targets corrected Pixel Android16QPR3/current Google Play system update and Android17, including Pixel10Pro/11Pro stable/beta. Exact OS builds are tracked; future beta compatibility cannot be guaranteed. A pinned generic Apache2 Android API bridge calls the native classic socket factory without root, Xposed runtime, vendor spoofing or global exemptions. A blocked/failed transport shows a truthful unavailable error. Three actual component readings are required; aggregate battery is not duplicated, uncertain random-address BLE reports are not attributed to a nearby pair. Unknown stays—; case readings older than2min are marked stale. Advertisement readings are quantized10% values.

UI confirms modes only from earbud responses. Missing acknowledgement causes rollback/error within1.5s, with one700ms retry and≥400ms write interval. Notification denial leaves app/tile functionality available. Optional silent popup lasts8s. Android requires a minimal foreground connection notification; optional continuous battery is disabled by default. Socket closes immediately after disconnect; service stops30s later. Background restrictions can require opening the app.

Build: open root in Android Studio, free SDK36/BuildTools36.0.0/JDK17; run the Gradle command above. Debug output is directly installable. Release requires your own signing key using Android Studio's Generate Signed APK flow or the four `AIRMODE_*` environment variables above. No paid keys. Signing secrets are excluded from Git. GitHub's published APK uses a dedicated persistent signing key; CI unsigned APK is not that release.

AirMode is not affiliated with Apple. AirPods is a trademark of Apple Inc. Application license: Apache-2.0. See [NOTICE](NOTICE), [protocol](docs/PROTOCOL.md) and [physical checklist](docs/DEVICE_TEST.md).

## Диагностика заряда / Battery diagnostics

`AirMode-0.1.1-diagnostics.apk` подписан тем же ключом и устанавливается поверх0.1.0, сохраняя настройки. В нём уже есть оба документированных варианта подписки и новый нативный popup. Подключите AirPods, откройте кейс рядом с телефоном, подождите15 секунд, выньте наушники и переключите режим. Настройки → семь нажатий на версию → «Отправить». Отправьте текст отчёта разработчику. «Проверить popup» использует явно демонстрационные80/54/76%, не данные наушников; главный экран никогда не подменяется тестовыми числами.

Отчёт хранится только в ограниченной памяти процесса, без Bluetooth-адресов, имён и серийных номеров. Включены только заголовки/счётчики сообщений, ограниченные пакеты батареи, состояние уведомлений и точная сборка телефона. Приложение не отправляет его автоматически. В обычном release диагностика отсутствует. Новый тихий канал popup имеет HIGH importance для системного heads-up; Android/DND и пользовательские настройки управляют показом. Подтверждённая модель может показать неизвестные значения «—», затем обновить их в пределах исходных8 секунд.

The same-key diagnostics APK updates0.1.0 and preserves settings. Connect AirPods, open the case near the phone, wait15s, take the buds out and change a mode. Settings → tap version seven times → Share report. The Test popup uses clearly labelled sample values, never repository/home data. The bounded in-memory report excludes addresses, names and serials; it is shared only through an explicit user action. Normal release has no diagnostics. The new silent HIGH channel requests native heads-up; OS/DND/user settings retain control. Unknown values stay— and update within the original8s lifetime.
