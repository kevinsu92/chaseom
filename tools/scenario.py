"""Scenario run (safety banner, exchange card, keep banner, receipt, driving glance) with a screenshot per step.

    python tools/scenario.py <screenshot_dir> [--package app.chaseom] [--soc 15]

NOTE: this run starts from a clean app: it clears the app's data (`pm clear`), so the saved
tomorrow's distance and label are reset and the first-run screens appear. Vehicle values are
changed only through the emulator's Virtual Console (the app receives them on its real signal
path); no broadcast, intent extra, setprop or clock change is used.
"""
import argparse
import time

import vc
from vc import adb, post, screen_has, shot, tap_text


def main():
    ap = argparse.ArgumentParser(description="Scenario run with screenshots")
    ap.add_argument("out", help="screenshot folder")
    ap.add_argument("--package", help="default: release app.chaseom if installed")
    ap.add_argument("--soc", default="20")  # demo SOC: the exchange card needs about 20 %
    a = ap.parse_args()
    out, soc = a.out, a.soc
    vc.connect()
    pkg = vc.installed_package(a.package)

    # Known starting state
    for p in ["/vehicle/gear?gear=Drive", "/vehicle/hvac?power=true", "/vehicle/hvac?ac=false",
              "/vehicle/hvac?driver_temp=22", "/vehicle/hvac?fan_speed=3", "/vehicle/hvac?recirc=false",
              "/vehicle/hvac?driver_seat=0", f"/vehicle/ev-battery?field=soc&value={soc}",
              "/vehicle/body?door_fl=false", "/vehicle/body?window_fl=0", "/vehicle/body?cabin_light=false",
              "/vehicle/seat?area=rear_left&kind=occupied&value=false",
              "/vehicle/seat?area=rear_right&kind=occupied&value=false",
              "/vehicle/ev-battery?field=charge_port_open&value=false"]:
        post(p)
    adb("shell", "am", "force-stop", pkg)
    adb("shell", "pm", "clear", "--user", "current", pkg)  # AAOS apps run as user 10
    adb("shell", "am", "start", "-n", vc.launch_component(pkg))
    time.sleep(5)
    shot(out, "00_driving")

    post("/vehicle/gear?gear=Park"); time.sleep(1.5)
    shot(out, "01_onboarding")
    tap_text("내일 몫 정하기"); time.sleep(1)
    shot(out, "02_settings")
    tap_text("저장"); time.sleep(1.5)
    shot(out, "03_staying_banner")
    print("N0 banner:", screen_has("잠들지 마세요"))

    post("/vehicle/hvac?driver_temp=26"); time.sleep(0.15); post("/vehicle/hvac?fan_speed=6"); time.sleep(0.4)
    shot(out, "04_dial_toast")
    time.sleep(3.5)
    shot(out, "05_exchange_card")
    print("exchange card:", screen_has("운전석 열선 2단"))

    post("/vehicle/hvac?driver_temp=21"); post("/vehicle/hvac?driver_seat=2"); post("/vehicle/hvac?fan_speed=3")
    time.sleep(0.4)
    shot(out, "06_after_exchange_toast")

    post("/vehicle/ev-battery?field=soc&value=10"); time.sleep(1.5)
    shot(out, "07_keep_near")
    post("/vehicle/ev-battery?field=soc&value=8"); time.sleep(1.5)
    shot(out, "07b_keep_below")
    print("keep banner:", screen_has("공조를 꺼야 지켜집니다") or screen_has("내일 몫 아래로"))

    time.sleep(45)  # receipt needs >= 1 minute of stay
    post("/vehicle/body?cabin_light=true"); post("/vehicle/seat?area=rear_left&kind=occupied&value=true")
    time.sleep(1.5)
    post("/vehicle/body?door_fl=true"); time.sleep(1.5)
    shot(out, "08_receipt_rear_detected")
    print("receipt, rear detected:", screen_has("탑승 감지됨"), screen_has("뒷좌석 직접 확인"))
    post("/vehicle/body?door_fl=false"); time.sleep(1.2)
    print("receipt closes with door:", not screen_has("떠나기 전"))

    post("/vehicle/seat?area=rear_left&kind=occupied&value=false"); time.sleep(1.2)
    post("/vehicle/body?door_fl=true"); time.sleep(1.5)
    shot(out, "09_receipt_rear_not_detected")
    print("receipt, rear not detected:", screen_has("감지 안 됨 — 직접 확인하세요"), screen_has("뒷좌석 직접 확인"))
    post("/vehicle/body?door_fl=false"); time.sleep(1)

    t0 = time.time()
    post("/vehicle/gear?gear=Drive"); time.sleep(0.8)
    shot(out, "10_drive_collapse")
    print(f"driving shot {time.time() - t0:.2f}s after D, glance shown:", screen_has("주행 중에는 화면을 쉬어요"))

    # Restore neutral state
    for p in ["/vehicle/body?cabin_light=false", "/vehicle/ev-battery?field=soc&value=62", "/vehicle/hvac?driver_seat=0",
              "/vehicle/hvac?driver_temp=22", "/vehicle/gear?gear=Park"]:
        post(p)
    vc.disconnect()


if __name__ == "__main__":
    main()
