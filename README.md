# AirMode

**Русский · [English below](#english)**

Бесплатное приложение для заряда и режимов шума наушников Apple AirPods и Sony. Kotlin, один экран Compose Material 3 Expressive, системные цвета и тёмная тема. Без аккаунта, рекламы, подписки, сервера и INTERNET permission.

**0.2.0 — предварительная версия.** На Pixel 10 Pro CP41.260831.007.A3 / SDK37 пользователь подтвердил работу AirPods5; отчёт0.1.2 содержит входящий Adaptive mode4. Кейс в этом отчёте сообщает «недоступен». Новое чтение BLE кейса и расширенные модели требуют физических тестов. Sony экспериментальный: доступные функции определяются ответами модели и прошивки, совместимость со всеми Sony не заявлена. [Проверки](docs/VERIFICATION.md), [аппаратный чеклист](docs/DEVICE_TEST.md).

## Установить

1. В [Release0.2.0](https://github.com/andrewkazavchinskyy-cloud/AirMode/releases/tag/v0.2.0) скачайте **AirMode-0.2.0.apk**. Для проверки кейса используйте **AirMode-0.2.0-diagnostics.apk**. Подпись прежняя, можно устанавливать поверх предыдущих версий. Приватный репозиторий требует GitHub-доступа; самому приложению аккаунт не нужен.
2. Разрешите установку из браузера, если Android попросит, откройте APK. Сопряжение наушников выполняйте в системных настройках Bluetooth способом своей модели.
3. Откройте AirMode, разрешите Bluetooth, по желанию уведомления, нажмите «Готово». Отказ в уведомлениях не блокирует заряд и переключение.
4. Настройки AirMode → «Добавить виджет на главный экран». Либо удерживайте пустое место рабочего стола → Виджеты → AirMode. Кнопки выбирают доступные режимы напрямую.
5. Настройки AirMode → «Добавить плитку в шторку». Если система отказала: полностью откройте шторку → Изменить/карандаш → перетащите AirMode. Долгое нажатие плитки открывает приложение.

## Apple

Распознаются все текущие беспроводные семейства AirPods из [справочника Apple](https://support.apple.com/en-us/109525). Это список реализованных моделей, а не аппаратная квалификация каждой прошивки.

| Семейство | Номера наушников | Режимы |
|---|---|---|
| AirPods1 | A1523,A1722 | Заряд |
| AirPods2 | A2032,A2031 | Заряд |
| AirPods3 | A2565,A2564 | Заряд |
| AirPods4 | A3053,A3050,A3054 | Заряд |
| AirPods4 ANC | A3056,A3055,A3057 | Все четыре |
| AirPods5 | A3531,A3532,A3533,A3439,A3440,A3441 | Все четыре |
| Pro1 | A2084,A2083 | Выкл/Шумодав/Прозрачность |
| Pro2 | A2931,A2699,A2698,A3047,A3048,A3049 | Все четыре |
| Pro3 | A3063,A3064,A3065 | Все четыре |
| Max1 | A2096,A3184 | Три режима, один заряд |
| Max2 | A3454 | Все четыре, один заряд |

Имя Bluetooth не разрешает команды: нужна свежая модель из протокола. Номера кейсов не считаются моделями наушников. Beats и проводные EarPods пока не реализованы. Физическое управление ножкой/кнопкой работает без AirMode.

## Sony

Native RFCOMM MDR/Tandem V1/V2: сначала проверяются опубликованный сервис, ответ версии, модель и capabilities, затем текущие настройки. Семейства WH/WF/WI/MDR/LinkBuds и ULT WEAR допускаются только после этой проверки. Поддержанные схемы дают общий заряд полноразмерных наушников либо отдельные Л/П/Кейс у TWS; доступные Выкл/Шумодав/Прозрачность зависят от capabilities. Неизвестные схемы оставляют прочерки или отключают управление с объяснением.

Adaptive Sound Control Sony не приравнивается к четвёртому режиму AirPods. Перед тестом закройте Sony Sound Connect, чтобы он не занимал канал. Разные поколения и прошивки требуют проверки; гарантии «любой Sony работает» нет. [Схемы и источники](docs/PROTOCOL.md).

## Заряд, задержка и Android

- Android12/API31+, min31/compile36/target36. Дизайн следует системным цветам и теме; нет собственного переключателя темы.
- Заряд поступает из живого протокола или сопоставленного BLE объявления. Неизвестное — «—», недоступный/старый процент отдельно подписан. Общий заряд не дублируется в Л/П/Кейс; Max/WH показывают одно число.
- Для неизвестного кейса откройте его рядом и нажмите «Обновить заряд». BLE окна4s, не чаще одного в15s. Когда живая Apple-сессия выдаёт ключи proximity, они временно используются в памяти для связи частного BLE адреса с текущей парой и чтения известных зашифрованных схем. Нет связи по RSSI/имени. Ключи не сохраняются и не попадают в отчёт. Эта схема ещё не подтверждена на вашем AirPods5.
- Живой заряд кейса из BLE не затирается сообщением «кейс недоступен» от канала наушников. Android-кэш не выдаётся за свежее показание. Закрытый/далёкий кейс может не передавать заряд.
- Режим подсвечивается только по ответу наушников. Анимация ожидания≤1.5s; затем текст «Ждём подтверждение», ответ принимается до2.5s. Это учитывает реальные ответы вашего Pixel около1.8s. Один повтор через700ms, физические записи≥400ms. Ошибка после2.5s, поздний реальный ответ обновляет подтверждённый режим.
- Apple-управление зависит от Classic L2CAP PSM0x1001 и стека телефона. Целевая матрица — Pixel10Pro/11Pro stable/beta; каждая точная сборка квалифицируется отдельно. Будущая beta не гарантируется. Заблокированный канал выдаёт короткую ошибку, доступный заряд остаётся.
- Узкий Apache2 Android API bridge вызывает только нативную Classic-фабрику: без root, Xposed runtime, VendorID и глобальных exemptions. Sony использует публичную RFCOMM-фабрику.
- Adaptive Apple объявляет документированные возможности один раз после явного выбора. На некоторых прошивках маска имеет более широкие эффекты; проверьте отсутствие изменений при разговоре. Настройки Conversation Awareness не отправляются.
- Тихий системный popup8s включён по умолчанию, постоянный заряд выключен. Android требует минимального foreground-уведомления, пока открыт канал. После отключения канал закрывается сразу, служба останавливается через30s. Ограничения фона могут потребовать открыть приложение.

## Сборка

Откройте корень в Android Studio, установите бесплатные SDK36/BuildTools36.0.0/JDK17.

```bash
./gradlew testDebugUnitTest lintRelease assembleDebug assembleRelease
```

Debug: app/build/outputs/apk/debug/app-debug.apk. Без ключа release unsigned. В Android Studio: Build → Generate Signed Bundle/APK → APK → Create new key. CLI: AIRMODE_KEYSTORE, AIRMODE_STORE_PASSWORD, AIRMODE_KEY_ALIAS=airmode, AIRMODE_KEY_PASSWORD. Секреты вне Git. Собственный ключ не обновляет APK, подписанный ключом этого проекта. CI unsigned APK отличается от подписанного Release.

## Диагностика

Установите diagnostics APK поверх текущего. Переподключите наушники, откройте кейс рядом, нажмите «Обновить заряд», проверьте приложение/плитку/виджет. Настройки → семь нажатий на версию → Отправить. Передайте текст отчёта вручную. Отчёт показывает источники, времена, факт получения ключей и числа распознанных объявлений; без адресов, имён, серийных номеров, ключей и encrypted payload. Ограниченная память процесса, без автоматической отправки/Logcat/диска. Обычная сборка удаляет диагностику. «Проверить popup» явно показывает тестовые значения.

AirMode не связан с Apple или Sony. AirPods — товарный знак Apple Inc. Apache-2.0. [NOTICE](NOTICE), [решения](docs/DECISIONS.md).

## English

Free offline Android battery/noise-mode app for Apple AirPods and experimentally Sony. Kotlin, one Compose Material3 Expressive Activity, dynamic colors/system dark mode. No account, ads, analytics, subscription, server or INTERNET permission.

**0.2.0 is a prerelease.** The user's Pixel10Pro CP41.260831.007.A3 / SDK37 report confirms received AirPods5 mode4; its case is unavailable. New BLE case decoding and expanded models require hardware qualification. The Apple table lists implemented current AirPods families, not tested interoperability for every firmware. Beats/wired EarPods are not implemented. Sony functions are gated by live protocol/model/capabilities; universal Sony compatibility is not claimed.

Download normal or diagnostics APK from [Release0.2.0](https://github.com/andrewkazavchinskyy-cloud/AirMode/releases/tag/v0.2.0). Same signing key supports in-place updates. Private GitHub access is required for downloads, not for app use. Pair through Android settings. Open AirMode, grant Bluetooth and optionally notifications, finish. Add the launcher widget or quick-settings tile through AirMode Settings; manual shade editing is available. Long-pressing the tile opens AirMode. Native hardware noise controls work independently.

Max/headphones show one truthful battery; TWS show L/Case/R where available. Sony native RFCOMM V1/V2 verifies service/version/model/capabilities and recognized response layouts before commands. Off/ANC/Transparency depend on capabilities; Sony Adaptive Sound Control is not mapped to AirPods Adaptive. Close Sound Connect when testing. Unknown formats fail closed.

Unknown charge stays—. Open the case nearby and Refresh battery:4-second windows at least15seconds apart. Live Apple proximity keys are memory-only and enable private-address attribution and recognized encrypted BLE layouts; this remains unqualified on your AirPods5. No RSSI/name attribution. A fresh case BLE reading survives an unavailable bud-link case report. Android cached values remain labelled last-known.

Confirmed device replies alone change mode. Loading stops by1.5seconds, then neutral awaiting-confirmation text until2.5seconds; the user's real reply arrived around1.8seconds. One700ms retry, physical writes at least400ms apart. Late real replies update actual state. Apple Adaptive uses the documented capability declaration once after explicit selection; no Conversation Awareness settings are written. Check unrelated speech/audio behavior on your firmware.

Android12+ (min31/compile36/target36), Pixel10Pro/11Pro stable/beta target matrix, exact-build qualification required. Future betas cannot be guaranteed. Apple ClassicL2CAP access uses a narrow native invocation bridge without root/Xposed/vendor spoofing/global exemptions. Sony uses public RFCOMM. Silent native8s popup is default-on; optional persistent battery is default-off, with Android's required foreground status while connected. Socket closes on disconnect, service stops30seconds later.

Build with free Android Studio SDK36/BuildTools36/JDK17 and the Gradle command above. Unsigned release needs your own key via Android Studio or AIRMODE_* environment variables. Signing secrets stay outside Git; CI unsigned output is not the published signed release.

Diagnostics updates preserve settings. Reconnect, open the case, Refresh, test controls and share the report via Settings → seven version taps. No addresses, names, serials, keys, encrypted payloads, disk/Logcat/network reporting. Normal release strips diagnostics. Popup preview and debug design screenshots are explicitly sample data, never live headphone evidence.

Not affiliated with Apple or Sony. AirPods is a trademark of Apple Inc. Apache-2.0. [Protocol evidence](docs/PROTOCOL.md), [verification](docs/VERIFICATION.md), [device checklist](docs/DEVICE_TEST.md).
