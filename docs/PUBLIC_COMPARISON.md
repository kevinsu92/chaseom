# 공개 실측 대조표 — 실차 대신 같은 식으로 맞춰 보기

> 이 파일은 `python tools/compare_public.py --write`가 만듭니다. 손으로 고치지 마세요. `python tools/compare_public.py --check`는 이 파일이 스크립트 출력과 같은지 확인합니다.

차섬은 실차에서 재지 않았습니다. 대신 정차한 전기차의 공조 소비를 **조건과 함께 공개한 실측**을 모아, 같은 조건을 앱 식(`app/src/main/java/app/chaseom/stay/EnergyModel.kt`, 빌드 G)에 넣고 차이를 냅니다. 스크립트는 앱 식을 한 줄씩 옮겼고, Kotlin `Float`처럼 매 연산을 32비트로 반올림합니다. 그래서 아래 시연 숫자는 JVM 단위 테스트 `OutsideTempGapTest`와 같습니다.

## 1. 시연 숫자 (재현 확인)

조건: 배터리 19%, 용량 77.4 kWh, 내일 몫 30 km(전비 5 km/kWh × 여유 1.2), 외기온 못 읽음(= −10°C로 계산).

| 장면 | 앱 계산 | 단위 테스트 |
|---|---|---|
| 22°C·풍량 3 | 167분 = **약 2시간 47분 (추정)** | `OutsideTempGapTest.heroFollowsOutsideTemperature` (167) |
| 26°C·풍량 6으로 바꾼 토스트 | **−21분** (167 → 146) | `OutsideTempGapTest.demoToastUnchangedAtMinus10` (−21) |
| 21°C + 운전석 열선 2단 카드 | **+16분** (146 → 162) | `OutsideTempGapTest.emulatorReadingKeepsTheDemo` (16) |

## 2. 대조표

- 앱 값은 실측 조건에 가장 가까운 설정으로 계산했습니다(풍량은 공개되지 않아 3단 가정).
- **앱 ÷ 실측이 1보다 크면** 앱이 더 많이 쓴다고 계산합니다. 남은 시간을 실제보다 짧게 말하는 쪽입니다. **1보다 작으면** 실제가 더 쓰고, 화면의 남은 시간이 실제보다 깁니다.
- 등급: **A** 기관 시험(외기·설정·시간 공개), **B** 언론·측정 기록(조건 일부), **C** 오너 1명의 기록.
- 확인: **원문** = 원문을 직접 열어 숫자 확인, **QA** = 05_qa가 원문 대조한 기존 9건(`docs/ENERGY_MODEL.md` §6), **에이전트** = 웹 조회로 본문 확인(원문 재대조 전).

| # | 실측 | 등급 | 확인 | 실측 kW | 앱 설정 | 앱 kW | 앱 ÷ 실측 | 판정 |
|---|---|---|---|---|---|---|---|---|
| D1 | [DOE·ANL 2024, 외기 0°F(−17.8°C), 실내 22.2°C 유지, BEV 1 / BEV 2](https://www.energy.gov/sites/default/files/2024-10/Impact_of_Cold_Ambient_Temperature_on_BEV_Performance_v15_TechEditFinal_12Sep2024__0.pdf) — 앱이 적게 잡는다 | A | 원문 p.11–12 | 3.77 / 4.12 | 22.2°C·풍량 3, 외기 −17.8°C 반영 | 3.26 | 0.87 / 0.79 | **실측이 더 큼** |
| D2 | [DOE·ANL 2024, 외기 20°F(−6.7°C), 실내 22.2°C 유지, BEV 1 / BEV 2](https://www.energy.gov/sites/default/files/2024-10/Impact_of_Cold_Ambient_Temperature_on_BEV_Performance_v15_TechEditFinal_12Sep2024__0.pdf) — 1대는 많이, 1대는 적게 잡는다 | A | 원문 p.11–12 | 2.33 / 3.15 | 22.2°C·풍량 3 (−10°C보다 따뜻해 −10°C로 계산) | 2.70 | 1.16 / 0.86 | **섞임** |
| D3 | [DOE·ANL 2024, 외기 95°F(35°C), 냉방, BEV 1 / BEV 2](https://www.energy.gov/sites/default/files/2024-10/Impact_of_Cold_Ambient_Temperature_on_BEV_Performance_v15_TechEditFinal_12Sep2024__0.pdf) | A | 원문 p.11–12 | 1.50 / 0.99 | A/C·풍량 3 | 2.39 | 1.59 / 2.40 | 앱이 더 많이 씀 |
| D4 | [DOE·ANL 2024, 외기 72°F(22.2°C), 공조 꺼짐, BEV 1 / BEV 2](https://www.energy.gov/sites/default/files/2024-10/Impact_of_Cold_Ambient_Temperature_on_BEV_Performance_v15_TechEditFinal_12Sep2024__0.pdf) | A | 원문 p.11–12 | 0.23 / 0.14 | 공조 꺼짐 (기본 부하) | 0.30 | 1.33 / 2.08 | 앱이 더 많이 씀 |
| H1 | [ADAC 2022, 외기 −10°C, 실내 20°C 유지 (전기차 7종, 현대 코나 일렉트릭 포함)](https://www.adac.de/rund-ums-fahrzeug/elektromobilitaet/tests/heizung-elektroauto/) | A | QA | 1.50–2.00 | 20°C·풍량 3 | 2.55 | 1.27–1.70 | 앱이 더 많이 씀 |
| H3 | [ADAC 2021 겨울 정체, −9 ~ −14°C, 22°C + 시트 열선 + 미등, 12시간 (Zoe / e-Up)](https://www.adac.de/rund-ums-fahrzeug/elektromobilitaet/info/haertetest-winter-stau/) — 외기온을 못 읽으면 앱 2.76 kW, Zoe 대비 0.92배. Zoe 값에는 미등 부하 포함 | A | QA | 3.00 / 2.20 | 22°C·풍량 3·열선 2단, 외기 −14°C 반영 | 3.04 | 1.01 / 1.38 | 앱이 더 많이 씀 |
| H2 | [ADAC 2022, 코나 일렉트릭을 20°C까지 데우는 첫 약 25분](https://www.adac.de/rund-ums-fahrzeug/elektromobilitaet/tests/heizung-elektroauto/) — 차가운 차를 처음 데우는 25분만 적게 잡는다 | A | QA | 3.60 | 20°C·풍량 3 | 2.55 | 0.71 | **실측이 더 큼** |
| B1 | [미국 INL, 액세서리 OFF 기본 부하](https://avt.inl.gov/sites/default/files/pdf/presentations/VSATTOctober201512VAuxilaryLoad.pdf) | A | QA | 0.14–0.26 | 공조 꺼짐 (기본 부하) | 0.30 | 1.16–2.21 | 앱이 더 많이 씀 |
| A24 | [ADAC 2024 여름 정체, Tesla Model Y, 챔버 35°C + 일사, 8시간](https://insideevs.de/news/728377/elektroauto-autobahnstau-stromverbrauch-klimaanlage/) — ADAC 원문 미대조 | A(보도) | 에이전트 | 1.50 | A/C·풍량 3 | 2.39 | 1.59 | 앱이 더 많이 씀 |
| N1 | [Tesla Model 3 히트펌프, 최저 −8°C, 21°C, 7.5시간](https://fossbytes.com/guy-slept-2021-tesla-model-3-8c-check-battery-efficiency/) | B | 에이전트 | 1.36 | 21°C·풍량 3 | 2.62 | 1.93 | 앱이 더 많이 씀 |
| N2 | [Tesla Model 3 (Car and Driver), 평균 약 −9.4°C, 18.3°C, 약 2일](https://cleantechnica.com/2022/01/21/car-and-driver-does-its-own-tesla-cold-weather-tests/) — 용량 75 kWh 가정 | B | 에이전트 | 1.65 | 18.3°C·풍량 3 | 2.42 | 1.47 | 앱이 더 많이 씀 |
| N4 | [VW ID.4 Pro S (제조사 시험), 약 −7°C, 22°C, 열선 1단](https://motorweek.org/this_just_in/volkswagen-tests-how-long-id-4-pro-s-ev-can-stay-warm-in-sub-freezing-tempe/) — 3시간 측정을 41시간으로 외삽한 제조사 값 | B | 에이전트 | 1.90 | 22°C·풍량 3·열선 1단 | 2.74 | 1.44 | 앱이 더 많이 씀 |
| N5 | [Tesla Model 3 LR (EVsmart), 평균 −5°C, 20°C, 내기순환, 38시간](https://blog.evsmart.net/t/stuck-in-snow-storm-test-with-electric-vehicles-at-minus-five-degrees-en/2096) — 용량 가정 | B | 에이전트 | 1.60 | 20°C·풍량 3·내기순환 | 2.03 | 1.27 | 앱이 더 많이 씀 |
| H4 | [Tesla 캠프 모드, 추운 날 (2차 인용)](https://electriccarscheme.com/blog/tesla-model-y-camp-mode-complete-ev-camping-guide-2025) | B | QA | 1.50–1.90 | 22°C·풍량 3 | 2.69 | 1.42–1.79 | 앱이 더 많이 씀 |
| K1 | [기아 EV9, 한여름 밤 유틸리티 모드 냉방, 약 8시간](https://economist.co.kr/article/view/ecn202607240017) | B | 에이전트 | 0.87 | A/C·풍량 3 | 2.39 | 2.75 | 앱이 더 많이 씀 |
| K2 | [현대 아이오닉 9, 약 22°C, 차박 하룻밤](https://evcentral.com.au/?p=44552) — 인용 전 한 번 더 확인 권장 | B | 에이전트 | 1.10 | 22°C·풍량 3 (온화한 날도 −10°C로 계산) | 2.69 | 2.45 | 앱이 더 많이 씀 |
| O1 | [현대 코나 일렉트릭 (오너), 약 −3°C, 21°C, 4시간](https://www.insideevsforum.com/community/threads/how-long-could-you-idle-your-kona-in-the-cold-and-stay-warm.12830/) | C | 에이전트 | 1.12 | 21°C·풍량 3 | 2.62 | 2.34 | 앱이 더 많이 씀 |
| O2 | [기아 레이 EV (오너), 약 −3°C, 약 22.5°C, 10시간](https://www.clien.net/service/board/cm_car/18419549) | C | 에이전트 | 1.20 | 22.5°C·풍량 3 | 2.73 | 2.27 | 앱이 더 많이 씀 |
| M1 | [현대 아이오닉 5 N (오너), 온화한 날, 23°C, 약 7시간](https://brunch.co.kr/@8fda19a51618425/95) | C | QA | 0.48 | 23°C·풍량 3 (온화한 날도 −10°C로 계산) | 2.76 | 5.75 | 앱이 더 많이 씀 |
| C2 | [Rivian 캠프 모드 에어컨 (오너 보고)](https://rivianforums.com/forum/threads/power-consumption-during-camping-mode.62204/) — 고부하 3 kW 사례 있음 | C | QA | 1.00–2.00 | A/C·풍량 3 | 2.39 | 1.19–2.39 | 앱이 더 많이 씀 |
| C1 | [현대 아이오닉 5, 42°C 직사광선, 에어컨 + 배터리 냉각 (오너, 순간값)](https://www.clien.net/service/board/cm_car/19225608) — 순간값이라 평균과 비교할 수 없다. 폭염에서 적게 잡을 가능성(기관 시험 없음) | C | QA | 3.48 | A/C·풍량 3 | 2.39 | — | 비교 불가 |

DOE 행의 실측 kW는 원문 Table 2의 공조 전력과 기타 전력을 더한 값입니다(BEV 1 / BEV 2 순서, 차종은 비공개).

## 3. 차이가 큰 곳: 영하 18°C는 실제가 더 씁니다

DOE·ANL 2024 시험의 외기 0°F(−17.8°C)에서는 두 대 모두 실측이 앱보다 큽니다. 빌드 G가 외기온을 읽어 난방을 늘려도(앱 3.26 kW) 그렇습니다. 숨기지 않고 크기까지 적습니다.

DOE는 유지 시간을 "사용 가능 에너지 ÷ (공조 + 기타 전력)"으로 계산했습니다. 차섬의 남은 시간 식과 같은 꼴이라, 같은 에너지를 넣으면 앱이 몇 배 길게 말하는지 바로 나옵니다.

| 차 | DOE 사용 가능 에너지 | 앱 식 (에너지 ÷ 앱 kW) | DOE 유지 시간 (원문) | 앱이 길게 말하는 비율 |
|---|---|---|---|---|
| BEV 1 | 54.5 kWh | 16.7시간 | 14.5시간 | 1.15배 |
| BEV 2 | 61.6 kWh | 18.9시간 | 15.0시간 | 1.26배 |

차섬 시연 차(77.4 kWh, 내일 몫 30 km)에 넣으면 이렇습니다(실측 전력은 BEV 1 / BEV 2).

| 배터리 | 앱 화면 (22.2°C·풍량 3, 외기 −17.8°C를 읽은 경우) | 실측 전력으로 계산 | 앱이 길게 말하는 분 |
|---|---|---|---|
| 19% | 137분 (2시간 17분) | 119분 / 109분 | 18분 / 28분 |
| 20% | 152분 (2시간 32분) | 131분 / 120분 | 21분 / 32분 |

참고로 같은 외기에서 시연 설정(22°C·풍량 3, 19%)은 138분 → 26°C·풍량 6 124분(−14분) → 열선 카드 135분(+11분)으로 계산됩니다.

**앱과 문서가 이미 말하고 있는 것**
- 화면의 계산 숫자에는 모두 "(추정)"이 붙습니다(`WordingRulesTest.computedValuesMarkedEstimate`).
- 앱 정보 화면은 계수가 "영하 10°C 정상상태 기준"이며 외기온이 더 추울 때만 그 값을 쓴다고 표시합니다(`about_model`, `about_coeff`). 내일 몫 설정 화면은 "겨울엔 더 많이 들어요. 여유분 20%를 더해 남겨 둬요 (추정)"이라고 씁니다(`s_margin`).
- README '테스트 환경'과 `docs/ENERGY_MODEL.md` §1·§6은 "아주 추운 날, 처음 데우는 동안, 직사광선 아래에서는 실제 소모가 더 클 수 있다"고 적습니다. 이 대조는 그 문장의 크기를 숫자로 보탭니다: **영하 18°C에서 1.15~1.26배, 배터리 19~20%에서 약 18~32분 길게 말합니다.**
- 화면에 "더 추운 날은 더 쓸 수 있다"는 문장 자체는 없습니다(APK 9ab0d0c3 동결). 2차에서 넣을 후보입니다.
- 내일 몫 여유분(×1.2)은 내일 갈 km를 지키는 장치입니다. 이 차이를 덮는다고 말하지 않습니다. 이렇게 추우면 내일 몫 자체도 더 듭니다.

## 4. 결론

1. **외기 −5 ~ −10°C 부근에서는 대부분 앱이 더 많이 쓴다고 계산합니다.** ADAC 2022(A, 7종), DOE BEV 1(A), B등급 4건(N1·N2·N4·N5), C등급 2건(O1·O2)이 그렇습니다(15대 중 14대). 예외는 DOE BEV 2(−6.7°C, A)로, 앱이 약 14% 적게 잡습니다.
2. **영하 18°C에서는 기관 시험 2대 모두 실제가 더 씁니다.** 앱의 남은 시간은 1.15~1.26배, 배터리 19~20%에서 약 18~32분 깁니다(§3).
3. **차가운 차를 처음 데우는 25분(H2), 폭염 직사광선 + 배터리 냉각(C1)도 실제가 더 클 수 있습니다.** C1은 순간값 1건이라 크기를 모릅니다.
4. **냉방(DOE 35°C, ADAC 2024 35°C + 일사), 공조 꺼짐, 온화한 날은 앱이 더 많이 씁니다.** 온화한 날은 2~6배 과대입니다. 외기온으로 남은 시간을 늘리지 않기 때문입니다(짧게 틀리는 쪽).
5. 현대차그룹 차종의 정차 실측은 코나(A, ADAC 2022 7종 중 1대), 코나·레이 EV(C), 아이오닉 5 N(C), EV9·아이오닉 9(B)뿐이고, EV6·아이오닉 5의 영하 10°C 이하 정차 실측은 찾지 못했습니다.

## 5. 2차에서 할 것 (APK 동결이라 지금은 하지 않음)

- 외기가 −10°C보다 추울 때의 난방 기울기를 2.3/32 = 0.072 kW/°C에서 DOE BEV 2 기준 약 0.093 kW/°C(3.732 kW ÷ (22.2 + 17.8))로 올리는 안. −10°C보다 추울 때만 써야 온화한 날 과대가 커지지 않습니다.
- 화면에 "아주 추운 날은 실제 소모가 더 클 수 있어요" 한 줄.
- 실차가 생기면 볼 항목: 정차 중 SOC 감소 추세(1% 해상도라 1시간 이상), 외기 −15°C 이하 하룻밤, 데우는 첫 30분.

## 6. 출처와 쓰지 않은 것

- DOE·ANL Program Record "Impact of Cold Ambient Temperature and Extreme Conditions on Electric Vehicles" (2024-09-12, Table 2): [PDF](https://www.energy.gov/sites/default/files/2024-10/Impact_of_Cold_Ambient_Temperature_on_BEV_Performance_v15_TechEditFinal_12Sep2024__0.pdf)
- 기존 9건과 등급 규칙: `docs/ENERGY_MODEL.md` §6.
- 쓰지 않은 것: Rivian R1T·F-150 Lightning 눈 속 정차(복귀 충전량이라 충전 손실 포함 상한), 아이오닉 5 0.74 kW·EV6 1.5 kW 전언(원 영상 미확인), 현대 아이오닉 5 차박(부하 혼합), Tesla 시트 열선 57 Wh(단위 시간 불명확, 정성 근거로만).
