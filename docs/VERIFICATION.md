# Verification / Проверки

Дата: 2026-10-06. Версия 0.1.0, prerelease. **Полная аппаратная готовность PRD не подтверждена**. Позже пользователь сообщил о тесте Pixel10Pro CP41.260831.007.A3 / AirPods5: режимы переключаются, заряд и popup не показываются. Это пользовательское наблюдение, не захват пакетов и не полный чеклист.

## Сборка и APK

- JDK17, Gradle8.13, AGP8.13.2, Kotlin2.2.21; minSdk31, compile/target36.
- `./gradlew testDebugUnitTest lintRelease assembleRelease` с отдельным release-ключом: BUILD SUCCESSFUL.
- 22 unit test: 11 protocol/model parser, 2 battery freshness, 1 state invariant, 8 command timing/cancellation. Failures0/errors0. Protocol fixtures описаны в PROTOCOL, это не захваты физических AirPods4/5.
- Release lint: 0 errors, 11 warnings (7 обновлений версий, 2 неиспользуемых ресурса, 2 рекомендации KTX). Новые Material/BOM требуют compile37; совместимые версии намеренно закреплены.
- APK2271318 bytes; подпись APKv2, RSA3072, apksigner verify PASS.
- APK SHA256: `584209f5fd7c3e1b1517614a866e7d10b1823cb88d3b666d9d3b41bd603a14d8`.
- Certificate SHA256: `21f14d555e532b6d1618595d41983f02868e1891254f0a7251085f833cc47cee`.
- `zipalign -c -P 16 4`: PASS. Обе arm64 native зависимости имеют LOAD alignment0x4000. DataStore1.2.1 и graphics-path1.1.0 закреплены после обнаружения предупреждения у старых транзитивных версий. Свежая установка release на16KB beta: warning отсутствует, `dumpsys package` → `pageSizeCompat=0`, `getconf PAGE_SIZE` →16384. Проверка только RELROend%16384 неверна для полностью покрытого LOAD; [Bionic учитывает этот случай](https://android.googlesource.com/platform/bionic/+/refs/heads/main/linker/linker_phdr.cpp).
- В release нет instrumentation, debug-log экрана или сырых логов протокола. AAPT подтверждает отсутствие INTERNET/location/microphone permissions и наличие только Bluetooth, notifications, connected-device FGS, boot и внутреннего AndroidX signature permission. SCAN neverForLocation, backup отключён.
- Runtime traffic на физическом телефоне не измерен; сетевого кода и INTERNET permission нет.

## Фактически запущенные системы

| Среда | Точная сборка | Что проверено |
|---|---|---|
| Android16/API36 Google APIs arm64 emulator | BE2A.250530.026.F3 /13894323 | Debug native Classic TYPE_L2CAP factory PASS без connect/отправки; signedrelease install/UI и разрешения |
| Android17/API37 beta3 Google APIs ps16k arm64 emulator | CP41.260731.005.B1 /16056512 | Debug native Classic TYPE_L2CAP factory PASS без connect/отправки; свежий signedrelease, 16KB, RU/EN, system dark/light |

Второй образ SDK — `system-images;android-37.2-beta3;google_apis_ps16k;arm64-v8a`. Он **не является** физическим Pixel10Pro/11Pro и не является последним QPR3Beta1. Последние [официальные QPR3Beta1 notes](https://developer.android.com/about/versions/17/qpr3/release-notes) изучены; DP11.260918.005/.006 не тестировались. Целевой Pixel и название Android не доказывают канал управления.

Native factory probe — debug-only instrumentation: создаёт TYPE_L2CAP PSM0x1001 и сразу закрывает без подключения. Он доказывает доступность конструктора на этих двух runtime, но не соединение, handshake, данные или изменение звука. Release включает тот же transport и стандартные consumer rules зависимости; release не содержит probe.

## Интерфейс

- RU по умолчанию. Один экран onboarding; Bluetooth объяснение до Nearby devices. Отказ POST не блокирует продолжение.
- Без наушников три `—`, а не фиктивные проценты; режимы отсутствуют.
- Настройки: после снятия двух чекбоксов оставшиеся два disabled; меньше двух нельзя. Все четыре изначально выбраны.
- Язык EN выбран в Settings, выдержал force-stop/cold launch; возвращаемое название и тексты EN. System night yes изменяет оформление без собственного переключателя темы.
- Реальные screenshots в [screenshots](screenshots/): beta release home RU/light, settings RU, settings EN и home EN/dark. Они показывают состояние Disconnected, не AirPods simulation.
- Final source: commitd53a7ad; APK SHA указан выше. Оба эмулятора обновлены этим APK с сохранением подписи; финальные install/cold main1604ms API36 и1073ms API37beta. Скриншоты Home обновлены. Полная UI/плитка последовательность ниже выполнена на30a3fb9; последнее изменение касается выбора двух физических пар и не было проверено с аппаратурой.
- Android16 final release: повторное native AddTile подтверждение, плитка «Нет AirPods, Unavailable», non-clickable; screenshot android16-release-tile.png. Нет активного AirModeService при Disconnected. Long press фактически открыл MainActivity.
- Android16: Bluetooth denial/retry → Allow, native POST denial → Done → main с тремя —; минимум два режима подтверждён UI и переживает настройки.
- Android16 measured post-update1405ms, ordinary cold1296ms на signed build30a3fb9: эти эмуляторные результаты превышают1s. Pixel8 критерий НЕ пройден/не измерен.
- Android17 first release launch1015ms до onboarding, повторный cold main695ms; это одиночные измерения headless эмулятора, не Pixel8 acceptance<1s.

## Обязательные проверки, которые ещё не выполнены

Все три реальные батареи; модели4/5 и прошивки; acoustic ANC/transparency/adaptive/off; протокольный ACK и отказ; плитка со сменой с ножки≤2s; popup/FGS с реальным connected; выключение/включение Bluetooth; звонок; две пары/переименованная пара; AirPods4 безANC/чужие наушники; boot/background recovery; физический Pixel8 coldstart; Pixel10Pro/11Pro latest stable/beta.

Следующий аппаратный шаг: [DEVICE_TEST](DEVICE_TEST.md). До его завершения релиз остаётся prerelease. Никакой пункт выше не заменён заглушкой или отмечен аппаратным PASS.

Аппаратная приёмка отслеживается в [Issue1](https://github.com/andrewkazavchinskyy-cloud/AirMode/issues/1).

Локальные временные build outputs вынесены из iCloud через Gradle init script: iCloud создал конфликтный generated values-lo 2.xml и блокировал чтение. После смены build directory stale incremental cache дал ошибки внутренних Kotlin symbols; выполнена чистая сборка с kotlin.incremental=false. Source code остаётся в Git; новый checkout CI/Android Studio не содержит этих локальных generated файлов.

GitHub Actions: [30a3fb9 run37508948540](https://github.com/andrewkazavchinskyy-cloud/AirMode/actions/runs/37508948540) SUCCESS (tests/lint/debug/release/artifact upload). Финальный source run [37509494160](https://github.com/andrewkazavchinskyy-cloud/AirMode/actions/runs/37509494160) SUCCESS; скачанный artifact report подтвердил22tests/0failures/0ignored, все build/lint/upload steps SUCCESS. Первый CI failed на удалённом Google SDK tools package; workflow исправлен на platform-tools. Два промежуточных устаревших run отменены, history сохранена.

## GitHub release receipt

[Release v0.1.0](https://github.com/andrewkazavchinskyy-cloud/AirMode/releases/tag/v0.1.0), опубликован (не draft), prerelease. Target2f033b5, кодd53a7ad. APK asset616287349 stateuploaded,2271318bytes; GitHub digest совпал с локальным SHA выше. APK и SHA256SUMS скачаны обратно через авторизованный gh; SHA256 validation PASS. Приватный репозиторий по стандарту пользователя; нужен доступ GitHub для загрузки, приложению аккаунт не нужен.

##0.1.1 исправление по пользовательскому тесту
Пользователь сообщил: Pixel10Pro CP41.260831.007.A3 / AirPods5 переключает режимы в0.1.0; батарея/popup не показываются. Телефон отдельно, USB недоступен; пользователь запросил диагностический APK. Это частичное аппаратное наблюдение, без захвата пакетов.0.1.1 отправляет оба документированных notification masks; строгий parser сохранён. Исправление батареи пока НЕ подтверждено на его устройстве.

Новый silent HIGH channel создаёт нативный heads-up с тремя колонками,8s абсолютным timeout и уважением ручного dismissal. На Android17beta16KB эмуляторе: диагностика подписана тем же ключом, `adb install -r` поверх0.1.0 SUCCESS с сохранением EN/settings;7 version taps открыли отчёт. Test popup → объяснение → системное POST Allow → фактический heads-up с явно тестовым заголовком и80/54/76. Screenshot показывает демонстрационное notification, не реальные AirPods. Главный экран/Repository остаются Disconnected/—. Проверка не является тестом charge protocol.

CI62ff242 (язык плитки/политика BLE до battery fix) [37511210924](https://github.com/andrewkazavchinskyy-cloud/AirMode/actions/runs/37511210924) SUCCESS. Новый источник battery fix проверяется отдельно.

Финальная локальная0.1.1: `testDebugUnitTest lintRelease assembleDebug assembleRelease` BUILD SUCCESSFUL.23tests/0failures/0errors, release lint0errors/20warnings (dependency updates, layout suggestions, small status labels, unused resources/KTX). Оба APK apksigner verify/16KBzipalign PASS, одинаковый certificate21f14d555e532b6d1618595d41983f02868e1891254f0a7251085f833cc47cee. Debug33789766bytes SHA25610685c6322456da9b71241177a3713f0df7708792b03f63786ee912c99cb47b1; release2276062bytes SHA256bc1ffc7645235dda97f2e5f6147237ad28f7c5f015d1fe8951a804c59921a810. Release dex не содержит ProtocolDiagnostics или текста диагностического отчёта; оба manifest безINTERNET/location/microphone. Android16 signedrelease update SUCCESS, launch1052ms; активная cached QS tile теперь EN `No AirPods`, state0. Debug17beta update SUCCESS; без реальных AirPods главный экран—.

Финальный diagnostic popup: screenshot [android17-diagnostics-popup.png](screenshots/android17-diagnostics-popup.png); native NotificationRecord ID3 имеет importance4, sound/vibrate=null, timeoutPT8S, custom headsUp/big views. После фактически выжданных9.2s active ID3 отсутствует. Повторный тестовый popup работает; статический отчёт после теста обновляет разрешение/channel4. Это строго демонстрационный preview.

Финальный обычный release0.1.1 обновил diagnostic на Android17beta16KB без удаления настроек; install SUCCESS, launch/main SUCCESS с тремя—.

##0.1.1 release receipt
[Release v0.1.1](https://github.com/andrewkazavchinskyy-cloud/AirMode/releases/tag/v0.1.1) опубликован, non-draft prerelease, source/targetf1a70372e0687a98ccaa82405fb695a6e93fd585. Normal APK asset616336363 и diagnostic asset616336364 uploaded; GitHub SHA256 digest совпадает с локальными значениями выше. Оба APK иSHA256SUMS скачаны обратно через gh; обе checksum PASS.0.1.0 сохранён без замены/удаления. Новый source CI [37512740790](https://github.com/andrewkazavchinskyy-cloud/AirMode/actions/runs/37512740790) впоследствии SUCCESS; tests/lint/debug/release/upload steps SUCCESS. Скачан artifact11435906026 (`APK-and-checks`,12982863bytes, digest790e352f0edd6342b53d2887adc4d199e6f4f7bb9f6c264207d222f7e42048d1); HTML report подтвердил23tests/0failures/0ignored. CI release unsigned и не заменяет опубликованный подписанный APK. Локальные обязательные checks PASS. Следующий шаг — пользовательский retest заряда/popup или sanitized diagnostic report; аппаратный charge PASS не заявлен.

## 0.1.2 — реальные пакеты, виджет и Adaptive

Пользователь передал санитарный отчёт0.1.1 с физического Pixel10Pro / SDK37 / CP41.260831.007.A3: SessionReady, A3531/A3532, входящие режимы1/2/3, принятые22-byte AAP battery сообщения. Зафиксированы Л48%, П61%, кейс100%, затем case status4 с00/FF.100% был действительно в пакете; последующее пользовательское наблюдение50% отсутствует в этом захвате и не считается независимо подтверждённым. Это частичная аппаратная проверка, не весь чеклист и не доказательство точности датчика.

Исправлены явная недоступность компонентов и независимое время кэша Android, приоритет присутствующего AAP, показ—/последнего значения, начало транспорта только после фактического foreground ownership. На явный запрос Adaptive отправляется одна документированная capability declaration на живую подтверждённую ANC-сессию; никакой CA-setting command.0x002b не подтверждает режим; только входящий mode4. Отдельный debug-отчёт содержит времена TX/RX, источники, metadata cache и ear states, без личных идентификаторов. Аппаратные Adaptive и возможные побочные эффекты маски ещё НЕ проверены.

`testDebugUnitTest lintRelease assembleDebug assembleRelease`: BUILD SUCCESSFUL.30tests,0failures,0errors; lint0errors/28warnings (закреплённые зависимости, рекомендации layout/KTX, старые мелкие подписи popup). Новые regression fixtures включают настоящие пакеты48/61/100 и недоступный кейс, порядок компонентов, приоритет/возраст источников, ear-state границы; старые8 timing/cancellation checks сохранены. Первый lint выявил3 отсутствующих RU widget translations; они исправлены, baseline/подавление ошибок не использовались.

Оба APK apksigner verify и16KB zipalign PASS, тот же certificate21f14d555e532b6d1618595d41983f02868e1891254f0a7251085f833cc47cee. Release dex не содержит ProtocolDiagnostics, текста отчёта или metadata debug cache. Манифест по AAPT: только Bluetooth CONNECT/SCAN neverForLocation, POST, connected-device FGS, BOOT и внутренний AndroidX permission; INTERNET/location/microphone отсутствуют.

Android17beta16KB: обновление diagnostic поверх0.1.1 SUCCESS, настройки EN сохранены. Native requestPinAppWidget вызвал системный диалог; «Add to home screen» создал настоящий launcher widget id3, updatePeriodMillis0. После `am kill` PID отсутствовал; нажатие Adaptive на сохранённом виджете создало новый процесс и FGS (startForegroundCount1). Без пары остались—/NoAirPods, debug report RX/TX отсутствуют; FGS снимается после5s discovery и служба позднее остановилась. Это проверяет холодный запуск, не отправку/ACK на наушниках. Смена языка наRU сразу обновила виджет; все4кнопки и колонки помещаются при font_scale1.3. [Скриншот виджета](screenshots/android17-012-widget-ru-font13.png) показывает Disconnected, не симуляцию AirPods.

Финальный diagnostic test popup фактически отрисован системой с явно тестовым заголовком и80/54/76: [скриншот](screenshots/android17-012-diagnostics-popup-ru.png). NotificationRecord ID3 importance4, sound/vibrate=null, timeoutPT8S. Позже ID3 отсутствует. Главный экран/Repository не получают демонстрационные данные. На одном раннем снимке карточка ещё была в системной анимации; не считать её доказательством полной отрисовки. Реальная задержка connected→первый пакет→popup измеряется только на телефоне по новым временным отметкам.

Android16 signed normal update SUCCESS, cold start1181ms. Последующая установка normal на Android17 поверхdiagnostics SUCCESS. Android17 normal cold start1370ms, page size16384; главный экранRU/—: [скриншот](screenshots/android17-012-release-home-ru.png). Данные об эмуляторном запуске не проходят критерий Pixel8<1s. Полная физическая матрица, независимое сравнение заряда, режим4, stem/tile/widget sync, звонок/перезагрузка остаются открыты. Прежние релизы и Git-история сохранены.

Последняя проверка также обнаружила первый холодный metadata read без достоверного возраста: такие данные теперь всегда отдельно помечены как последние, до живого AAP/атрибутированной рекламы. Чистая функция cache policy проверяет первую запись, повторное чтение без обновления времени, изменение только последнего cached значения и приоритет live-протокола. Дополнительная финальная сборка проверяет именно эту правку.

Быстрый второй выбор после раннего ACK теперь принимается, а фактическая запись ожидает400ms в существующем mutex Session. Ранее второй выбор мог молча игнорироваться при уже активных кнопках. Дополнительный timing regression проверяет ожидание native rate limit и подтверждение без лишнего повтора. Финальный набор содержит32теста; результат окончательной сборки ниже фиксируется после завершения.

Финальные исходники0.1.2 после обеих последних правок: BUILD SUCCESSFUL,32tests/0failures/0errors, lint0errors/28warnings, signeddebug и release собраны. Временные ранние APK были заменены локально до публикации; их checksum не является checksum релиза. Окончательные файлы и GitHub receipt фиксируются после привязки к source commit и скачивания обратно.
