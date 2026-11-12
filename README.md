# 차섬 (Chaseom)

## 1. 앱 이름 및 한 줄 소개
- **앱 이름**: 차섬 (Chaseom)
- **한 줄 소개**: 전기차를 세워 두고 공조를 켠 채 머물 때, 내일 탈 거리만큼 배터리를 남긴 기준으로 **지금 설정으로 얼마나 더 머물 수 있는지(추정)**를 작은 섬 그림으로 보여 주는 Pleos 차량 앱입니다.
- **핵심 기능** (모두 기어 P에서만 보입니다)
  - **작은 날씨 섬**: 해 크기는 설정온도, 바람은 풍량, 물 높이는 배터리, 부표 1개는 머물 수 있는 30분(최대 6개), 배의 짐은 내일 몫입니다.
  - **따뜻함 환율**: 다이얼을 돌리는 순간 토스트가 남은 시간의 변화를 보여 줍니다(예: "설정 26°C · 풍량 6 · −21분 (추정)"). 비슷한 체감을 더 적은 전력으로 얻는 조합(운전석 열선, 저온 화상 주의)도 제안합니다.
  - **기다림 예보**: "몇 시까지 기다려요? (선택)"에서 시각을 고르면 부표 줄에 깃발이 서고, 오늘 머물 시간이 그 시각에 닿는지를 한 줄로 알려 줍니다.
  - **내일 지킬 몫, 안전 고지, 떠나기 전 영수증**: 내일 처음 갈 거리를 배터리에서 따로 남겨 둡니다. P에 들어설 때와 1시간마다, 야간에 공조 사용 경고를 띄웁니다. 운전석 문을 열면 머문 시간, 쓴 전력, "뒷좌석 직접 확인" 줄을 보여 줍니다.
- **안전 고지**
  - 계산한 숫자는 모두 **추정**이고 "(추정)"을 붙여 보여 줍니다.
  - 앱은 차를 **읽기만 하고 표시만** 합니다. 공조를 켜거나 끄거나 바꾸지 않으며, 차량 제어 권한도 런타임 권한 요청도 없습니다.

## 2. 실행 방법
1. 아래 3번 환경의 Pleos Connect Emulator를 Cold Boot로 켭니다. Virtual Console에서 배터리를 **19%**로 둡니다. 약 20% 이상이면 남은 시간이 "3시간 이상"으로 묶여 변화가 보이지 않습니다.
2. APK를 설치하고 앱 목록에서 **차섬**을 실행합니다. 처음 실행하면 내일 탈 거리 설정 화면이 나옵니다.
   ```bash
   adb install -r apk/Chaseom_Chaseom_release.apk
   ```
3. Virtual Console에서 다음을 조작합니다.
   - 기어 **P**: 머무는 화면과 섬이 나옵니다.
   - 운전석 설정온도 22→26°C, 풍량 3→6: 토스트 "설정 26°C · 풍량 6 · −21분 (추정)"이 뜨고 해가 커집니다. 비슷한 체감 카드대로 설정 21°C + 운전석 열선 2단으로 바꾸면 약 +16분 (추정)입니다.
   - 화면의 "몇 시까지 기다려요? (선택)": 시각을 고르면 깃발과 비교 줄이 나옵니다. "깃발 내리기"로 지웁니다.
   - 배터리 8%: 내일 몫 아래 경고가 나옵니다.
   - 운전석 문 열기: 떠나기 전 영수증이 나옵니다.
   - 기어 **D**: 섬과 숫자를 바로 접습니다. 주행 중에는 시스템이 앱 화면을 가립니다.

**소스에서 빌드**
- `ANDROID_HOME` 또는 `local.properties`의 `sdk.dir`이 필요합니다.
- 디버그 빌드와 단위 테스트: `./gradlew assembleDebug`, `./gradlew testDebugUnitTest` (테스트 175개).
- 릴리스 APK: `keystore.properties`를 `keystore.properties.example`처럼 준비한 뒤 `scripts/build-release.ps1`로 만듭니다.
- Windows에서 경로에 한글이 있으면 AGP가 빌드를 거부합니다. 영문 경로에 클론하거나, 드라이브 문자를 자동으로 연결(`subst`)하는 위 스크립트를 쓰세요.

## 3. 테스트 환경 및 필요한 설정
| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect Emulator **v2.1.0** (`system-images;android-34;pleos_car_emulator;x86_64`), Automotive, API 34, RAM 4GB, Cold Boot |
| 화면 규격 | **2560×1440**. 앱 영역은 넓은 창(약 1680 dp)과 1:1 분할 창(약 840 dp) 모두에서 확인했습니다. |
| 시스템 이미지 사이트 (Windows) | `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml` |
| 차량 값 입력 | Virtual Console (기어, 배터리, 공조, 좌석 열선, 문, 창문, 와이퍼) |
| 빌드 | Android Studio (JBR), Gradle 9.9.0, AGP 9.5.1, Kotlin 2.4.21, compileSdk 37, minSdk 28, targetSdk 34 |
| SDK | `ai.pleos.playground:Vehicle:2.3.2` (EvBattery, HVAC, Seat, Door, Window, Light, Wiper), AAOS `CarPropertyManager` (연료 종류, 배터리 용량, 충전구, 기어) |
| 패키지 | `app.chaseom` |
| 권한 | Pleos `CAR_ENERGY`, `CAR_CLIMATE`, `CAR_SEATS`, `CAR_WINDOWS`, `CAR_DOORS`, `CAR_LIGHTS` / AAOS `CAR_INFO`, `CAR_ENERGY_PORTS`, `CAR_POWERTRAIN` / `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`. 모두 normal 권한이라 런타임 팝업이 없습니다. 빌드 때 SDK와 AndroidX가 `VEHICLE_BINDING`과 앱 자신의 권한을 더 넣습니다. |

- 모든 화면과 수치는 에뮬레이터에서 확인했고, 실차에서는 확인하지 않았습니다.
- 남은 시간은 차의 공조 상태에 공개 계수표를 곱해 낸 추정입니다. 아주 추운 날, 처음 데우는 동안, 직사광선 아래에서는 실제 소모가 더 클 수 있습니다.
- Pleos Connect Emulator 2.1.0에서는 Virtual Console의 2열 "Occupied"가 SDK에 `false`로 들어옵니다. 그래서 앱은 이 값을 뒤집어 읽습니다. 앱은 뒷좌석을 "비었음"이라고 말하지 않고, 영수증의 "뒷좌석 직접 확인" 줄을 늘 보여 줍니다.

## 4. APK 위치
- **`apk/Chaseom_Chaseom_release.apk`** (release 빌드, 서명됨, sha256 `c2f1d1b86925162e754015be06bd2dd0a93916f552636169c4a6ae0bcdb6d589`)
- 시연 영상: [video/차섬_시연영상.mp4](video/차섬_시연영상.mp4)
- 위 APK를 에뮬레이터에서 잰 값: [docs/evidence/measure_release.json](docs/evidence/measure_release.json). 다이얼에서 토스트까지 중앙값 36 ms, 기어 P→D 접힘 최대 36 ms, 앱 시작 1.2–1.5초, 정지 화면 CPU 0.25%, 메모리 58 MB입니다.

## 안전 규칙 = 테스트
아래 규칙을 어기는 코드나 문구를 넣으면 단위 테스트(`./gradlew testDebugUnitTest`)가 실패합니다. 문구 테스트는 화면 문자열 파일을 직접 읽어 검사합니다.

| 지키는 규칙 | 테스트 |
|---|---|
| 계산한 숫자에는 늘 "(추정)"이 붙는다 | `WordingRulesTest.computedValuesMarkedEstimate` |
| 앱은 공조를 끄지 않는다고 말하고, 경고(K1·K2)도 공조를 끄거나 줄이라고 하지 않는다 | `WordingRulesTest.keepShareNeverLockedAndAppDoesNotControlClimate`, `WordingRulesTest.k1AndK2NeverTellThePersonToCutTheClimate` |
| 뒷좌석을 "비었음"으로 표시하지 않고, 영수증에는 늘 "뒷좌석 직접 확인" 줄이 있다 | `WordingRulesTest.rearSeatWording`, `StayEngineTest.receiptRowsOrderAndRearAlwaysCheck` |
| 수면·차박·보장 같은 금칙어가 없다(취급설명서 경고 인용만 예외) | `WordingRulesTest.noBannedWordsOutsideTheWarnings` |
| 머무는 동안 60분마다, 야간에는 시작과 매시 고지가 다시 뜬다 | `StayEngineTest.hourNoticeEvery60Minutes`, `StayEngineTest.nightNoticeAtStartAndHourly` |
| 주행 중 화면은 숫자 없이 짧은 안내뿐이고, 기어를 모르거나 연결이 끊기면 주행 중으로 보고 화면을 접는다 | `WordingRulesTest.drivingGlanceShortAndNumberFree`, `DrivingStateGateTest.unknownGearFailsSafeToDriving` |
| 전기차로 확인되지 않으면 남은 시간을 숨기고 "시동" 고지를 한다 | `ModeAndEnergyTest.modeRules`, `StayEngineTest.reducedModeHasNoEnergyAnywhere` |
| 기다림 깃발은 표시만 하고 안심 문구를 쓰지 않으며, 22시 뒤에는 보이지 않는다 | `WordingRulesTest.waitFlagWordingIsDisplayOnlyAndNeverReassures`, `WaitFlagTest.f8FlagHidesAtTwentyTwo` |
| 섬 그림은 잘리지 않는다. 카드가 함께 뜨면 같은 비율로 줄인다(최소 75%) | `IslandFitTest` |

## 라이선스
- 코드는 [LICENSE](LICENSE)를 따릅니다.
- 섬 그림, 아이콘, 화면 문구는 직접 만들었으며 외부 그림이나 캐릭터는 쓰지 않았습니다.

아래 외부 구성 요소는 각자의 라이선스를 따릅니다.

| 구성 요소 | 버전 | 라이선스 |
|---|---|---|
| Pleos Playground Vehicle SDK | 2.3.2 | Pleos Playground 이용 약관 (저장소에 넣지 않음, Gradle이 Pleos Maven에서 받음) |
| Gson (Pleos SDK 내부 의존성) | — | Apache License 2.0 |
| Jetpack Compose BOM, AndroidX Core KTX, Activity Compose, Lifecycle | 2026.09.00 / 1.19.1 / 1.13.0 / 2.11.0 | Apache License 2.0 |
| Kotlin, kotlinx.coroutines | 2.4.21 / 1.11.0 | Apache License 2.0 |
| JUnit (테스트만) | 4.13.2 | Eclipse Public License 1.0 |
