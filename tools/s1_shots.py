"""Build G S1/S2 island captures for the 2-second check (UXR C1-C10): Large and Small windows, day and night.
States per window/theme: no flag, reach (first choice), short (last choice), swap card open, cap (6 buoys), K1.
Usage: python tools/s1_shots.py <out_dir> [package]
Uses the Virtual Console only (tools/vc.py). Night = `cmd uimode night yes`, always restored to `no` at the end."""
import re
import sys
import time

import vc

PKG = sys.argv[2] if len(sys.argv) > 2 else "app.chaseom.debug"


def choices():
    for _ in range(8):
        m = re.findall(r'text="([^"]*까지 \(\+[^"]*\))"', vc.screen_xml())
        if m:
            return m
        time.sleep(0.5)
    return []


def plant(which):
    """Plant a flag: which = 0 (earliest choice) or -1 (latest)."""
    vc.tap_text("깃발 내리기", timeout=1)
    time.sleep(1)
    if vc.tap_text("몇 시까지 기다려요? (선택)", timeout=3):
        time.sleep(1.2)
        c = choices()
        if c:
            vc.tap_text(c[which])
    time.sleep(2.5)


def scene(out, tag):
    vc.base_state(soc=19)
    vc.set_car(temp=26, fan=7)
    time.sleep(4)
    vc.tap_text("깃발 내리기", timeout=1)
    time.sleep(1.5)
    vc.shot(out, f"{tag}_1_noflag")
    plant(0)
    vc.shot(out, f"{tag}_2_reach")
    plant(-1)
    vc.shot(out, f"{tag}_3_short")
    vc.set_car(temp=21, fan=3, seat=2)  # swap: the stay grows, the card under the flag lines
    time.sleep(3)
    vc.shot(out, f"{tag}_4_swap")
    vc.set_car(seat=0, temp=22, fan=1, soc=95)  # 3 hours or more: six buoys
    time.sleep(4)
    plant(-1)
    vc.shot(out, f"{tag}_5_cap")
    vc.set_car(temp=26, fan=7, soc=9)  # near tomorrow's share: K1
    time.sleep(4)
    vc.shot(out, f"{tag}_6_k1")
    vc.tap_text("깃발 내리기", timeout=1)


def main():
    out = sys.argv[1]
    vc.require_device()
    vc.connect()
    try:
        vc.adb("shell", "cmd", "uimode", "night", "no")
        vc.base_state(soc=19)
        vc.adb("shell", "am", "start", "-n", f"{PKG}/app.chaseom.MainActivity")
        time.sleep(6)
        if vc.tap_text("내일 몫 정하기", timeout=2):
            time.sleep(1); vc.tap_text("저장"); time.sleep(2)
        vc.tap_text("알겠어요", timeout=2)
        for night in (False, True):
            vc.adb("shell", "cmd", "uimode", "night", "yes" if night else "no")
            time.sleep(3)
            vc.tap_text("알겠어요", timeout=2)
            dn = "night" if night else "day"
            scene(out, f"L_{dn}")
            with vc.small_window(PKG):
                scene(out, f"S_{dn}")
    finally:
        vc.adb("shell", "cmd", "uimode", "night", "no")
        vc.base_state(soc=19)
        vc.disconnect()


if __name__ == "__main__":
    main()
