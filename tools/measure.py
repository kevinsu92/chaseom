"""Performance of a release APK on the emulator, written to JSON together with the APK's sha256.

    python tools/measure.py apk/Chaseom_Chaseom_release.apk docs/evidence/measure_release.json

What it measures (the numbers in README "검증 표"):
 1. toast first frame: VC climate write -> first frame showing the dial toast (PROBE toastFrame), 10 runs
    island first frame: VC climate write -> first frame after the island got the change (PROBE islandFrame), 10 runs
 2. fold: VC gear P -> D -> the island leaves the screen (PROBE islandGone), 10 runs (island builds only)
 3. app start: `am start -W` TotalTime after force-stop, 3 runs
 4. CPU: 30 s idle on the stay screen, and 60 s with one dial change every 10 s; PSS memory
CPU is % of one core as reported by `top` (the emulator has 4 cores). Times are host-clock
differences after correcting the host/emulator clock offset (best of 8 round trips, about +-20 ms).
The APK is installed with `adb install -r`; app data is kept (the first-run screens must already
be done, e.g. by tools/demo.py).
"""
import argparse
import datetime
import hashlib
import json
import os
import re
import statistics
import threading
import time

import vc

PKG = "app.chaseom"


def main():
    global PKG
    ap = argparse.ArgumentParser(description="Measure a release APK")
    ap.add_argument("apk")
    ap.add_argument("out_json")
    ap.add_argument("--shot", help="optional screenshot path of the stay screen")
    ap.add_argument("--package", default=PKG, help="app.chaseom (default)")
    a = ap.parse_args()

    PKG = a.package
    vc.connect()
    print(vc.adb("install", "-r", a.apk).strip().splitlines()[-1])
    vc.base_state(soc=15)
    comp = vc.launch_component(PKG)

    starts = []
    for _ in range(3):
        vc.adb("shell", "am", "force-stop", PKG)
        time.sleep(2)
        m = re.search(r"TotalTime:\s*(\d+)", vc.adb("shell", "am", "start", "-W", "-n", comp))
        starts.append(int(m.group(1)) if m else None)
        time.sleep(4)
    time.sleep(10)  # warning banner collapses, stay screen settles
    island = vc.screen_has("실내 측정 아님")  # the island cause chip (is_legend, island v2.5)
    if a.shot:
        vc.shot(os.path.dirname(a.shot) or ".", os.path.splitext(os.path.basename(a.shot))[0])

    toast = [vc.timed_write("toastFrame", {"fan": 7 if i % 2 == 0 else 3}, wait=5) for i in range(10)]
    island_frame = [vc.timed_write("islandFrame", {"temp": 26 if i % 2 == 0 else 22}, wait=5) for i in range(10)] if island else []

    pid = vc.adb("shell", "pidof", PKG).strip()
    time.sleep(5)
    idle = vc.cpu_samples(pid, 30)
    stop = threading.Event()

    def every10():
        v = 3
        while not stop.is_set():
            v = 7 if v == 3 else 3
            vc.set_car(fan=v)
            stop.wait(10)

    threading.Thread(target=every10, daemon=True).start()
    dial = vc.cpu_samples(pid, 60)
    stop.set()
    time.sleep(2)
    pss = vc.pss_mb(PKG)

    fold = []
    if island:
        for _ in range(10):
            vc.set_car(gear="Park")
            time.sleep(4)
            fold.append(vc.timed_write("islandGone", {"gear": "Drive"}, wait=2))
        vc.set_car(gear="Park")
    vc.base_state(soc=15)
    vc.disconnect()

    st = os.stat(a.apk)
    res = {
        "build_type": "release",
        "island_on_screen": island,
        "apk": os.path.basename(a.apk),
        "apk_sha256": hashlib.sha256(open(a.apk, "rb").read()).hexdigest(),
        "apk_bytes": st.st_size,
        "apk_built": datetime.datetime.fromtimestamp(st.st_mtime).isoformat(timespec="seconds"),
        "measured": datetime.datetime.now().isoformat(timespec="seconds"),
        "toast_frame_ms": toast, "toast": vc.summary(toast),
        "island_frame_ms": island_frame, "island_frame": vc.summary(island_frame),
        "fold_ms": fold, "fold": vc.summary(fold),
        "start_total_ms": starts,
        "cpu_idle_30s_avg": round(statistics.mean(idle), 2) if idle else None,
        "cpu_dial10s_60s_avg": round(statistics.mean(dial), 1) if dial else None,
        "cpu_dial10s_60s_max": max(dial) if dial else None,
        "pss_mb": pss,
        "note": "CPU = % of one core (emulator x86_64, 4 cores). Times from the app's PROBE lines "
                "(first frame after composition); host/emulator clock offset corrected (+-20 ms).",
    }
    print(json.dumps({k: v for k, v in res.items() if not isinstance(v, list)}, ensure_ascii=False))
    os.makedirs(os.path.dirname(a.out_json) or ".", exist_ok=True)
    with open(a.out_json, "w", encoding="utf-8") as f:
        json.dump(res, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
