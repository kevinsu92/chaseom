#!/usr/bin/env python3
"""Compare Chaseom's energy coefficients with public parked-EV measurements.

No emulator, no Android SDK, no third-party packages. Python 3.9+.

    python tools/compare_public.py            # demo numbers + comparison table to the terminal
    python tools/compare_public.py --write    # also rewrite docs/PUBLIC_COMPARISON.md
    python tools/compare_public.py --check    # exit 1 if docs/PUBLIC_COMPARISON.md is not what this script makes

The draw model below is a line-by-line port of app/src/main/java/app/chaseom/stay/EnergyModel.kt
(build G, APK 9ab0d0c3). Kotlin does the arithmetic in 32-bit Float, so every operation here is
rounded to float32 the same way; the demo minutes then match the JVM unit tests exactly
(OutsideTempGapTest: 167 / 146 / 162 at SOC 19 %).

Measurements come from docs/ENERGY_MODEL.md section 6 (05_qa VERIFIED set) and the trend lab's
REALCAR_PROXY (2026-11-13), mainly the DOE/ANL 2024 Program Record, Table 2. Each row keeps its
source link, grade and how the value was checked.
"""
from __future__ import annotations

import argparse
import math
import struct
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import List, Optional, Tuple

ROOT = Path(__file__).resolve().parent.parent
OUT_MD = ROOT / "docs" / "PUBLIC_COMPARISON.md"


def f32(x: float) -> float:
    """Round to IEEE-754 single precision, like a Kotlin Float."""
    return struct.unpack("f", struct.pack("f", x))[0]


# --- EnergyModel.kt constants (build G) -------------------------------------------------------
BASE_KW = f32(0.30)
HEAT_KW_AT_22C = f32(2.3)
HEAT_OUTSIDE_C = f32(-10.0)
HEAT_REFERENCE_DELTA = f32(32.0)
OUTSIDE_MIN_C, OUTSIDE_MAX_C = -40.0, 60.0
AC_KW = f32(2.0)
RECIRC_HEAT_FACTOR = f32(0.76)
FAN_KW_PER_STEP = f32(0.03)
SEAT_HEAT_KW = [0.0, f32(0.049), f32(0.066), f32(0.100)]
DEFAULT_KM_PER_KWH = f32(5.0)
KEEP_FACTOR = f32(1.2)
DISPLAY_CAP_MINUTES = 180


@dataclass(frozen=True)
class Climate:
    power_on: bool = True
    set_c: Optional[float] = 22.0
    fan: int = 3
    ac: bool = False
    recirc: bool = False
    seat: int = 0
    outside_c: Optional[float] = None  # None = the car did not report it


def heat_outside_c(outside_c: Optional[float]) -> float:
    valid = None
    if outside_c is not None and not math.isnan(outside_c) and OUTSIDE_MIN_C <= outside_c <= OUTSIDE_MAX_C:
        valid = f32(outside_c)
    return min(valid if valid is not None else HEAT_OUTSIDE_C, HEAT_OUTSIDE_C)


def draw_kw(s: Climate) -> float:
    seat = SEAT_HEAT_KW[max(0, min(3, s.seat))]
    if not s.power_on:
        return f32(BASE_KW + seat)
    fan = f32(max(0, min(8, s.fan)) * FAN_KW_PER_STEP)
    if s.ac:
        cabin = AC_KW
    else:
        t = f32(s.set_c if s.set_c is not None else 22.0)
        heat = f32(f32(HEAT_KW_AT_22C * f32(t - heat_outside_c(s.outside_c))) / HEAT_REFERENCE_DELTA)
        heat = max(heat, 0.0)
        cabin = f32(heat * RECIRC_HEAT_FACTOR) if s.recirc else heat
    return f32(f32(f32(BASE_KW + cabin) + fan) + seat)


def keep_percent(keep_km: int, capacity_kwh: float, km_per_kwh: float = DEFAULT_KM_PER_KWH) -> float:
    v = f32(f32(f32(f32(keep_km / f32(km_per_kwh)) * KEEP_FACTOR) / f32(capacity_kwh)) * f32(100.0))
    return min(max(v, 0.0), 100.0)


def minutes_to_keep(soc: int, keep: float, capacity_kwh: float, kw: float) -> int:
    avail = f32(f32(f32(soc - keep) / f32(100.0)) * f32(capacity_kwh))
    if avail <= 0 or kw <= 0:
        return 0
    return int(math.floor(f32(f32(avail / kw) * f32(60.0))))


def signed(n: int) -> str:
    """+16 / −21 with a real minus sign, as on the app screen."""
    return f"+{n}" if n > 0 else f"−{-n}" if n < 0 else "0"


def hm(minutes: int) -> str:
    return f"{minutes // 60}시간 {minutes % 60}분"


# --- Demo scene (README "심사위원 3분 재현", video v31) -----------------------------------------
CAP_KWH = 77.4
KEEP_KM = 30
DEMO_SOC = 19


def demo_numbers(outside_c: Optional[float] = None) -> Tuple[int, int, int]:
    keep = keep_percent(KEEP_KM, CAP_KWH)
    m = lambda c: min(minutes_to_keep(DEMO_SOC, keep, CAP_KWH, draw_kw(c)), DISPLAY_CAP_MINUTES)
    base = m(Climate(set_c=22, fan=3, outside_c=outside_c))
    hot = m(Climate(set_c=26, fan=6, outside_c=outside_c))
    swap = m(Climate(set_c=21, fan=6, seat=2, outside_c=outside_c))
    return base, hot, swap


# --- Public measurements ----------------------------------------------------------------------
@dataclass
class Row:
    id: str
    label: str
    grade: str          # A institution test / B press or measured tool / C one owner
    checked: str        # 원문 = opened the original; 에이전트 = web agent read the article; QA = 05_qa VERIFIED set
    measured_kw: Tuple[float, ...]  # one value per vehicle, or (low, high) for a range
    app: Climate
    app_note: str
    source: str
    comparable: bool = True
    note: str = ""
    doe_usable_kwh: Tuple[float, ...] = field(default_factory=tuple)
    doe_hold_h: Tuple[float, ...] = field(default_factory=tuple)  # hold time printed in DOE Table 2


DOE = "https://www.energy.gov/sites/default/files/2024-10/Impact_of_Cold_Ambient_Temperature_on_BEV_Performance_v15_TechEditFinal_12Sep2024__0.pdf"
ADAC22 = "https://www.adac.de/rund-ums-fahrzeug/elektromobilitaet/tests/heizung-elektroauto/"
ADAC21 = "https://www.adac.de/rund-ums-fahrzeug/elektromobilitaet/info/haertetest-winter-stau/"
INL = "https://avt.inl.gov/sites/default/files/pdf/presentations/VSATTOctober201512VAuxilaryLoad.pdf"

ROWS: List[Row] = [
    # DOE / ANL 2024 Program Record, Table 2: stationary, cabin held at 72 F (22.2 C). kW = HVAC + other.
    Row("D1", "DOE·ANL 2024, 외기 0°F(−17.8°C), 실내 22.2°C 유지, BEV 1 / BEV 2", "A", "원문 p.11–12",
        (3.376 + 0.390, 3.732 + 0.390), Climate(set_c=22.2, outside_c=-17.8), "22.2°C·풍량 3, 외기 −17.8°C 반영", DOE,
        note="앱이 적게 잡는다", doe_usable_kwh=(54.5, 61.6), doe_hold_h=(14.5, 15.0)),
    Row("D2", "DOE·ANL 2024, 외기 20°F(−6.7°C), 실내 22.2°C 유지, BEV 1 / BEV 2", "A", "원문 p.11–12",
        (1.848 + 0.479, 2.898 + 0.253), Climate(set_c=22.2, outside_c=-6.7), "22.2°C·풍량 3 (−10°C보다 따뜻해 −10°C로 계산)", DOE,
        note="1대는 많이, 1대는 적게 잡는다", doe_usable_kwh=(57.1, 62.7)),
    Row("D3", "DOE·ANL 2024, 외기 95°F(35°C), 냉방, BEV 1 / BEV 2", "A", "원문 p.11–12",
        (0.956 + 0.544, 0.755 + 0.239), Climate(ac=True, set_c=22.2, outside_c=35), "A/C·풍량 3", DOE,
        doe_usable_kwh=(59.3, 64.2)),
    Row("D4", "DOE·ANL 2024, 외기 72°F(22.2°C), 공조 꺼짐, BEV 1 / BEV 2", "A", "원문 p.11–12",
        (0.226, 0.144), Climate(power_on=False), "공조 꺼짐 (기본 부하)", DOE),
    # docs/ENERGY_MODEL.md section 6 (PUBLIC_MEASUREMENTS v3, 05_qa VERIFIED)
    Row("H1", "ADAC 2022, 외기 −10°C, 실내 20°C 유지 (전기차 7종, 현대 코나 일렉트릭 포함)", "A", "QA",
        (1.5, 2.0), Climate(set_c=20, outside_c=-10), "20°C·풍량 3", ADAC22),
    Row("H3", "ADAC 2021 겨울 정체, −9 ~ −14°C, 22°C + 시트 열선 + 미등, 12시간 (Zoe / e-Up)", "A", "QA",
        (3.0, 2.2), Climate(set_c=22, seat=2, outside_c=-14), "22°C·풍량 3·열선 2단, 외기 −14°C 반영", ADAC21,
        note="외기온을 못 읽으면 앱 2.76 kW, Zoe 대비 0.92배. Zoe 값에는 미등 부하 포함"),
    Row("H2", "ADAC 2022, 코나 일렉트릭을 20°C까지 데우는 첫 약 25분", "A", "QA",
        (3.6,), Climate(set_c=20, outside_c=-10), "20°C·풍량 3", ADAC22,
        note="차가운 차를 처음 데우는 25분만 적게 잡는다"),
    Row("B1", "미국 INL, 액세서리 OFF 기본 부하", "A", "QA",
        (0.136, 0.258), Climate(power_on=False), "공조 꺼짐 (기본 부하)", INL),
    Row("A24", "ADAC 2024 여름 정체, Tesla Model Y, 챔버 35°C + 일사, 8시간", "A(보도)", "에이전트",
        (1.5,), Climate(ac=True, outside_c=35), "A/C·풍량 3",
        "https://insideevs.de/news/728377/elektroauto-autobahnstau-stromverbrauch-klimaanlage/", note="ADAC 원문 미대조"),
    Row("N1", "Tesla Model 3 히트펌프, 최저 −8°C, 21°C, 7.5시간", "B", "에이전트",
        (1.36,), Climate(set_c=21, outside_c=-8), "21°C·풍량 3",
        "https://fossbytes.com/guy-slept-2021-tesla-model-3-8c-check-battery-efficiency/"),
    Row("N2", "Tesla Model 3 (Car and Driver), 평균 약 −9.4°C, 18.3°C, 약 2일", "B", "에이전트",
        (1.65,), Climate(set_c=18.3, outside_c=-9.4), "18.3°C·풍량 3",
        "https://cleantechnica.com/2022/01/21/car-and-driver-does-its-own-tesla-cold-weather-tests/", note="용량 75 kWh 가정"),
    Row("N4", "VW ID.4 Pro S (제조사 시험), 약 −7°C, 22°C, 열선 1단", "B", "에이전트",
        (1.9,), Climate(set_c=22, seat=1, outside_c=-7), "22°C·풍량 3·열선 1단",
        "https://motorweek.org/this_just_in/volkswagen-tests-how-long-id-4-pro-s-ev-can-stay-warm-in-sub-freezing-tempe/",
        note="3시간 측정을 41시간으로 외삽한 제조사 값"),
    Row("N5", "Tesla Model 3 LR (EVsmart), 평균 −5°C, 20°C, 내기순환, 38시간", "B", "에이전트",
        (1.6,), Climate(set_c=20, recirc=True, outside_c=-5), "20°C·풍량 3·내기순환",
        "https://blog.evsmart.net/t/stuck-in-snow-storm-test-with-electric-vehicles-at-minus-five-degrees-en/2096", note="용량 가정"),
    Row("H4", "Tesla 캠프 모드, 추운 날 (2차 인용)", "B", "QA",
        (1.5, 1.9), Climate(set_c=22), "22°C·풍량 3",
        "https://electriccarscheme.com/blog/tesla-model-y-camp-mode-complete-ev-camping-guide-2025"),
    Row("K1", "기아 EV9, 한여름 밤 유틸리티 모드 냉방, 약 8시간", "B", "에이전트",
        (0.87,), Climate(ac=True), "A/C·풍량 3", "https://economist.co.kr/article/view/ecn202607240017"),
    Row("K2", "현대 아이오닉 9, 약 22°C, 차박 하룻밤", "B", "에이전트",
        (1.1,), Climate(set_c=22, outside_c=22), "22°C·풍량 3 (온화한 날도 −10°C로 계산)", "https://evcentral.com.au/?p=44552",
        note="인용 전 한 번 더 확인 권장"),
    Row("O1", "현대 코나 일렉트릭 (오너), 약 −3°C, 21°C, 4시간", "C", "에이전트",
        (1.12,), Climate(set_c=21, outside_c=-3), "21°C·풍량 3",
        "https://www.insideevsforum.com/community/threads/how-long-could-you-idle-your-kona-in-the-cold-and-stay-warm.12830/"),
    Row("O2", "기아 레이 EV (오너), 약 −3°C, 약 22.5°C, 10시간", "C", "에이전트",
        (1.20,), Climate(set_c=22.5, outside_c=-3), "22.5°C·풍량 3", "https://www.clien.net/service/board/cm_car/18419549"),
    Row("M1", "현대 아이오닉 5 N (오너), 온화한 날, 23°C, 약 7시간", "C", "QA",
        (0.48,), Climate(set_c=23, outside_c=15), "23°C·풍량 3 (온화한 날도 −10°C로 계산)", "https://brunch.co.kr/@8fda19a51618425/95"),
    Row("C2", "Rivian 캠프 모드 에어컨 (오너 보고)", "C", "QA",
        (1.0, 2.0), Climate(ac=True), "A/C·풍량 3",
        "https://rivianforums.com/forum/threads/power-consumption-during-camping-mode.62204/", note="고부하 3 kW 사례 있음"),
    Row("C1", "현대 아이오닉 5, 42°C 직사광선, 에어컨 + 배터리 냉각 (오너, 순간값)", "C", "QA",
        (3.48,), Climate(ac=True), "A/C·풍량 3", "https://www.clien.net/service/board/cm_car/19225608",
        comparable=False, note="순간값이라 평균과 비교할 수 없다. 폭염에서 적게 잡을 가능성(기관 시험 없음)"),
]

# Rows whose ratio is per vehicle (two vehicles) rather than a low-high range.
PER_VEHICLE = {"D1", "D2", "D3", "D4", "H3"}


def ratios(r: Row) -> Tuple[float, ...]:
    app = draw_kw(r.app)
    return tuple(app / m for m in r.measured_kw)


def verdict(r: Row) -> str:
    if not r.comparable:
        return "비교 불가"
    rs = ratios(r)
    if min(rs) >= 1.0:
        return "앱이 더 많이 씀"
    if max(rs) < 1.0:
        return "**실측이 더 큼**"
    return "**섞임**"


def fmt_kw(vals: Tuple[float, ...], per_vehicle: bool) -> str:
    if len(vals) == 1:
        return f"{vals[0]:.2f}"
    return " / ".join(f"{v:.2f}" for v in vals) if per_vehicle else f"{vals[0]:.2f}–{vals[1]:.2f}"


def fmt_ratio(r: Row) -> str:
    if not r.comparable:
        return "—"
    rs = ratios(r)
    if len(rs) == 1:
        return f"{rs[0]:.2f}"
    if r.id in PER_VEHICLE:
        return " / ".join(f"{x:.2f}" for x in rs)
    lo, hi = sorted(rs)
    return f"{lo:.2f}–{hi:.2f}"


def minutes_for_kw(kw: float, soc: int) -> int:
    return minutes_to_keep(soc, keep_percent(KEEP_KM, CAP_KWH), CAP_KWH, f32(kw))


# --- Output -----------------------------------------------------------------------------------
def terminal_report() -> str:
    b, h, s = demo_numbers()
    lines = [
        "== 시연 장면 (SOC 19%, 77.4 kWh, 내일 몫 30 km, 외기 못 읽음 = −10°C) ==",
        f"22°C·풍량 3            : {b}분 = 약 {hm(b)} (추정)",
        f"26°C·풍량 6 토스트     : {signed(h - b)}분  ({b} → {h})",
        f"21°C·풍량 6·열선 2단 카드: {signed(s - h)}분  ({h} → {s})",
        "",
        "== 공개 실측 대조 (앱 kW ÷ 실측 kW, 1보다 크면 앱이 더 많이 쓴다고 계산) ==",
    ]
    for r in ROWS:
        lines.append(f"{r.id:<4} {r.grade:<7} 실측 {fmt_kw(r.measured_kw, r.id in PER_VEHICLE):>11} kW | "
                     f"앱 {draw_kw(r.app):.2f} kW | 앱÷실측 {fmt_ratio(r):>11} | {verdict(r).replace('**', '')}")
    d1 = next(r for r in ROWS if r.id == "D1")
    app_kw = draw_kw(d1.app)
    lines += ["", "== 영하 18°C (DOE D1) 유지 시간: DOE 식 = 사용 가능 에너지 ÷ 전력 =="]
    for e, d in zip(d1.doe_usable_kwh, d1.doe_hold_h):
        lines.append(f"{e} kWh: 앱 {e / app_kw:.1f} h, DOE {d:.1f} h → 앱이 {e / app_kw / d:.2f}배 길게 말함")
    for soc in (19, 20):
        lines.append(f"SOC {soc}%: 앱 {minutes_for_kw(app_kw, soc)}분, 실측 전력 "
                     + " / ".join(f"{minutes_for_kw(m, soc)}분" for m in d1.measured_kw))
    return "\n".join(lines)


def markdown() -> str:
    b, h, s = demo_numbers()
    d1 = next(r for r in ROWS if r.id == "D1")
    app18 = draw_kw(d1.app)
    soc_rows, gaps = [], []
    for soc in (19, 20):
        a = minutes_for_kw(app18, soc)
        ms = [minutes_for_kw(m, soc) for m in d1.measured_kw]
        soc_rows.append(f"| {soc}% | {a}분 ({hm(a)}) | {ms[0]}분 / {ms[1]}분 | {a - ms[0]}분 / {a - ms[1]}분 |")
        gaps += [a - m for m in ms]
    hold_ratios = [(e / app18) / d for e, d in zip(d1.doe_usable_kwh, d1.doe_hold_h)]
    big = f"영하 18°C에서 {min(hold_ratios):.2f}~{max(hold_ratios):.2f}배, 배터리 19~20%에서 약 {min(gaps)}~{max(gaps)}분"
    hold = [f"| BEV {i + 1} | {e} kWh | {e / app18:.1f}시간 | {d:.1f}시간 | {e / app18 / d:.2f}배 |"
            for i, (e, d) in enumerate(zip(d1.doe_usable_kwh, d1.doe_hold_h))]
    b18, h18, s18 = demo_numbers(-17.8)

    out = [
        "# 공개 실측 대조표 — 실차 대신 같은 식으로 맞춰 보기",
        "",
        "> 이 파일은 `python tools/compare_public.py --write`가 만듭니다. 손으로 고치지 마세요. "
        "`python tools/compare_public.py --check`는 이 파일이 스크립트 출력과 같은지 확인합니다.",
        "",
        "차섬은 실차에서 재지 않았습니다. 대신 정차한 전기차의 공조 소비를 **조건과 함께 공개한 실측**을 모아, "
        "같은 조건을 앱 식(`app/src/main/java/app/chaseom/stay/EnergyModel.kt`, 빌드 G)에 넣고 차이를 냅니다. "
        "스크립트는 앱 식을 한 줄씩 옮겼고, Kotlin `Float`처럼 매 연산을 32비트로 반올림합니다. "
        "그래서 아래 시연 숫자는 JVM 단위 테스트 `OutsideTempGapTest`와 같습니다.",
        "",
        "## 1. 시연 숫자 (재현 확인)",
        "",
        "조건: 배터리 19%, 용량 77.4 kWh, 내일 몫 30 km(전비 5 km/kWh × 여유 1.2), 외기온 못 읽음(= −10°C로 계산).",
        "",
        "| 장면 | 앱 계산 | 단위 테스트 |",
        "|---|---|---|",
        f"| 22°C·풍량 3 | {b}분 = **약 {hm(b)} (추정)** | `OutsideTempGapTest.heroFollowsOutsideTemperature` (167) |",
        f"| 26°C·풍량 6으로 바꾼 토스트 | **{signed(h - b)}분** ({b} → {h}) | `OutsideTempGapTest.demoToastUnchangedAtMinus10` (−21) |",
        f"| 21°C + 운전석 열선 2단 카드 | **{signed(s - h)}분** ({h} → {s}) | `OutsideTempGapTest.emulatorReadingKeepsTheDemo` (16) |",
        "",
        "## 2. 대조표",
        "",
        "- 앱 값은 실측 조건에 가장 가까운 설정으로 계산했습니다(풍량은 공개되지 않아 3단 가정).",
        "- **앱 ÷ 실측이 1보다 크면** 앱이 더 많이 쓴다고 계산합니다. 남은 시간을 실제보다 짧게 말하는 쪽입니다. "
        "**1보다 작으면** 실제가 더 쓰고, 화면의 남은 시간이 실제보다 깁니다.",
        "- 등급: **A** 기관 시험(외기·설정·시간 공개), **B** 언론·측정 기록(조건 일부), **C** 오너 1명의 기록.",
        "- 확인: **원문** = 원문을 직접 열어 숫자 확인, **QA** = 05_qa가 원문 대조한 기존 9건(`docs/ENERGY_MODEL.md` §6), "
        "**에이전트** = 웹 조회로 본문 확인(원문 재대조 전).",
        "",
        "| # | 실측 | 등급 | 확인 | 실측 kW | 앱 설정 | 앱 kW | 앱 ÷ 실측 | 판정 |",
        "|---|---|---|---|---|---|---|---|---|",
    ]
    for r in ROWS:
        label = f"[{r.label}]({r.source})"
        if r.note:
            label += f" — {r.note}"
        out.append(f"| {r.id} | {label} | {r.grade} | {r.checked} | {fmt_kw(r.measured_kw, r.id in PER_VEHICLE)} | "
                   f"{r.app_note} | {draw_kw(r.app):.2f} | {fmt_ratio(r)} | {verdict(r)} |")
    out += [
        "",
        "DOE 행의 실측 kW는 원문 Table 2의 공조 전력과 기타 전력을 더한 값입니다(BEV 1 / BEV 2 순서, 차종은 비공개).",
        "",
        "## 3. 차이가 큰 곳: 영하 18°C는 실제가 더 씁니다",
        "",
        f"DOE·ANL 2024 시험의 외기 0°F(−17.8°C)에서는 두 대 모두 실측이 앱보다 큽니다. 빌드 G가 외기온을 읽어 "
        f"난방을 늘려도(앱 {app18:.2f} kW) 그렇습니다. 숨기지 않고 크기까지 적습니다.",
        "",
        "DOE는 유지 시간을 \"사용 가능 에너지 ÷ (공조 + 기타 전력)\"으로 계산했습니다. 차섬의 남은 시간 식과 같은 꼴이라, "
        "같은 에너지를 넣으면 앱이 몇 배 길게 말하는지 바로 나옵니다.",
        "",
        "| 차 | DOE 사용 가능 에너지 | 앱 식 (에너지 ÷ 앱 kW) | DOE 유지 시간 (원문) | 앱이 길게 말하는 비율 |",
        "|---|---|---|---|---|",
        *hold,
        "",
        "차섬 시연 차(77.4 kWh, 내일 몫 30 km)에 넣으면 이렇습니다(실측 전력은 BEV 1 / BEV 2).",
        "",
        "| 배터리 | 앱 화면 (22.2°C·풍량 3, 외기 −17.8°C를 읽은 경우) | 실측 전력으로 계산 | 앱이 길게 말하는 분 |",
        "|---|---|---|---|",
        *soc_rows,
        "",
        f"참고로 같은 외기에서 시연 설정(22°C·풍량 3, 19%)은 {b18}분 → 26°C·풍량 6 {h18}분({signed(h18 - b18)}분) → 열선 카드 {s18}분({signed(s18 - h18)}분)으로 계산됩니다.",
        "",
        "**앱과 문서가 이미 말하고 있는 것**",
        "- 화면의 계산 숫자에는 모두 \"(추정)\"이 붙습니다(`WordingRulesTest.computedValuesMarkedEstimate`).",
        "- 앱 정보 화면은 계수가 \"영하 10°C 정상상태 기준\"이며 외기온이 더 추울 때만 그 값을 쓴다고 표시합니다(`about_model`, `about_coeff`). "
        "내일 몫 설정 화면은 \"겨울엔 더 많이 들어요. 여유분 20%를 더해 남겨 둬요 (추정)\"이라고 씁니다(`s_margin`).",
        "- README '테스트 환경'과 `docs/ENERGY_MODEL.md` §1·§6은 \"아주 추운 날, 처음 데우는 동안, 직사광선 아래에서는 실제 소모가 더 클 수 있다\"고 적습니다. "
        f"이 대조는 그 문장의 크기를 숫자로 보탭니다: **{big} 길게 말합니다.**",
        "- 화면에 \"더 추운 날은 더 쓸 수 있다\"는 문장 자체는 없습니다(APK 9ab0d0c3 동결). 2차에서 넣을 후보입니다.",
        "- 내일 몫 여유분(×1.2)은 내일 갈 km를 지키는 장치입니다. 이 차이를 덮는다고 말하지 않습니다. 이렇게 추우면 내일 몫 자체도 더 듭니다.",
        "",
        "## 4. 결론",
        "",
        "1. **외기 −5 ~ −10°C 부근에서는 대부분 앱이 더 많이 쓴다고 계산합니다.** ADAC 2022(A, 7종), DOE BEV 1(A), "
        "B등급 4건(N1·N2·N4·N5), C등급 2건(O1·O2)이 그렇습니다(15대 중 14대). 예외는 DOE BEV 2(−6.7°C, A)로, 앱이 약 14% 적게 잡습니다.",
        f"2. **영하 18°C에서는 기관 시험 2대 모두 실제가 더 씁니다.** 앱의 남은 시간은 {big.replace('영하 18°C에서 ', '')} 깁니다(§3).",
        "3. **차가운 차를 처음 데우는 25분(H2), 폭염 직사광선 + 배터리 냉각(C1)도 실제가 더 클 수 있습니다.** C1은 순간값 1건이라 크기를 모릅니다.",
        "4. **냉방(DOE 35°C, ADAC 2024 35°C + 일사), 공조 꺼짐, 온화한 날은 앱이 더 많이 씁니다.** 온화한 날은 2~6배 과대입니다. "
        "외기온으로 남은 시간을 늘리지 않기 때문입니다(짧게 틀리는 쪽).",
        "5. 현대차그룹 차종의 정차 실측은 코나(A, ADAC 2022 7종 중 1대), 코나·레이 EV(C), 아이오닉 5 N(C), EV9·아이오닉 9(B)뿐이고, "
        "EV6·아이오닉 5의 영하 10°C 이하 정차 실측은 찾지 못했습니다.",
        "",
        "## 5. 2차에서 할 것 (APK 동결이라 지금은 하지 않음)",
        "",
        "- 외기가 −10°C보다 추울 때의 난방 기울기를 2.3/32 = 0.072 kW/°C에서 DOE BEV 2 기준 약 0.093 kW/°C"
        "(3.732 kW ÷ (22.2 + 17.8))로 올리는 안. −10°C보다 추울 때만 써야 온화한 날 과대가 커지지 않습니다.",
        "- 화면에 \"아주 추운 날은 실제 소모가 더 클 수 있어요\" 한 줄.",
        "- 실차가 생기면 볼 항목: 정차 중 SOC 감소 추세(1% 해상도라 1시간 이상), 외기 −15°C 이하 하룻밤, 데우는 첫 30분.",
        "",
        "## 6. 출처와 쓰지 않은 것",
        "",
        f"- DOE·ANL Program Record \"Impact of Cold Ambient Temperature and Extreme Conditions on Electric Vehicles\" (2024-09-12, Table 2): [PDF]({DOE})",
        "- 기존 9건과 등급 규칙: `docs/ENERGY_MODEL.md` §6.",
        "- 쓰지 않은 것: Rivian R1T·F-150 Lightning 눈 속 정차(복귀 충전량이라 충전 손실 포함 상한), 아이오닉 5 0.74 kW·EV6 1.5 kW 전언(원 영상 미확인), "
        "현대 아이오닉 5 차박(부하 혼합), Tesla 시트 열선 57 Wh(단위 시간 불명확, 정성 근거로만).",
        "",
    ]
    return "\n".join(out)


def main(argv: Optional[List[str]] = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--write", action="store_true", help="rewrite docs/PUBLIC_COMPARISON.md")
    ap.add_argument("--check", action="store_true", help="fail if docs/PUBLIC_COMPARISON.md differs from the script output")
    a = ap.parse_args(argv)
    try:
        sys.stdout.reconfigure(encoding="utf-8")  # Windows consoles
    except (AttributeError, ValueError):
        pass

    # Same table as OutsideTempGapTest.demoScenesByOutsideTemperature (JVM unit test).
    for o, want in {-20.0: (132, 118, 128), -15.0: (147, 131, 143), -10.0: (167, 146, 162),
                    0.0: (167, 146, 162), 25.0: (167, 146, 162), None: (167, 146, 162)}.items():
        got = demo_numbers(o)
        if got != want:
            print(f"FAIL: port drifted from EnergyModel.kt at outside={o}: {got} != {want}")
            return 1
    print(terminal_report())

    md = markdown()
    if a.write:
        OUT_MD.write_text(md, encoding="utf-8", newline="\n")
        print(f"\nwrote {OUT_MD.relative_to(ROOT)}")
    if a.check:
        cur = OUT_MD.read_text(encoding="utf-8") if OUT_MD.exists() else ""
        if cur.replace("\r\n", "\n") != md:
            print(f"\nFAIL: {OUT_MD.relative_to(ROOT)} differs from script output (run with --write)")
            return 1
        print(f"\nOK: {OUT_MD.relative_to(ROOT)} matches")
    return 0


if __name__ == "__main__":
    sys.exit(main())
