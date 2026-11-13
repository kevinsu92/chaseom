# 차섬 (Chaseom)

## 1. 앱 이름 및 한 줄 소개
- **앱 이름**: 차섬 (Chaseom)
- **한 줄 소개**: 전기차를 세워 두고 공조를 켠 채 머물 때, 내일 탈 거리만큼 배터리를 남긴 기준으로 **지금 설정으로 얼마나 더 머물 수 있는지(추정)**를 작은 섬 그림으로 보여 주는 Pleos 차량 앱입니다.
- **핵심 기능** (모두 기어 P에서만 보입니다)
  - **작은 섬**: 해 크기는 설정온도, 부표 1개는 머물 수 있는 30분(최대 6개), 부두 칩("내일 몫 30km")은 내일 몫, 깃발은 고른 시각입니다. 배터리는 화면 위 "배터리 n%"로 보여 줍니다.
  - **공조 환율**: 다이얼을 돌리는 순간 토스트가 남은 시간의 변화를 보여 줍니다(예: 설정온도를 22→26°C로 올리면 "설정 26°C · −16분 (추정)"). 비슷한 체감을 더 적은 전력으로 얻는 조합(운전석 열선, 저온 화상 주의)도 제안합니다.
  - **기다림 예보**: "몇 시까지 기다려요? (선택)"에서 시각을 고르면 부표 줄에 깃발이 서고, 오늘 머물 시간이 그 시각에 닿는지를 한 줄로 알려 줍니다.
  - **내일 지킬 몫, 안전 고지, 떠나기 전 영수증**: 내일 처음 갈 거리를 배터리에서 따로 남겨 둡니다. P에 들어설 때와 1시간마다, 야간에 공조 사용 경고를 띄웁니다. 운전석 문을 열면 머문 시간, 쓴 전력, "뒷좌석 직접 확인" 줄을 보여 줍니다.
  - **이 차 기준 보정**: 머무는 동안 실제 배터리 감소를 같은 공조 설정의 계산값과 비교해, 근거가 충분히 쌓이면(2%p·20분 이상) 그 차의 소비 계수를 0.8~1.5배 안에서 고칩니다. 고친 값을 쓰면 "이 차 기준으로 보정됨 (추정)"을 붙입니다. 남은 시간을 늘리는 쪽은 더 강한 근거(6%p·60분 이상)가 있을 때만 씁니다.
  - **조수석**: 동승자가 감지되거나 조수석 공조를 따로 설정하면 조수석 공조와 열선도 계산에 넣습니다.
- **안전 고지**
  - 계산한 숫자는 모두 **추정**이고 "(추정)"을 붙여 보여 줍니다.
  - 앱은 차를 **읽기만 하고 표시만** 합니다. 공조를 켜거나 끄거나 바꾸지 않으며, 차량 제어 권한도 런타임 권한 요청도 없습니다.

## 2. 실행 방법
1. 아래 3번 환경의 Pleos Connect Emulator를 Cold Boot로 켭니다. Virtual Console에서 배터리를 **19%**로 둡니다. 20% 이상이면 남은 시간은 "3시간 이상"으로 표시되고, 다이얼을 바꾸면 변화율(예: "머물 시간 −12% (추정)")이 뜹니다.
2. APK를 설치하고 앱 목록에서 **차섬**을 실행합니다. 처음 실행하면 내일 탈 거리 설정 화면이 나옵니다.
   ```bash
   adb install -r apk/Chaseom_Chaseom_release.apk
   ```
3. Virtual Console에서 다음을 조작합니다.
   - 기어 **P**: 머무는 화면과 섬이 나옵니다.
   - 운전석 설정온도 22→26°C: 토스트 "설정 26°C · −16분 (추정)"이 뜨고 해가 커집니다. 이어서 풍량 3→6: "풍량 6 · −5분 (추정)". 남은 시간은 약 2시간 47분 → 2시간 26분(−21분)이 됩니다. 두 값을 한 번에 바꾸면 토스트 하나("풍량 6 · 설정 26°C · −21분 (추정)")로 뜹니다. 비슷한 체감 카드대로 설정 21°C + 운전석 열선 2단으로 바꾸면 약 +16분 (추정)입니다.
   - 화면의 "몇 시까지 기다려요? (선택)": 시각을 고르면 깃발과 비교 줄이 나옵니다. "깃발 내리기"로 지웁니다.
   - 배터리 8%: 내일 몫 아래 경고가 나옵니다.
   - 운전석 문 열기: 떠나기 전 영수증이 나옵니다.
   - 기어 **D**: 섬과 숫자를 바로 접습니다. 주행 중에는 시스템이 앱 화면을 가립니다.

**소스에서 빌드**
- JDK 17 이상(`JAVA_HOME`, 예: Android Studio에 들어 있는 JBR)과 Android SDK(`ANDROID_HOME` 또는 `local.properties`의 `sdk.dir`)가 필요합니다.
- macOS/Linux에서는 처음 한 번 `chmod +x gradlew`가 필요할 수 있습니다.
- 디버그 빌드와 단위 테스트: `./gradlew assembleDebug`, `./gradlew testDebugUnitTest` (테스트 348개).
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
| SDK | `ai.pleos.playground:Vehicle:2.3.2` (EvBattery, HVAC, Seat, Door, Window, Light, Wiper), AAOS `CarPropertyManager` (연료 종류, 배터리 용량, 충전구, 기어, 외기온) |
| 패키지 | `app.chaseom` |
| 권한 | Pleos `CAR_ENERGY`, `CAR_CLIMATE`, `CAR_SEATS`, `CAR_WINDOWS`, `CAR_DOORS`, `CAR_LIGHTS` / AAOS `CAR_INFO`, `CAR_ENERGY_PORTS`, `CAR_POWERTRAIN`, `CAR_EXTERIOR_ENVIRONMENT` / `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `RECEIVE_BOOT_COMPLETED`(시동 뒤 앱을 열지 않아도 머문 시간을 세기 위함). 모두 normal 권한이라 런타임 팝업이 없습니다. 빌드 때 SDK와 AndroidX가 `VEHICLE_BINDING`과 앱 자신의 권한을 더 넣습니다. |

- 모든 화면과 수치는 에뮬레이터에서 확인했고, 실차에서는 확인하지 않았습니다.
- 남은 시간은 차의 공조 상태에 공개 계수표를 곱해 낸 추정입니다. 계수, 출처, 공개 실측과의 대조는 [docs/ENERGY_MODEL.md](docs/ENERGY_MODEL.md)에 있습니다. 아주 추운 날(영하 18°C에서 실제 소모가 1.15~1.26배), 처음 데우는 동안, 직사광선 아래에서는 실제 소모가 더 클 수 있습니다.
- 알려진 한계는 아래 '한계' 절에 있습니다.

## 4. APK 위치
- **`apk/Chaseom_Chaseom_release.apk`** (release 빌드, 서명됨, 버전 1.3.1, sha256 `f27d00adb64b16d748488d1cb84f09955ad5a255738bd10d5454844f821114f8`)
- 시연 영상: [video/차섬_시연영상.mp4](video/차섬_시연영상.mp4) (sha256 `5874e834cd8cfe7f76882ee2d5e3123fadfbd9794c521b2e2c9edfdd5552e2b2`)

## 검증 표
위 APK(1.3.1)를 에뮬레이터에서 잰 값입니다. 원자료는 [docs/evidence/measure_release.json](docs/evidence/measure_release.json)입니다. 측정 때 PC 부하가 높았습니다(호스트 CPU 68~90%, 에뮬레이터 load 6.9). 그래서 시간 값은 부하가 낮을 때보다 길게 나왔을 수 있습니다.

| 항목 | 값 |
|---|---|
| 단위 테스트 | 348개 통과 |
| 다이얼 → 토스트가 화면에 뜨기까지 (10회) | 중앙값 38 ms, 최대 80 ms |
| 머무는 화면을 연 뒤 섬의 첫 프레임 (10회) | 중앙값 45 ms, 최대 112 ms |
| 기어 P → D, 섬과 숫자가 사라지기까지 (10회) | 중앙값 30 ms, 최대 47 ms |
| 앱 시작 (콜드 스타트, 3회) | 2.2–2.7초 (2,152–2,701 ms) |
| 정지 화면 CPU (30초) | 평균 0.5% |
| 10초마다 다이얼 1회, 60초 평균 CPU | 1.1% (전환 순간 최대 19%) |
| 메모리 (PSS) | 47 MB |

앱 시작은 가장 길 때 2.7초로 Pleos 기준(시작 10초 이내) 안입니다.

## 한계 (2차에서 고칠 것)
- **2열 탑승 값은 SDK 2.3.2의 변환을 확인한 뒤 뒤집어 읽습니다.** Pleos Vehicle SDK 2.3.2는 AAOS 원값(0 모름, 1 비었음, 2 탑승)을 `원값 == 1`일 때 `true`로 바꿉니다(바이트코드와 에뮬레이터 로그로 확인). 그래서 SDK의 `true`는 "비었음"입니다. 이 변환은 에뮬레이터가 아니라 APK 안의 SDK에서 일어나므로, 실차 VHAL이 AAOS 표준 값(0/1/2)을 줄 때는 실차에서도 같습니다. 실차의 원값은 확인하지 못했습니다. 앱은 확인한 SDK 버전에서만 값을 뒤집고, 다른 버전이면 "확인 안 됨"으로 둡니다. SDK 버전을 올리면 테스트가 먼저 실패합니다(`cabin/Occupancy.kt`). AAOS `SEAT_OCCUPANCY`를 직접 읽는 권한은 signature|privileged(`CONTROL_CAR_SEATS`)라 쓰지 않습니다. 어느 경우든 앱은 뒷좌석을 "비었음"이라고 말하지 않고, 영수증의 "뒷좌석 직접 확인" 줄을 늘 보여 줍니다.
- **뒷좌석 센서가 '알 수 없음'(원값 0)을 보내도 '탑승 감지됨'으로 표시하고 강조 점을 붙입니다.** SDK 2.3.2가 0과 2를 모두 `false`로 넘겨 앱이 둘을 구분할 수 없기 때문입니다. 직접 확인하게 하는 쪽(안전 쪽)으로 틀립니다.
- **재연결은 AAOS 차량 서비스와 Pleos SDK 모두에 있습니다.** AAOS `Car` 연결은 하나(`vehicle/CarConnection.kt`)이고, 차량 서비스가 다시 시작되면 기어·실내 속성·UX 제한을 다시 등록합니다. 끊긴 동안은 기어를 모름으로 보고 화면을 접습니다(`DrivingStateGateTest`). Pleos Vehicle SDK는 연결이 끊겼음을 알리는 콜백이 문서에 없어, 머무는 동안 60초 넘게 아무 응답이 없으면 SDK를 다시 만들어 붙입니다(60초·120초·300초 간격). 초기화가 실패하면 5초·15초 뒤, 그다음 60초마다 다시 시도합니다. '연결됨'은 첫 기어 값을 받은 뒤에만 표시합니다.
- **외기온은 영하 10°C보다 추울 때만 씁니다.** AAOS `ENV_OUTSIDE_TEMPERATURE`(normal 권한)를 읽어, 영하 10°C보다 추우면 난방 부하를 (설정온도 − 외기온) / 32로 늘립니다. 그보다 따뜻하거나, 값을 못 읽거나, −40~60°C 밖이면 영하 10°C로 계산합니다. 계수를 잰 조건이 영하 10°C이고 온화한 날의 선형식은 검증되지 않았기 때문에, 외기온으로 남은 시간을 늘리지는 않습니다(짧게 틀리는 쪽). 냉방(2.0 kW)은 외기와 무관한 상단값 그대로입니다.

- **차량 값 재확인 규칙:** 10초마다 공조 전원과 배터리 값을 다시 읽지만, 차가 직접 알려 준 값을 이 재확인으로 덮어쓰지 않습니다(SDK가 가끔 틀린 응답을 주기 때문). 앱을 켤 때 받은 값만 있으면 같은 값이 두 번 연속 올 때 채웁니다. 차가 처음 알려 준 값 자체가 틀렸다면, 다음 변경 알림이 올 때까지 그대로 남을 수 있습니다.
- **머문 시간 측정 서비스는 시동이 켜져 있는 동안 계속 돕니다.** 다른 앱을 보고 있다가 P에 들어서도 머문 시간을 셉니다. 주행 중에는 화면을 그리지 않고 1초 갱신도 멈춥니다. 다만 차가 절전에 들어가기 전에 앱 프로세스가 강제 종료됐다면, 그 시동의 첫 주차는 앱을 연 때부터 셉니다.
- **차량 값이 60초 넘게 확인되지 않으면 남은 시간을 숨기고 "확인 안 됨"으로 표시합니다.** Pleos SDK 연결이 끊기면 다시 연결을 시도합니다.
- **온화한 날도 영하 10°C로 계산합니다.** 그래서 영하 10°C보다 따뜻한 날에는 남은 시간이 실제보다 짧게 나옵니다(안전한 쪽).

## 안전 규칙 = 테스트
아래 규칙을 어기는 코드나 문구를 넣으면 단위 테스트(`./gradlew testDebugUnitTest`)가 실패합니다. 문구 테스트는 화면 문자열 파일을 직접 읽어 검사합니다.

| 지키는 규칙 | 테스트 |
|---|---|
| 계산한 숫자에는 늘 "(추정)"이 붙는다 | `WordingRulesTest.computedValuesMarkedEstimate` |
| 앱은 공조를 끄지 않는다고 말하고, 내일 몫 근처·이하 경고도 공조를 끄거나 줄이라고 하지 않는다 | `WordingRulesTest.keepShareNeverLockedAndAppDoesNotControlClimate`, `WordingRulesTest.k1AndK2NeverTellThePersonToCutTheClimate` |
| 뒷좌석을 "비었음"으로 표시하지 않고, 영수증에는 늘 "뒷좌석 직접 확인" 줄이 있다. 뒷좌석 센서를 읽지 못하면 "감지 안 됨"이 아니라 "확인 안 됨"이라고 쓴다 | `WordingRulesTest.rearSeatWording`, `StayEngineTest.receiptRowsOrderAndRearAlwaysCheck`, `StayEngineTest.rearReadFailureIsUnknownNotNotDetected` |
| 수면·차박·보장 같은 금칙어가 없다(취급설명서 경고 인용만 예외) | `WordingRulesTest.noBannedWordsOutsideTheWarnings` |
| 머무는 동안 60분마다, 야간에는 시작과 매시 고지가 다시 뜬다 | `StayEngineTest.hourNoticeEvery60Minutes`, `StayEngineTest.nightNoticeAtStartAndHourly` |
| 주행 중 화면은 숫자 없이 짧은 안내뿐이고, 기어를 모르거나 연결이 끊기면 주행 중으로 보고 화면을 접는다 | `WordingRulesTest.drivingGlanceShortAndNumberFree`, `DrivingStateGateTest.unknownGearFailsSafeToDriving` |
| 전기차로 확인되지 않으면 남은 시간을 숨기고 "시동" 고지를 한다 | `ModeAndEnergyTest.modeRules`, `StayEngineTest.reducedModeHasNoEnergyAnywhere` |
| 기다림 깃발은 표시만 하고 안심 문구를 쓰지 않으며, 22시 뒤에는 보이지 않는다 | `WordingRulesTest.waitFlagWordingIsDisplayOnlyAndNeverReassures`, `WaitFlagTest.f8FlagHidesAtTwentyTwo` |
| 섬 그림은 늘 원래 크기로 그린다. 부표 6개와 마지막 칸의 깃발도 그림 오른쪽 끝에서 8px 이상 안쪽에 든다 | `S1LayoutTest` |

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
