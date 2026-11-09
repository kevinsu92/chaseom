"""Record the island motion (HANDOFF 10): drive -> P (mooring) -> two dial changes (one bob each) -> D.

    python tools/motion_clip.py <out.mp4> [--package app.chaseom]

Uses `adb shell screenrecord` (about 15 s) while the Virtual Console plays the car. Also prints the
time from the gear-P write to the first mooring frame (PROBE moorFrame).
"""
import argparse
import os
import threading
import time

import vc


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("out")
    ap.add_argument("--package")
    a = ap.parse_args()
    vc.connect()
    pkg = vc.installed_package(a.package)
    vc.base_state(soc=19)  # 06_comms demo SOC
    vc.set_car(gear="Drive")
    vc.adb("shell", "am", "start", "-n", vc.launch_component(pkg))
    time.sleep(5)  # away from P for more than 3 s

    rec = threading.Thread(target=lambda: vc.adb("shell", "screenrecord", "--size", "1280x720", "--bit-rate", "6000000", "--time-limit", "15", "/sdcard/chaseom_motion.mp4"))
    rec.start()
    time.sleep(1.0)
    ms = vc.timed_write("moorFrame", {"gear": "Park"}, wait=3.5)
    print(f"gear P write -> first mooring frame: {ms} ms")
    vc.set_car(temp=26, fan=6)  # dial 1 (both within one 1.2 s toast batch): weather change, one bob after it
    time.sleep(3.5)
    vc.set_car(temp=21, seat=2)  # dial 2: the swap the card suggests
    time.sleep(3.5)
    vc.set_car(gear="Drive")  # leaving P: the island folds, nothing moves
    rec.join()
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    vc.adb("pull", "/sdcard/chaseom_motion.mp4", a.out)
    vc.adb("shell", "rm", "/sdcard/chaseom_motion.mp4")
    vc.base_state(soc=20)
    vc.disconnect()
    print("saved", a.out)


if __name__ == "__main__":
    main()
