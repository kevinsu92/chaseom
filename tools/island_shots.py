"""Screenshot every round-1 island state (04_design state IDs S0..S5, S11) through the Virtual Console.

    python tools/island_shots.py <out_dir> [--package app.chaseom]
"""
import argparse
import time

import vc

STEPS = [
    ("S2_26c_fan7", dict(temp=26, fan=7)),
    ("S3_21c_heat2", dict(temp=21, fan=3, seat=2)),
    ("S4_heat3_passenger1", dict(seat=3, seat_passenger=1)),
    ("S5_ac_recirc", dict(seat=0, seat_passenger=0, ac=True, recirc=True)),
    ("S1_climate_off", dict(ac=False, recirc=False, power=False)),
    ("S11_below_keep", dict(power=True, soc=8)),
    ("cap_soc62", dict(soc=62)),
]


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("out")
    ap.add_argument("--package")
    a = ap.parse_args()
    vc.connect()
    pkg = vc.installed_package(a.package)
    vc.base_state(soc=15)
    vc.set_car(fan=3)
    vc.adb("shell", "am", "start", "-n", vc.launch_component(pkg))
    time.sleep(14)
    vc.shot(a.out, "S0_22c_fan3")
    for name, car in STEPS:
        vc.set_car(**car)
        time.sleep(5)
        vc.shot(a.out, name)
        print("shot", name)
    vc.base_state(soc=15)
    vc.disconnect()


if __name__ == "__main__":
    main()
