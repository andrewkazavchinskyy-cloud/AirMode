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

## 0.1.2 release receipt

[Release v0.1.2](https://github.com/andrewkazavchinskyy-cloud/AirMode/releases/tag/v0.1.2) опубликован, non-draft prerelease. Source и tag target: `98f351d3c6607fc01bddb26c2622143722c0d3d0`. Окончательная перепаковка после commit успешна; normal APK содержит этот source SHA в version-control metadata.

| Подписанный файл | Размер, bytes | SHA256 | GitHub asset ID |
|---|---:|---|---:|
| AirMode-0.1.2.apk | 2304290 | `31ad2ec689142239e16bc81a96609c38f9299c4783ff5a9155e1076d24ffc49c` | 616438465 |
| AirMode-0.1.2-diagnostics.apk | 33830714 | `9cd70cfb53a901553d34782b5d0ab763419da6214c5f59aa0b7ae1657c48b1dc` | 616438461 |

Оба asset имеют state uploaded. Оба APK и SHA256SUMS скачаны обратно с GitHub; `shasum -a256 -c SHA256SUMS` подтвердил оба файла. SHA256SUMS asset616438463. Окончательные APK подписаны прежним certificate21f14d555e532b6d1618595d41983f02868e1891254f0a7251085f833cc47cee; apksigner verify и16KB zipalign PASS. Normal dex не содержит ProtocolDiagnostics или диагностических текстов. Старые0.1.0/0.1.1 releases/assets/tags сохранены.

Тот же окончательный normal APK повторно установлен через `adb install -r` на API36 и API37beta: обе установки SUCCESS, оба MainActivity start Status ok. API36 cold TotalTime1378ms; второй API37 запуск обозначен системой WARM, TotalTime1143ms. Последнее значение не является cold-start измерением и не заменяет критерий физического Pixel8.

Source [CI37518426577](https://github.com/andrewkazavchinskyy-cloud/AirMode/actions/runs/37518426577) terminal SUCCESS; tests/lint/debug/release/upload и cleanup steps SUCCESS. Скачан artifact11438811248 (`APK-and-checks`,13028572bytes, digest`beee636704e9a0eb442fb2dedb195fbe78c547f06920a02cdf65fa59f0969c8b`); его HTML report подтверждает32tests/0failures/0ignored. CI release unsigned; опубликованные APK выше подписаны локально и отдельно проверены.

[Issue1](https://github.com/andrewkazavchinskyy-cloud/AirMode/issues/1) сохраняется открытым: обновлены реальные частичные наблюдения пользователя и retest0.1.2. Аппаратная работа Adaptive, независимая точность батареи, реальная задержка popup и полная PRD-матрица ещё не подтверждены.

## User0.1.2 report and0.2.0 scope,2026-10-07

Physical user evidence, not local USB: Pixel10Pro SDK37 CP41.260831.007.A3, AirPods5 A3532. SessionReady/problemnull; live L17/R31, case unavailable with status4/00; Android metadata unavailable,110Apple advertisements unmatched. Incoming mode4 at207570192 after TX207568385:1807ms. Other replies include1647/1671/1650ms Off and1372ms Transparency. These are actual received mode reports, not acoustic tests of every combination. User says other functionality good. Popup at207551693 after selected207551224:469ms in this capture, not a latency guarantee.

0.2.0 addresses premature1500ms timeout with loading≤1500ms and neutral pending confirmation to2500ms, retains confirmed selection. Adds original memory-only Apple proximity decoding for recognized case layouts; AirPods5 keys/layout not yet physically observed. Pair-epoch scan guard fixes invalidation when control session opens. Fresh case advertisements survive bud-link-unavailable reports; cached metadata cannot refresh/withdraw live values.

Expanded original code recognizes official AirPods1–5/Pro1–3/Max1–2 with per-model modes and single Max charge; native Sony MDR V1/V2 is experimental, capability/schema gated. No Sony model hardware-qualified; Beats/wired EarPods unimplemented. Actual debug design fixtures are explicitly labelled sample data, not Repository/BT or device evidence. Full physical acceptance and exact latest Pixel10/11 stable/beta matrix remain open.

Local combined0.2.0 checks after Sony retry/busy and native widget negotiation fixes:63tests/0failures/0errors/0ignored; release lint0errors/0fatal/34warnings; debug/release assemble SUCCESS. Expected protocol AES/ECB lint warning remains: the wire block uses ECB, and no authenticated-integrity claim is made. No new dependencies added. Debug-only labelled design probe uses native instrumentation and never writes fixture state to Repository.

Actual Android17 emulator CP41.260731.005.B1/API37/16KB, not user's physical build: in-place diagnostics updateSUCCESS/settings preserved; real disconnected app shows three unknown values. Real existing launcher widget updated to new card/buttons; cold mode tap after am kill starts connectedDevice FGS and keeps unknown/disconnected data, no successful headphone command claimed. Explicit sample-only snapshots cover AirPods5 three components/unknown case, neutral pending state, Sony WH single76%/three modes, Max1 single76%/three modes. Night mode/font1.3 labels/buttons fit; fixture banner is visible. Screenshot evidence in docs/screenshots/android17-020-*.png. Sony and Max fixtures are UI evidence only, not paired hardware.

## Published signed0.2.0 receipt

Source/tag targetc2f6f8a94ed17f84d5068824851205a7668d5d8a; exact committed source combined checks repeatSUCCESS:63tests/0failures/0errors/0ignored, release lint0errors/0fatal/34warnings, both assemblies. Normal APK version-control metadata contains this SHA, packageapp.airmode/versionCode4/min31/compile36/target36. Normal dex strips ProtocolDiagnostics, design/transport probes, RX report and new proximity/BLE diagnostic text. No INTERNET/location/microphone/overlay permission. Both apksigner verify and16KB zipalign PASS, same persistent cert21f14d555e532b6d1618595d41983f02868e1891254f0a7251085f833cc47cee.

[Releasev0.2.0](https://github.com/andrewkazavchinskyy-cloud/AirMode/releases/tag/v0.2.0) is non-draft/prerelease. Annotated tag remote objecta7e1d42306df0fc0c4a9db80ebb4215e5c0a94d2 dereferencesc2f6f8a94ed17f84d5068824851205a7668d5d8a; release metadata targetCommitishmain does not replace that fixed tag.

| File | Bytes | SHA256 | Asset ID |
|---|---:|---|---:|
| AirMode-0.2.0.apk |2360046|42b92732e9e01914f31cd8acdc79f97b9f026252407166af1e66a60e892883df|616684858|
| AirMode-0.2.0-diagnostics.apk |33584152|033d9838e81b8901cb62e2866c8d4a7e00fe6d1fb27873f42bf9e19d75d435be|616684862|

SHA256SUMS asset616684863. Both stateuploaded. Downloaded both APKs and checksum file back from GitHub; both SHA256 checksPASS. Exact final normal APK updates/install/startSUCCESS on API36 and API37beta16KB, settings preserved; API36 COLD TotalTime1095ms, API37 COLD759ms. These emulator values do not qualify physical Pixel8 coldstart criterion. Normal pm list instrumentation is empty. RU/night/font1.3 and EN normal interfaces show honest disconnected data; native widget also fits dark/font1.3. Added final release screenshots and noANC sample-only layout proof.

[Issue1](https://github.com/andrewkazavchinskyy-cloud/AirMode/issues/1) remainsOPEN, updated with latest partial actual evidence and0.2.0 Apple/Sony/case retest, retaining previous evidence/checklist. Older releases/assets/tags/commits preserved. Exact source [CI37532725646](https://github.com/andrewkazavchinskyy-cloud/AirMode/actions/runs/37532725646) was still running at this receipt; terminal/artifact evidence follows when received.

Exact-source [CI37532725646](https://github.com/andrewkazavchinskyy-cloud/AirMode/actions/runs/37532725646) now terminalSUCCESS, headc2f6f8a94ed17f84d5068824851205a7668d5d8a; tests/lint/debug/release/upload/cleanup stepsSUCCESS. Downloaded artifact11444464184 APK-and-checks,13120439bytes, recorded digest88527fe7ef85f67cb75b6b278896f9d858c9dfe7066e8a23fb163f5c6674bab4. Its HTML report confirms63tests/0failures/0ignored. CI app-release-unsigned.apk is not the separately signed published APK. Hardware case truth, expanded Apple/Sony interoperability and exact latest Pixel10/11 stable/beta remain unverified.

## Stable public release authorization and privacy audit, 2026-10-07

The user reports good AirPods operation and Sony battery display, and explicitly asks for a stable release, public repository and documentation. The latest report is user-observed, not a new USB capture. Sony model/firmware is unspecified; Sony mode switching and independent case accuracy are not newly confirmed. Existing exact Pixel10Pro/AirPods5 evidence above remains the hardware reference. The full matrix remains open in Issue1.

Before public visibility, read-only audit covered all17 reachable commits /218 unique blobs (192text,25PNG,1Gradle wrapper JAR), commit/tag/ref messages, and Gitleaks default rules with full redaction: zero leaks. Additional review classified public SIG vectors, synthetic test keys/MACs and documented wire constants; no real identity/signing credentials found. All25 historical/current PNG blobs were visually reviewed, including full-resolution diagnostic/tile views; no private account/contact/serial/MAC/location metadata. All44 historical Markdown blobs had public technical/project links only.

GitHub surface audit covered1Issue/0comments,0PRs/reviews,4release bodies and all11assets (7APKs/4checksums),12historical Actions logs (attempt1), all6retained artifacts including12CI APKs and reports. Release checksums all match; no unmasked credentials, real Bluetooth identifiers, private paths, signing files or captured private diagnostic reports found. Masked checkout auth and literal GITHUB_TOKEN setup references are not credential exposure. The sole debug socket-probe address is a known synthetic unconnected test. No Git history/ref/asset deletion or rewrite.

Publication changes docs/distribution metadata only. Existing signedv0.2.0/sourcec2f6f8a/tag/certificate/assets remain unchanged. RU/EN static GitHub Pages documentation uses no runtime scripts, external fonts, framework or analytics; JSON-LD is descriptive data. Local HTML/schema/canonical/hreflang/assets/links/sitemap checks pass; browser inspection confirms desktop/mobile layout and image loading. No claim of Google indexing/ranking is made. Public endpoint and deployment receipts follow after publication.

## Public stable / Pages receipts

Documentation commitc47c961ef96b5677ea82e12bdf95d13758361e75 pushed to main. Final pre-publication Gitleaks scans all18reachable commits /536.62KB: zero findings. GitHub repository confirmed public (`isPrivate=false`); public About description,14topics and homepage now reference https://andrewkazavchinskyy-cloud.github.io/AirMode/ .

Existingv0.2.0 promoted in place to non-draft/non-prerelease Latest. Anonymous HTTPS API requests with no authorization confirm public repo and `/releases/latest` tagv0.2.0. Anonymous normal APK downloaded:2360046bytes, SHA25642b92732e9e01914f31cd8acdc79f97b9f026252407166af1e66a60e892883df; apksigner verifies unchanged SHA256 certificate21f14d555e532b6d1618595d41983f02868e1891254f0a7251085f833cc47cee;16KB zipalignPASS. APK asset IDs616684858/616684862 and SHA256SUMS616684863 unchanged. Anonymous checksum-file digest also unchanged. Existing0.2.0 users do not need reinstall; no rebuild/re-upload/new signing key.

Native GitHub Pages: legacy branchmain/path/docs,HTTPS enforced. Initial deployment statusbuilt, commitc47c961, duration17857ms, no error. RU https://andrewkazavchinskyy-cloud.github.io/AirMode/ and EN https://andrewkazavchinskyy-cloud.github.io/AirMode/en/ anonymously return200text/html, byte-identical to committed pages. Sitemap returns200, valid XML/two canonicalURLs. No X-Robots-Tag noindex; page metaindex/follow, selfcanonical and three hreflang alternatives. Domain-root robots.txt returns404, not a crawl disallow; no project-path robots.txt is presented as authoritative domain policy. No Search Console submission or actual Google index/rank confirmation.

Real browser checks: publicRU/EN render, correct titles/languages/canonicals, loaded sample-labelled image, responsive390x844 without horizontal overflow (document375/viewport390), nativeFAQ disclosure expands. Desktop default viewport also inspected; temporary mobile override reset. No UI claim inferred from source alone. Local static metadata/schema/assets/links/sitemap checks pass. Documentation update does not change app code, so63-test/sourceCI hardware-evidence boundary remains unchanged.

Issue1 retitled ongoing Apple/Sony/exact-Pixel compatibility; stable publication no longer gated by it, but complete hardware matrix remainsOPEN. Earlier evidence/checklists retained; misleading0.1.2-over0.2 instructions marked archived. All earlier commits/tags/releases/assets preserved.
