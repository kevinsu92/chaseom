"""Edge checks for the restored-stay, charging and connecting states (05_qa B_GATE 3, E4-1/E4-3/E4-5).

    python tools/edges.py <screenshot_dir> [--package app.chaseom]

Only the Virtual Console and normal app start/stop are used (no clock change, no fake values).
Takes about 3 minutes: the receipt needs a real stay of at least one minute.
"""
import argparse
import time

import vc


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("out")
    ap.add_argument("--package")
    a = ap.parse_args()
    vc.connect()
    pkg = vc.installed_package(a.package)
    comp = vc.launch_component(pkg)
    res = {}

    # E4-5: right after a cold start, before the first gear value, the app says "connecting".
    vc.base_state(soc=30)
    vc.adb("shell", "am", "force-stop", pkg)
    time.sleep(1)
    vc.adb("shell", "am", "start", "-n", comp)
    seen = False
    for _ in range(6):
        if vc.screen_has("차와 연결하는 중이에요"):
            seen = True
            vc.shot(a.out, "E4-5_connecting")
            break
        time.sleep(0.2)
    res["E4-5 connecting state seen"] = seen
    time.sleep(6)

    # E4-1: stay, app killed, battery charged +10 %p while away -> a new stay.
    vc.set_car(gear="Drive")
    time.sleep(1)
    vc.set_car(gear="Park")
    time.sleep(70)
    vc.adb("shell", "am", "force-stop", pkg)
    vc.set_car(soc=40)
    time.sleep(2)
    vc.adb("shell", "am", "start", "-n", comp)
    time.sleep(6)
    vc.shot(a.out, "E4-1_restart_after_charge")
    res["E4-1 new stay after charge"] = vc.screen_has("새로 시작했어요")

    # E4-1b: killed and back at once with the same battery -> the same stay continues.
    time.sleep(65)
    vc.adb("shell", "am", "force-stop", pkg)
    time.sleep(2)
    vc.adb("shell", "am", "start", "-n", comp)
    time.sleep(6)
    vc.shot(a.out, "E4-1b_restart_within_10min")
    res["E4-1b stay continues"] = vc.screen_has("분째")

    # E4-3: battery rose during the stay -> the receipt says "charging", never 0.0kWh.
    vc.set_car(soc=45)
    time.sleep(2)
    vc.set_car(door_driver=True)
    time.sleep(1.5)
    vc.shot(a.out, "E4-3_receipt_charging")
    res["E4-3 charging line"] = vc.screen_has("충전 중이라 계산하지 않았어요")
    res["E4-3 no 0.0kWh"] = not vc.screen_has("0.0kWh")
    vc.set_car(door_driver=False)

    vc.base_state(soc=20)
    vc.disconnect()
    for k, v in res.items():
        print(("OK " if v else "NG ") + k)


if __name__ == "__main__":
    main()
