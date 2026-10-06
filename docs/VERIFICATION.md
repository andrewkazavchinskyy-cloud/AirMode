# Verification / Проверки

Дата: 2026-10-06. Версия 0.1.0, prerelease. **Аппаратная готовность PRD не подтверждена**: пользователь сообщил, что физического Pixel/AirPods сейчас нет.

## Сборка и APK

- JDK17, Gradle8.13, AGP8.13.2, Kotlin2.2.21; minSdk31, compile/target36.
- `./gradlew testDebugUnitTest lintRelease assembleRelease` с отдельным release-ключом: BUILD SUCCESSFUL.
- 21 unit test: 11 protocol/model parser, 2 battery freshness, 1 state invariant, 7 command timing/cancellation. Failures0/errors0. Protocol fixtures описаны в PROTOCOL, это не захваты физических AirPods4/5.
- Release lint: 0 errors, 11 warnings (7 обновлений версий, 2 неиспользуемых ресурса, 2 рекомендации KTX). Новые Material/BOM требуют compile37; совместимые версии намеренно закреплены.
- APK2271318 bytes; подпись APKv2, RSA3072, apksigner verify PASS.
- APK SHA256: `488cefee2abaa268f7d42d1ec9f7c1ada14b6098bd7297a79a6b38e857cfeec7`.
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
- Android17 first release launch1015ms до onboarding, повторный cold main695ms; это одиночные измерения headless эмулятора, не Pixel8 acceptance<1s.

## Обязательные проверки, которые ещё не выполнены

Все три реальные батареи; модели4/5 и прошивки; acoustic ANC/transparency/adaptive/off; протокольный ACK и отказ; плитка со сменой с ножки≤2s; popup/FGS с реальным connected; выключение/включение Bluetooth; звонок; две пары/переименованная пара; AirPods4 безANC/чужие наушники; boot/background recovery; физический Pixel8 coldstart; Pixel10Pro/11Pro latest stable/beta.

Следующий аппаратный шаг: [DEVICE_TEST](DEVICE_TEST.md). До его завершения релиз остаётся prerelease. Никакой пункт выше не заменён заглушкой или отмечен аппаратным PASS.
