"""Build G S1/S2 island captures: Large and Small windows, day and night, without a flag, with a flag and at K1.
Usage: python tools/s1_shots.py <out_dir> [package]
Uses the Virtual Console only (tools/vc.py). Night = `cmd uimode night yes`, always restored to `no` at the end."""
import re
import sys
import time

import vc

PKG = sys.argv[2] if len(sys.argv) > 2 else "app.chaseom.debug"


def tap_first(pattern, timeout=5.0):
    end = time.time() + timeout
    while time.time() < end:
        m = re.search(r'text="(' + pattern + r')"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', vc.screen_xml())
        if m:
            x1, y1, x2, y2 = map(int, m.groups()[1:])
            vc.adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            return m.group(1)
        time.sleep(0.5)
    return None


def scene(out, tag):
    vc.base_state(soc=19)
    time.sleep(1)
    vc.set_car(temp=26, fan=7)
    time.sleep(4)
    vc.tap_text("깃발 내리기", timeout=1)
    time.sleep(1.5)
    vc.shot(out, f"{tag}_a_noflag")
    if vc.tap_text("몇 시까지 기다려요? (선택)", timeout=3):
        time.sleep(1.2)
        # the longest choice, so the flag stands past the last buoy (short case)
        picked = None
        for _ in range(6):
            m = re.findall(r'text="([^"]*까지 \(\+[^"]*\))"', vc.screen_xml())
            if m:
                picked = m[-1]
                break
            time.sleep(0.5)
        if picked:
            vc.tap_text(picked)
    time.sleep(2.5)
    vc.shot(out, f"{tag}_b_flag")
    vc.set_car(temp=21, fan=3, seat=2)
    time.sleep(3)
    vc.shot(out, f"{tag}_c_flag_after_swap")
    vc.set_car(seat=0, temp=26, fan=7, soc=9)
    time.sleep(4)
    vc.shot(out, f"{tag}_d_k1")
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
