# tools — 에뮬레이터에서 차섬을 손대지 않고 재현·측정하는 도구

모든 도구는 Pleos Connect Emulator의 **Virtual Console(VC)** 을 HTTP로 조작해 차의 상태를 바꿉니다. 바꾸는 상태는 기어, 배터리, 공조, 좌석, 문, 창문, 조명입니다. 앱은 그 값을 **실제 차량 신호 경로**(Vehicle SDK·AAOS)로 받습니다.
- 앱에 broadcast, intent extra, setprop을 쓰지 않습니다.
- 시각을 바꾸거나 시간을 빠르게 돌리지 않습니다.
- release 빌드에는 데모 모드나 가짜 값이 없습니다.
- 이 폴더는 APK에 들어가지 않습니다.

준비물은 다음과 같습니다.
- 실행 중인 Pleos Connect Emulator 2.1.0
- 설치된 차섬 (`adb install -r apk/Chaseom_Chaseom_release.apk`)
- adb (PATH, `ADB`, `ANDROID_HOME` 중 하나)
- Python 3.9 이상 (외부 패키지 없음)

| 도구 | 하는 일 | 앱 데이터 |
|---|---|---|
| `python tools/demo.py` | **30초 시연을 자동 재생**합니다. 배터리 19% → 히터 26°C·풍량 7 → 21°C + 열선 2단 → 내일 몫 경고 → 운전석 문(영수증) → 기어 D 순서입니다. 단계마다 화면 문구와 "VC 쓰기 → 화면 반영 ms"를 출력하고, 끝에 `6/6 장면 확인`을 냅니다 | 그대로 둠 (처음 실행이면 첫 화면을 자동으로 넘김) |
| `python tools/scenario.py <폴더>` | 시나리오(안전 배너, 환율 카드, 내일 몫 배너, 영수증, 주행 중 화면)를 돌리며 단계마다 스크린샷을 남깁니다 | **`pm clear`로 초기화**합니다. 내일 몫 설정이 기본값(30 km)으로 돌아갑니다 |
| `python tools/measure.py <apk> <json>` | release APK의 성능을 재서 JSON에 APK sha256과 함께 남깁니다. 항목은 토스트 첫 프레임, P→D 접힘, 앱 시작, CPU, 메모리입니다. README 검증 표의 원본입니다 | 그대로 둠 |
| `python tools/check_hash.py` | `apk/`의 APK sha256이 README 검증 표·`docs/evidence/measure_release.json`의 해시와 같은지 확인합니다(제출 직전) | 에뮬레이터 필요 없음 |
| `python tools/edges.py <폴더>` | 엣지 상태(시작 직후 "연결 중", 충전 뒤 새 체류, 10분 안 재시작은 이어 감, 충전 중 영수증)를 캡처합니다 | 그대로 둠 |

시간 측정은 앱이 남기는 `PROBE` 로그 한 줄(벽시계 시각과 차량 신호 값만, 사용자 입력은 없음)을 씁니다. 이 로그는 평소에는 꺼져 있고, 도구가 시작할 때 `adb shell setprop log.tag.PROBE D`로 켭니다. 호스트와 에뮬레이터의 시계 차를 8회 왕복 중 가장 빠른 값으로 보정하므로 오차는 약 ±20 ms입니다.

영수증은 실제로 1분 이상 머문 뒤에만 나오므로, `demo.py`는 그 단계에서 1분쯤 기다립니다.

**좁은 창(1:1 분할) 확인**: Pleos는 앱 두 개를 띄우면 앱 영역을 1:1로 나눕니다(창 하나 약 840dp). 차섬의 모든 화면은 이 폭에서도 확인합니다. 스크립트에서는 `with vc.small_window("app.chaseom"):`를 쓰면 앱 창을 841dp로 줄였다가 끝날 때 반드시 원래대로 되돌립니다(`am task resize`, 화면 해상도나 밀도는 바꾸지 않음).
