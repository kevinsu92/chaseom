"""Tightest island state (K1 banner + flag short line + swap card) at L and S, plus the (i) fold-out line.
This branch has no outside-temperature input (heating is fixed at -10 C outside), so the VC outside temperature does
not matter here; on a build with ENV_OUTSIDE_TEMPERATURE set it to -10 C first (G5).
Usage: python tools/s1_tight.py <out_dir> [package]"""
import sys
import time

import vc
from s1_shots import plant

PKG = sys.argv[2] if len(sys.argv) > 2 else "app.chaseom.debug"


def tight(out, tag):
    # K1 and a swap card together (the card needs a gain of 10 min or more, so a higher SOC inside K1 helps).
    for soc in (16, 15, 14, 13, 12, 11):
        vc.set_car(temp=26, fan=7, seat=0, soc=soc)
        time.sleep(4)
        xml = vc.screen_xml()
        print(soc, "K1" if "곧 내일 몫에 닿아요" in xml else "-", "card" if "닫기" in xml else "-")
        if "곧 내일 몫에 닿아요" in xml and "닫기" in xml:
            break
    plant(-1)
    # Stay on 26 C / fan 7: the swap card proposes 21 C + seat heat 2, so switching to it would close the card.
    time.sleep(3)
    vc.shot(out, f"{tag}_7_k1_flag_card")
    vc.tap_text("섬은 공조 설정과 머물 시간으로 그려요 · 실내 측정 아님", timeout=2)
    time.sleep(1.5)
    vc.shot(out, f"{tag}_8_fold_open")
    vc.tap_text("섬은 공조 설정과 머물 시간으로 그려요 · 실내 측정 아님", timeout=2)
    vc.tap_text("깃발 내리기", timeout=1)


def main():
    out = sys.argv[1]
    vc.require_device()
    vc.connect()
    try:
        vc.adb("shell", "cmd", "uimode", "night", "no")
        vc.base_state(soc=19)
        vc.adb("shell", "am", "start", "-n", f"{PKG}/app.chaseom.MainActivity")
        time.sleep(5)
        if vc.tap_text("내일 몫 정하기", timeout=3):  # first run after install: take the default 30 km
            time.sleep(1.5); vc.tap_text("저장"); time.sleep(2)
        vc.tap_text("알겠어요", timeout=2)
        tight(out, "L_day")
        with vc.small_window(PKG):
            tight(out, "S_day")
    finally:
        vc.adb("shell", "cmd", "uimode", "night", "no")
        vc.base_state(soc=19)
        vc.disconnect()


if __name__ == "__main__":
    main()
