"""Tightest island state (K1 banner + flag short line + swap card) at L and S, plus the (i) fold-out line.
Usage: python tools/s1_tight.py <out_dir> [package]"""
import sys
import time

import vc
from s1_shots import plant

PKG = sys.argv[2] if len(sys.argv) > 2 else "app.chaseom.debug"


def tight(out, tag):
    for soc in (13, 12, 14, 11):
        vc.set_car(temp=26, fan=7, seat=0, soc=soc)
        time.sleep(4)
        if vc.screen_has("곧 내일 몫에 닿아요"):
            break
    plant(-1)
    vc.set_car(temp=21, fan=3, seat=2)
    time.sleep(4)
    vc.shot(out, f"{tag}_7_k1_flag_card")
    vc.tap_text("섬은 공조 설정·배터리로 그려요 · 실내 측정 아님", timeout=2)
    time.sleep(1.5)
    vc.shot(out, f"{tag}_8_fold_open")
    vc.tap_text("섬은 공조 설정·배터리로 그려요 · 실내 측정 아님", timeout=2)
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
