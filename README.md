# 차섬 — 오늘 머물 시간을 보여 주는 작은 섬

![tests](https://img.shields.io/badge/unit%20tests-141%20passing-2ea44f) ![runtime permissions](https://img.shields.io/badge/runtime%20permission%20prompts-0-blue) ![vehicle control](https://img.shields.io/badge/vehicle%20control-none%20(read%20only)-blue) ![apk](https://img.shields.io/badge/release%20APK-4.0%20MB-informational)

| 히터를 올리면 해가 커지고 남은 시간이 줄어듦 | 내일 몫 아래로 내려가면 경고 | 운전석 문을 열면 떠나기 전 영수증 |
|---|---|---|
| ![섬과 토스트](docs/images/dial_toast.png) | ![내일 몫 경고](docs/images/keep_k2.png) | ![영수증](docs/images/receipt.png) |

## 1. 앱 이름 및 한 줄 소개
- **앱 이름**: 차섬 (Chaseom)
- **한 줄 소개**: 전기차를 세워 두고 공조를 켠 채 머물 때, 내일 탈 거리만큼 배터리를 남긴 기준으로 **지금 설정으로 얼마나 더 머물 수 있는지(추정)**를 보여 주는 Pleos 차량 앱
- **핵심 기능**
  - **작은 날씨 섬**: 해 크기 = 공조 설정온도, 바람 = 풍량, 물 높이 = 배터리, 부표 1개 = 머물 수 있는 30분(최대 6개), 배의 짐 = 내일 몫. 그림 아래 범례와 캡션 "섬은 공조 설정·배터리로 그려요 · 실내 측정 아님"이 늘 보입니다. 기어 P에서만 표시합니다.
  - **따뜻함 환율**: 다이얼을 바꾸는 순간 토스트로 남은 시간 변화(예: "설정 26°C · 풍량 6 · −21분 (추정)")를 보여 주고, 비슷한 체감을 더 적은 전력으로 얻는 조합(운전석 열선, 저온 화상 주의)을 제안합니다.
  - **내일 지킬 몫**: 내일 처음 갈 거리(여유분 20% 포함)를 배터리에서 따로 표시합니다. 남은 시간은 "(추정)", 3시간 넘으면 "3시간 이상"입니다.
  - **맴도는 구름·먼 비**: 공조를 켠 채 창문을 모두 닫고 내기순환을 20분 넘게 쓰면 구름 하나가 뜹니다(공기 측정 아님). 앞 와이퍼가 AUTO로 실제로 닦을 때만 섬 뒤에 먼 비가 보입니다(바깥 날씨 측정 아님).
  - **안전 고지와 떠나기 전 영수증**: P 진입·1시간·야간에 공조 사용 경고. 운전석 문을 열면 머문 시간·쓴 전력·내일 몫·창문·실내등·충전 포트와 "뒷좌석 직접 확인" 줄을 보여 줍니다. 앱은 공조를 끄지 않습니다.

## 2. 실행 방법
1. Pleos Connect Emulator(아래 3번 환경)를 Cold Boot로 켜고, Virtual Console에서 배터리를 **19%**로 둡니다(약 20% 이상이면 남은 시간이 "3시간 이상"으로 묶여 변화가 보이지 않습니다).
2. APK를 설치하고 앱 목록에서 **차섬**을 실행합니다. 처음 실행하면 내일 탈 거리 설정 화면이 나옵니다.
   ```bash
   adb install -r apk/Chaseom_Chaseom_release.apk
   ```
3. Virtual Console에서 조작합니다.
   - 기어 **P** → 머무는 화면과 섬
   - 운전석 설정온도 22→26°C, 풍량 3→6 → 토스트 "설정 26°C · 풍량 6 · −21분 (추정)". 비슷한 체감 카드대로 설정 21°C + 운전석 열선 2단 → 약 +16분 (추정)
   - 내기순환·외기 전환 → 토스트에는 "내기순환"·"외기유입"만 나오고 분은 붙지 않음(남은 시간은 큰 숫자에 바로 반영)
   - 앞 와이퍼 AUTO → 섬 뒤 먼 비 (Low/High는 그리지 않음)
   - 운전석 문 열기 → 떠나기 전 영수증 (문을 닫으면 닫힘)
   - 기어 **D** → 섬과 숫자를 바로 접고, 주행 중에는 시스템이 앱 화면을 가림
4. 또는 자동으로: `python tools/demo.py`. Virtual Console 조작을 재생하고 단계마다 화면 문구와 반영 시간을 출력한 뒤 `6/6 장면 확인`을 냅니다(영수증 단계에서 1분쯤 기다림). release APK에는 데모 모드나 가짜 값이 없습니다(가짜 데이터는 차량 서비스가 없을 때 쓰는 디버그 빌드에만 있음). 도구 설명은 [tools/README.md](tools/README.md)에 있습니다.

**소스에서 빌드**: `ANDROID_HOME` 또는 `local.properties`의 `sdk.dir`이 필요합니다. `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`. 릴리스 APK는 `keystore.properties`(예시 `keystore.properties.example`)를 준비한 뒤 `scripts/build-release.ps1`로 만듭니다. Windows에서 경로에 한글이 있으면 AGP가 빌드를 거부하므로 영문 경로에 클론하거나 이 스크립트(자동 `subst`)를 쓰세요.

## 3. 사용 기술·SDK와 테스트 환경
- **SDK/API**: `ai.pleos.playground:Vehicle:2.3.2` (EvBattery, HVAC, Seat, Door, Window, Light, Wiper), AAOS `CarPropertyManager` (연료 종류, 배터리 용량, 충전구, 기어 `GEAR_SELECTION`). 공개 문서에 있는 API만 씁니다.
- **구조** ([그림](docs/images/architecture.svg)): 어댑터(`cabin/`, `vehicle/`)만 SDK 타입을 알고, SDK 오류는 `null`(= 모름)로 넘깁니다. 도메인(`stay/`)은 Android 없는 순수 Kotlin이라 안전 규칙과 숫자를 JVM 단위 테스트로 검사합니다. 화면(`ui/`)은 Jetpack Compose와 Pleos 디자인 토큰을 씁니다.

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect Emulator **v2.1.0** (`system-images;android-34;pleos_car_emulator;x86_64`), 2560×1440, Automotive, API 34, RAM 4GB, Cold Boot |
| 시스템 이미지 사이트 (Windows) | `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml` |
| 빌드 | Android Studio (JBR), Gradle 9.9.0, AGP 9.5.1, Kotlin 2.4.21, compileSdk 37, minSdk 28, targetSdk 34 |
| 패키지 | `app.chaseom` (디버그 `app.chaseom.debug`) |
| 권한 (런타임 권한 요청 0, 차량 제어 권한 0) | Pleos `CAR_ENERGY`, `CAR_CLIMATE`, `CAR_SEATS`, `CAR_WINDOWS`, `CAR_DOORS`, `CAR_LIGHTS` / AAOS `CAR_INFO`, `CAR_ENERGY_PORTS`, `CAR_POWERTRAIN` / `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` |
| SDK·라이브러리가 빌드 때 자동으로 넣는 권한 | `ai.pleos.playground.service.vehicle.VEHICLE_BINDING` (Pleos Vehicle SDK), `app.chaseom.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (AndroidX, 앱 자신의 권한) |

## 4. APK 위치
- **`apk/Chaseom_Chaseom_release.apk`** (release 빌드, 서명됨, sha256 `ca0269fe854dacf86a230be9e7250e2c7ed12fe8d0616b651bfd400768b60783`)
- 시연 영상: [video/차섬_시연영상.mp4](video/차섬_시연영상.mp4)

## 검증 표
위 APK를 Pleos Connect Emulator 2.1.0에서 잰 값입니다. 원자료는 [docs/evidence/measure_release.json](docs/evidence/measure_release.json), 다시 재려면 `python tools/measure.py apk/Chaseom_Chaseom_release.apk out.json`, 해시 대조는 `python tools/check_hash.py`입니다.

| 항목 | 값 |
|---|---|
| 단위 테스트 | **141개 통과** (`./gradlew testDebugUnitTest`, 에뮬레이터 없이 1분) |
| 테스트 커버리지 (Kover, 라인) | 도메인 **98.4%** (`stay/`의 엔진·계수·섬, Android 접착층 제외) · 전체 28.8%. 원자료 [docs/evidence/coverage.xml](docs/evidence/coverage.xml) |
| 다이얼 → 토스트가 화면에 뜨기까지 (10회) | 중앙값 **33 ms**, 최대 85 ms |
| 기어 P → D, 섬과 숫자가 사라지기까지 (10회) | 최대 **60 ms** (중앙값 19 ms) |
| 다이얼 → 섬 그림이 바뀌기까지 (10회) | 중앙값 **42 ms**, 최대 143 ms (첫 회만 143, 나머지 27–63 ms) |
| 앱 시작 (`am start -W`, 콜드 스타트) | Pleos 기준 10초 이내. 이번 측정 1.03–1.54초(에뮬레이터를 다시 켠 뒤), 오래 켜 둔 에뮬레이터에서는 1.6–2.3초, 새로 설치한 뒤 첫 실행 한 번 5.2초 |
| 정지 화면 CPU (30초) | 평균 **0.25%** |
| 10초마다 다이얼 1회, 60초 평균 CPU | 평균 1.9% (전환 순간 최대 31%) |
| 메모리 (PSS) | 60 MB |
| 런타임 권한 팝업 | **0** (요청하는 권한은 모두 normal) |
| 차량 제어 | **0** (앱은 차를 읽기만 하고, 공조를 끄거나 바꾸지 않음) |

CPU는 코어 1개 기준(에뮬레이터 4코어)입니다. 시간은 Virtual Console에 값을 쓴 시각부터 앱이 남긴 로그 시각까지이며, 시계 차 보정으로 약 ±20 ms 오차가 있습니다. 이 시간 로그(`PROBE`)는 평소 꺼져 있고 측정 도구가 `adb shell setprop log.tag.PROBE D`로 켭니다.

## 안전 규칙 = 테스트
아래 규칙을 어기는 코드나 문구를 넣으면 단위 테스트가 실패합니다. 문구 테스트는 화면 문자열 파일을 직접 읽어 검사합니다.

| 지키는 규칙 | 실패하는 테스트 |
|---|---|
| 뒷좌석을 "비었음"이나 초록으로 표시하지 않고, 영수증에는 늘 "뒷좌석 직접 확인" 줄이 있다 | `WordingRulesTest.rearSeatWording`, `StayEngineTest.rearRowNeverHasADotForAnyOccupancy`, `StayEngineTest.receiptRowsOrderAndRearAlwaysCheck` |
| 남은 시간은 3시간 위로 늘지 않고, 토스트·환율 카드도 3시간으로 자른 값끼리 비교한다 | `StayEngineTest.toastDeltaUsesThreeHourCappedValues`, `StayEngineTest.toastDeltaCrossingTheCapIsBounded`, `StayEngineTest.noExchangeCardWhenAboveCap` |
| 계산된 숫자에는 늘 "(추정)"이, 토스트와 해 옆 숫자의 온도 앞에는 늘 "설정"이 붙는다 | `WordingRulesTest.computedValuesMarkedEstimate`, `WordingRulesTest.roundOneIslandSaysNothingAboutSeatHeat` |
| 수면·차박·보장·"최초" 같은 금칙어가 없다 (취급설명서 경고 인용만 예외) | `WordingRulesTest.noBannedWordsOutsideTheWarnings`, `WordingRulesTest.designRuleWordsAbsent`, `WordingRulesTest.manualQuoteVerbatimWithSource` |
| 주행 중 화면은 숫자 없이 짧은 안내뿐이다 | `WordingRulesTest.drivingGlanceShortAndNumberFree` |
| 앱은 공조를 끄지 않는다고 말하고, 내일 몫은 "잠금"이 아니다 | `WordingRulesTest.keepShareNeverLockedAndAppDoesNotControlClimate` |
| 내일 몫 경고(K1·K2)는 공조를 끄거나 줄이라고 하지 않는다. K1은 "계속 켜 두면 내일 몫을 쓰게 돼요", K2는 "충전하거나 출발을 고려하세요"라고 말한다 | `WordingRulesTest.k1AndK2NeverTellThePersonToCutTheClimate` |
| 내기순환·외기 전환 토스트는 두 방향 모두 분을 붙이지 않는다(다른 변경과 묶여도). 남은 시간은 큰 숫자에 바로 반영 | `StayEngineTest.airSourceSwitchToastHasNoMinutesButTheHeroMoves`, `WordingRulesTest.airSourceSwitchToastShowsNoMinutes` |
| 머무는 동안 60분마다, 야간에는 시작과 매시 고지가 다시 뜬다. 고지가 떠 있으면 섬은 멈춘다 | `StayEngineTest.hourNoticeEvery60Minutes`, `StayEngineTest.nightNoticeAtStartAndHourly`, `StayEngineTest.nightBeginningDuringStayShowsNightCard`, `IslandTest.frozenUnderNoticeOrReceipt` |
| 섬은 2열 탑승·문에 반응하지 않고, 창문은 구름을 띄울지 정할 때만 쓴다. 섬은 '공조 설정·배터리로 그려요 · 실내 측정 아님'이라고 말하고, 밤에도 달·별·모닥불을 그리지 않는다 | `IslandTest.rearSeatWindowsAndDoorsAreNotInputs`, `WordingRulesTest.islandWordingDaytimeAndDecoration` |
| 구름은 내기순환 20분(창문 닫힘) 뒤에만 하나로 뜨고, 창문을 열면 조용히, 외기로 바꾸면 한 번 흘러 나가며 사라진다(보상 표시·문구 없음) | `StayEngineTest.recircCloudAfter20MinIgnoresSnoozeAndLeavesQuietly`, `StayEngineTest.recircCloudNeedsClimateOnAndKnownClosedWindows`, `IslandInfoTest.weatherFollowsClimateAndOffIsTheSmallSun` |
| 배는 배터리가 줄어도 기울거나 가라앉지 않고, 정박은 차가 다녀왔을 때만 한 번 움직인다 | `IslandTest.belowKeepEmptiesTheDayBandButNeverChangesTheBoatOrSun`, `IslandTest.crateOutlineOnlyNearOrBelowKeep`, `IslandTest.mooringOnlyWhenTheCarCameBack` |
| 정차 중 공조를 놀이처럼 조작하게 하는 명령형 문구가 없고, 수동 와이퍼·방향지시등·실내등은 섬 입력이 아니다 | `WordingRulesTest.noImperativeDialPrompt`, `IslandTest.noManualWiperTurnSignalOrLampInput` |
| 전기차로 확인되지 않으면 남은 시간·토스트·환율 카드를 모두 숨기고 "시동" 고지를 한다 | `ModeAndEnergyTest.modeRules`, `StayEngineTest.reducedModeHasNoEnergyAnywhere`, `WordingRulesTest.reducedModeIdlingNotice` |
| 기어 P를 벗어나면 체류가 끝나고 모든 카드가 닫힌다 | `StayEngineTest.leavingParkEndsStayAndClosesEverything` |

### 신호를 못 읽을 때 (모르면 안전한 쪽으로)
| 신호 | 못 읽을 때 | 테스트 |
|---|---|---|
| 기어 (앱 시작 직후) | "차와 연결하는 중이에요"만 표시 (숫자·버튼 없음) | 화면 확인 `tools/edges.py` |
| 기어 (끊김·모름·오류 이벤트·상태가 '사용 가능'이 아님), 차량 연결 끊김, 시스템 주행 제한 | 주행 중으로 보고 화면을 접음 | `DrivingStateGateTest.unknownGearFailsSafeToDriving`, `DrivingStateGateTest.gearWithUnavailableOrErrorStatusIsUnknownAndCountsAsDriving`, `DrivingStateGateTest.disconnectedFailsSafeToDrivingEvenIfLastGearWasPark`, `DrivingStateGateTest.systemUxRestrictionOverridesParkGear` |
| 연료 종류, 배터리 % 또는 용량 | 축소 모드 (남은 시간 없음, "시동" 고지) | `StayEngineTest.fuelReadFailureStartsReduced`, `ModeAndEnergyTest.modeRules` |
| 전비 | 기본값 5 km/kWh (차가 준 값이 더 작으면 그 값) | `ModeAndEnergyTest.efficiencyUsesTheSmallerOfVehicleAndDefault` |
| 등받이 각도 | 등받이 고지를 끔 (시간·야간 고지는 그대로) | `StayEngineTest.noBackrestCapabilityMeansNoN3` |
| 2열 탑승 | 감지 안 되면 회색 '감지 안 됨'(마지막 줄 '뒷좌석 직접 확인'은 늘 있습니다) | `StayEngineTest.rearRowNeverHasADotForAnyOccupancy` |
| 실내등 · 충전 포트 | 영수증에 "확인 안 됨". 운전석 문이 열리는 순간 실내등 스위치를 다시 읽고, 실패하거나 늦으면 직전 값이 아니라 "확인 안 됨". 열린 영수증의 실내등 줄은 스위치를 따라 바뀜 | `StayEngineTest.lightUnknownIsReportedNotDropped`, `StayEngineTest.receiptLightRowFollowsTheSwitchBothWays` |
| 문, 창문 | "모름"으로 두고 "닫힘"으로 치지 않음. 영수증은 확인된 닫힘 → 열림에서만 뜸 | `PollGateTest.unreadableDoorIsUnknownNotClosed`, `StayEngineTest.unknownDoorNeverOpensOrClosesTheReceipt`, `StayEngineTest.windowsUnknownIsReportedNotClosed`, `StayEngineTest.unknownWindowDoesNotCountAsClosed` |
| 앱이 꺼진 사이의 시간·배터리 | 10분 넘게 지났거나, 충전됐거나, 배터리가 5%p 넘게 다르면 새 체류로 시작 | `StayEngineTest.restoreStartsNewStayAfter10Min` 외 4개 |
| 체류 중 충전 | 영수증 "충전 중이라 계산하지 않았어요" (0.0kWh라고 하지 않음) | `StayEngineTest.receiptShowsChargingInsteadOfZeroKwh` |
| 와이퍼 | AUTO이고 실제로 닦을 때만 먼 비. 수동 작동·AUTO 대기·읽기 실패는 비 없음 | `StayEngineTest.rainOnlyForAutoWiperActuallyWiping` |

## 한계 (에뮬레이터 검증, 실차 미검증)
- 모든 화면과 수치는 Pleos Connect Emulator 2.1.0에서 확인했고, 실차에서는 확인하지 않았습니다. 1시간 재경고·야간 고지·축소 모드·등받이 고지는 위 단위 테스트로 검증합니다. Virtual Console의 등받이 각도는 에뮬레이터 차량 데이터에 반영되지 않아 등받이 고지는 지원 차량에서만 켜집니다. 출시 빌드에는 시간을 빠르게 돌리는 코드가 없습니다.
- 2열 탑승 값은 앱이 뒤집어 읽습니다(`OCCUPANCY_SDK_INVERTED`). Pleos Connect Emulator 2.1.0에서 Virtual Console의 "Occupied"가 SDK에서 `false`로 왔기 때문입니다(2026-11-09 실측, 2열 두 좌석 모두). 차량 서비스가 보내는 원값은 AAOS 정의(1 = 빈 좌석, 2 = 탑승)와 맞으므로, 뒤집힘은 SDK의 정수→참·거짓 변환에서 생기는 것으로 보입니다(추정). 실차에서는 확인하지 않았습니다. 방향이 틀리면 탑승한 좌석이 "감지 안 됨"으로 보일 수 있습니다. 그래서 앱은 뒷좌석을 "비었음"이라고 말하지 않고, 영수증 마지막 줄 "뒷좌석 직접 확인"을 센서 값과 상관없이 항상 보여 줍니다.
- 실내등·독서등·와이퍼 레버·등받이는 차가 변화를 알려 주지 않아 1초마다 읽습니다. 이 읽기는 P에서 체류가 열려 있는 동안에만 돌고, P를 벗어나거나 체류가 끝나면 멈췄다가 다시 P가 되면 시작합니다(`PollGateTest`).
- 남은 시간은 차의 공조 상태에 공개 계수표를 곱한 추정입니다. 출처는 [docs/ENERGY_MODEL.md](docs/ENERGY_MODEL.md)에 있습니다. 계수는 영하 10°C 정상상태 기준입니다. 더 추운 날, 처음 데우는 동안, 한여름 직사광선에서는 실제 소모가 더 클 수 있습니다(추정).
- 계산에서는 내기순환이 난방 전력을 줄이는 것으로 보아, 내기순환을 켜면 남은 시간이 더 길게 나옵니다(추정). 그래도 앱은 내기순환을 권하지 않습니다. 내기순환과 외기를 바꿀 때 토스트에 분을 붙이지 않고, 공조를 켠 채 창문을 닫고 내기순환을 20분 넘게 쓰면 바깥 공기를 들이라는 카드를 띄웁니다.
- 에뮬레이터에서 신호별로 확인한 사실은 [docs/PLATFORM_NOTES.md](docs/PLATFORM_NOTES.md)에 있습니다.

## 라이선스
코드는 [LICENSE](LICENSE)를 따릅니다. 사용한 SDK·오픈소스 구성 요소의 버전과 라이선스는 [docs/THIRD_PARTY.md](docs/THIRD_PARTY.md)에 있습니다. 외부 그림·캐릭터는 쓰지 않았습니다.
