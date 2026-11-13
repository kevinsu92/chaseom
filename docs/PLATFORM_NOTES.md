# 플랫폼 노트 — 실제로 재 보고 알게 된 것

Pleos Connect Emulator 2.1.0과 Virtual Console(VC)에서 차섬이 읽는 신호를 하나씩 조작했습니다. 그리고 앱이 언제, 어떤 값을 받는지 기록했습니다. 시각은 앱의 `PROBE` 로그(측정할 때만 켬)로 쟀습니다.

## 1. 신호별 결과
| 신호 | API | 결과 | 앱의 처리 |
|---|---|---|---|
| 공조 전원·A/C·풍량·내기순환 | Vehicle SDK HVAC | **운전석 영역(`ROW_1_LEFT`)으로 구독하면 거부**됨(`area ID: 0x1 not supported`). `check…Capabilities`가 주는 영역(에뮬레이터: 1·2열 묶음)으로는 정상 | capability가 준 영역 중 운전석을 포함한 것을 골라 구독 |
| 설정온도·좌석 열선 | HVAC / Seat | 운전석·조수석 영역으로 정상. 열선 VC 값 −3..3 중 음수는 통풍 | 0 미만은 0단으로 처리 |
| 등받이 각도 | Seat | SDK 경로는 정상(READ_WRITE, 0..100). 그러나 **VC 등받이 조작이 차량 값에 들어가지 않음**(값이 0에 고정, 이벤트 0건, VC 카드도 숨김) | 등받이 고지는 차가 값을 줄 때만 켜짐. 에뮬레이터에서는 꺼짐 |
| 2열 탑승 | Seat Occupancy | 이벤트는 즉시 옴. **값이 VC와 반대**(VC "Occupied" → SDK `false`) | 원인: SDK 2.3.2가 `원값 == 1`(AAOS VACANT)을 `true`로 변환(바이트코드 확인). 확인한 SDK 버전에서만 반전, 다른 버전은 "확인 안 됨"(`Occupancy.kt`, 버전 고정 테스트). AAOS `SEAT_OCCUPANCY`는 signature 권한이라 쓰지 않음. SDK 2.3.2는 센서 상태 불명(UNKNOWN)도 `false`로 주므로, 앱의 "탑승 감지됨"에는 상태 불명도 포함됨(직접 확인을 부르는 쪽). 어느 쪽이든 앱은 "비었음"이라고 말하지 않음 |
| 연료 종류 | AAOS `INFO_FUEL_TYPE` | `[10]` = 전기 | 전기 단독 + SOC·용량을 읽을 때만 전체 모드, 그 밖에는 축소 모드 |
| 배터리 용량 | AAOS `INFO_EV_BATTERY_CAPACITY` | 77,400 Wh | kWh로 변환 |
| 전비 | `EvBattery.getCumulativeEvEfficiency` | `ERROR_NOT_SUPPORTED_PROPERTY` | 기본값 5 km/kWh ([ENERGY_MODEL](ENERGY_MODEL.md)) |
| 충전 포트 | AAOS `EV_CHARGE_PORT_OPEN` | 즉시 수신 | 영수증 줄 |
| 문·트렁크 | Door | 운전석 `ROW_1_LEFT`, 트렁크 = `DoorValue.REAR` | 운전석 문이 열리는 순간 영수증 |
| 창문 | Window | VC 30% → SDK 3 (범위 0..10) | 0 초과 = 열림 |
| 실내등 | Light (get만 제공) | 1초 폴링, VC 반영 0.4~0.5초 | 영수증 줄 |
| 독서등 | Light | VC 앞좌석 독서등이 반영되지 않음 | 영수증에서 제외 |
| 기어 | AAOS `GEAR_SELECTION` (공개 문서 API) | 즉시 수신. Pleos `Vehicle.getGear()`도 동작하지만 공개 문서에 없어 쓰지 않음 | P가 아니거나 시스템 주행 제한이면 화면을 접음. 첫 기어 값을 받기 전에는 "차와 연결하는 중이에요" |
| SOC | Vehicle SDK | 즉시 수신 | 계산 입력 |

## 2. 빌드·실행 함정
- **R8가 Pleos SDK 안의 Gson `TypeToken` 시그니처를 지웁니다.**
  - 증상: release 빌드에서만 capability 응답이 실패합니다("TypeToken must be created with a type argument"). 공조 전원·풍량·A/C·내기순환·등받이 범위가 오지 않습니다.
  - 해결: `app/proguard-rules.pro`의 TypeToken keep 규칙. **다른 Pleos 앱도 release 빌드에 같은 규칙이 필요합니다.**
- **AAOS에서 앱은 user 10으로 실행됩니다.** `adb shell pm clear <패키지>`는 user 0만 지웁니다. 초기화는 `pm clear --user current`로 하세요.
- **VC는 HTTP로 조작할 수 있습니다.** 방법은 `POST /vehicle/...`이고, `/browser/session` heartbeat가 필요합니다. 그래서 시연과 검증을 사람 손 없이 다시 돌릴 수 있습니다(`tools/`).
- **SDK 콜백은 메인 스레드가 아닌 binder 스레드에서 옵니다.** 리스너는 값을 상태 흐름에 넣기만 하고, 화면은 메인에서 모아 그립니다.
- **방향지시등을 켜면 시스템 사각지대 카메라 창이 앱 왼쪽 위를 덮습니다.** 그래서 섬 입력에서 방향지시등을 뺐습니다.

## 3. 실차에서 확인할 것 (에뮬레이터로는 알 수 없음)
- 등받이 값의 방향(0이 젖힘인지 세움인지)
- 2열 탑승 값의 의미(반전 여부)
- 영수증 순서: 실차에서는 문을 열기 전에 시동이나 전원 상태가 먼저 바뀔 수 있음
- 실제 내연·하이브리드 차량의 `INFO_FUEL_TYPE` 값
