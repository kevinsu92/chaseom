"""Signal probe: drive every Virtual Console card the app reads and report how fast each vehicle
callback reaches the app (PROBE lines = SDK callback wall clock). This is how the platform facts in
docs/PLATFORM_NOTES were found (e.g. HVAC callbacks need the capability area, backrest writes do
not reach the emulator's vehicle HAL, rear occupancy is inverted on the emulator).

    python tools/probe_signals.py [out_dir] [package]
"""
import json
import statistics
import sys
import time
import urllib.request

import vc
from vc import VC, adb, post


def write(name, path):
    t = time.time() * 1000
    resp = post(path).decode("utf-8", "replace")
    return {"name": name, "path": path, "host_ms": t, "resp": resp[:120]}


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else "."
    vc.connect()
    pkg = vc.installed_package(sys.argv[2] if len(sys.argv) > 2 else None)
    print("availability:", json.loads(urllib.request.urlopen(VC + "/vehicle/state").read())["availability"]["available"])

    adb("shell", "am", "force-stop", pkg)
    adb("logcat", "-c")
    adb("shell", "am", "start", "-n", vc.launch_component(pkg))
    time.sleep(6)

    offset = vc.clock_offset_ms()
    print(f"clock offset device-host = {offset:.1f} ms")

    steps = []
    # Baseline known state
    for p in ["/vehicle/gear?gear=Park", "/vehicle/hvac?power=true", "/vehicle/hvac?driver_temp=22",
              "/vehicle/hvac?fan_speed=3", "/vehicle/hvac?recirc=false", "/vehicle/ev-battery?field=charge_port_open&value=false", "/vehicle/seat?area=driver&kind=position&control=recline&value=60",
              "/vehicle/seat?area=passenger&kind=position&control=recline&value=60"]:
        post(p)
    time.sleep(2)

    # HVAC latency series (10 x temperature, 10 x fan)
    for i in range(10):
        steps.append(write("hvac.temp", f"/vehicle/hvac?driver_temp={22 + (i % 2) * 4}"))
        time.sleep(1.2)
    for i in range(10):
        steps.append(write("hvac.fan", f"/vehicle/hvac?fan_speed={3 + (i % 2) * 3}"))
        time.sleep(1.2)
    steps.append(write("hvac.power_off", "/vehicle/hvac?power=false")); time.sleep(1.2)
    steps.append(write("hvac.power_on", "/vehicle/hvac?power=true")); time.sleep(1.2)
    steps.append(write("hvac.ac_on", "/vehicle/hvac?ac=true")); time.sleep(1.2)
    steps.append(write("hvac.recirc_on", "/vehicle/hvac?recirc=true")); time.sleep(1.2)
    steps.append(write("seatheat.driver2", "/vehicle/hvac?driver_seat=2")); time.sleep(1.2)
    # Backrest direction: lower value
    steps.append(write("backrest.driver30", "/vehicle/seat?area=driver&kind=position&control=recline&value=30")); time.sleep(1.2)
    steps.append(write("backrest.passenger20", "/vehicle/seat?area=passenger&kind=position&control=recline&value=20")); time.sleep(1.2)
    # Rear occupancy
    steps.append(write("occ.rear_left_on", "/vehicle/seat?area=rear_left&kind=occupied&value=true")); time.sleep(1.2)
    steps.append(write("occ.rear_right_on", "/vehicle/seat?area=rear_right&kind=occupied&value=true")); time.sleep(1.2)
    steps.append(write("occ.rear_left_off", "/vehicle/seat?area=rear_left&kind=occupied&value=false")); time.sleep(1.2)
    # Body
    steps.append(write("window.fl30", "/vehicle/body?window_fl=30")); time.sleep(1.2)
    steps.append(write("door.fl_open", "/vehicle/body?door_fl=true")); time.sleep(1.2)
    steps.append(write("door.rl_open", "/vehicle/body?door_rl=true")); time.sleep(1.2)
    steps.append(write("trunk.open", "/vehicle/body?trunk=true")); time.sleep(1.2)
    steps.append(write("light.cabin_on", "/vehicle/body?cabin_light=true")); time.sleep(2.2)
    steps.append(write("light.reading_fl_on", "/vehicle/reading-lights?area=front_left&value=true")); time.sleep(2.2)
    # Energy
    steps.append(write("soc.62", "/vehicle/ev-battery?field=soc&value=62")); time.sleep(1.2)
    steps.append(write("chargeport.open", "/vehicle/ev-battery?field=charge_port_open&value=true")); time.sleep(1.2)
    # Gear
    steps.append(write("gear.drive", "/vehicle/gear?gear=Drive")); time.sleep(1.5)
    steps.append(write("gear.park", "/vehicle/gear?gear=Park")); time.sleep(1.5)

    # Restore a neutral demo state
    for p in ["/vehicle/body?door_fl=false", "/vehicle/body?door_rl=false", "/vehicle/body?trunk=false",
              "/vehicle/body?window_fl=0", "/vehicle/body?cabin_light=false",
              "/vehicle/reading-lights?area=front_left&value=false", "/vehicle/ev-battery?field=charge_port_open&value=false",
              "/vehicle/seat?area=rear_right&kind=occupied&value=false", "/vehicle/hvac?recirc=false", "/vehicle/hvac?driver_seat=0",
              "/vehicle/seat?area=driver&kind=position&control=recline&value=60",
              "/vehicle/seat?area=passenger&kind=position&control=recline&value=60"]:
        post(p)
    time.sleep(2)
    vc.disconnect()

    log = adb("logcat", "-d", "-v", "epoch", "-s", "PROBE:*")
    events = []
    for line in log.splitlines():
        parts = line.split()
        if len(parts) < 6 or "PROBE" not in line:
            continue
        try:
            ts = float(parts[0]) * 1000
        except ValueError:
            continue
        msg = line.split("PROBE", 1)[1].lstrip(": ").strip()
        events.append((ts - offset, msg))  # convert to host clock

    key_for = {"hvac.temp": "tempSet=", "hvac.fan": "fan=", "hvac.power_off": "hvacPower=false",
               "hvac.power_on": "hvacPower=true", "hvac.ac_on": "ac=true", "hvac.recirc_on": "recirc=true",
               "seatheat.driver2": "seatHeat=2", "backrest.driver30": "backrestDriver",
               "backrest.passenger20": "backrestPassenger", "occ.rear_left_on": "occRearLeft=",
               "occ.rear_right_on": "occRearRight=", "occ.rear_left_off": "occRearLeft=",
               "window.fl30": "window.driver=", "door.fl_open": "door.driver=", "door.rl_open": "door.rearLeft=",
               "trunk.open": "door.trunk", "light.cabin_on": "cabinLight=true", "light.reading_fl_on": "readingLight=",
               "soc.62": "soc=62", "chargeport.open": "chargePort=", "gear.drive": "gear=", "gear.park": "gear="}
    results = []
    for s in steps:
        key = key_for[s["name"]]
        hit = next(((t, m) for t, m in events if t >= s["host_ms"] - 50 and m.startswith(key)), None)
        lat = round(hit[0] - s["host_ms"], 1) if hit and hit[0] - s["host_ms"] < 3000 else None
        results.append({**s, "event": hit[1] if hit and lat is not None else None, "latency_ms": lat})
        print(f'{s["name"]:22s} {str(lat):>8s} ms  {results[-1]["event"]}')

    for k in ("hvac.temp", "hvac.fan"):
        lats = [r["latency_ms"] for r in results if r["name"] == k and r["latency_ms"] is not None]
        if lats:
            print(f"{k}: n={len(lats)} mean={statistics.mean(lats):.1f} median={statistics.median(lats):.1f} max={max(lats):.1f}")

    initial = [m for _, m in events if m.split("=")[0] in ("fuelType", "capacity", "efficiency", "backrestCaps", "aaosGear", "soc")
               or "FAILED" in m]
    with open(f"{out_dir}/d1_probe_result.json", "w", encoding="utf-8") as f:
        json.dump({"clock_offset_ms": offset, "steps": results, "initial_and_failures": initial,
                   "raw_events": [m for _, m in events]}, f, ensure_ascii=False, indent=1)
    print("initial/failures:")
    for m in dict.fromkeys(initial):
        print("  ", m)


if __name__ == "__main__":
    main()
