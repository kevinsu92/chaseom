"""차섬 30-second demo, hands-free.

    python tools/demo.py            # uses the installed app (release app.chaseom first, else debug)
    python tools/demo.py --package app.chaseom.debug

Drives the Pleos Connect emulator's Virtual Console like a person turning the car's own controls,
prints what the app shows at each step and how long the screen took to react (VC write -> first
frame, from the app's PROBE timing lines). Nothing inside the app is faked; the release build has
no demo mode.

Needs: Pleos Connect emulator running, Chaseom installed, adb (PATH, ADB or ANDROID_HOME), Python 3.9+.
The receipt step waits about one minute, because the app shows the receipt only after a real
stay of at least one minute.
"""
import argparse
import re
import sys
import time

import vc

HERO = re.compile(r"^(약 .+분|약 \d+시간|3시간 이상)$")
# Dial toast: "<what changed> · <−1시간 13분 | 3시간 이상 유지 (추정) | 변화 거의 없음>"
DELTA = re.compile(r"·.*([−+]\s?\d|유지 \(추정\)|변화 거의 없음)")


def hero_text():
    return next((t for t in vc.screen_texts() if HERO.match(t)), "?")


def toast_text():
    return next((t for t in vc.screen_texts() if DELTA.search(t)), None)


def step(n, title):
    print(f"\n[{n}/6] {title}")


def ok(cond, what):
    print(f"   {'OK ' if cond else 'NG '} {what}")
    return bool(cond)


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")  # Korean output on Windows consoles
    except Exception:  # noqa: BLE001
        pass
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--package", help="app.chaseom (release) or app.chaseom.debug")
    args = ap.parse_args()

    vc.connect()
    pkg = vc.installed_package(args.package)
    print(f"차섬 demo · package {pkg}")
    if pkg.endswith(".debug"):
        print("debug 빌드로 실행 중 (release app.chaseom이 설치돼 있지 않음)")
    results = []
    try:
        # A fresh stay: leave P first so the app starts a new session, then park.
        vc.set_car(gear="Drive")
        vc.base_state(soc=19)
        vc.set_car(gear="Drive")
        vc.adb("shell", "am", "start", "-n", vc.launch_component(pkg))
        time.sleep(4)

        step(1, "P에 세우고 배터리 19%, 공조 22°C · 풍량 3")
        vc.set_car(gear="Park")
        time.sleep(2)
        if vc.screen_has("내일 몫 정하기"):  # first run: onboarding -> settings (default 30 km) -> save
            vc.tap_text("내일 몫 정하기"); time.sleep(1)
            vc.tap_text("저장"); time.sleep(2)
        if vc.tap_text("알겠어요", timeout=2):  # night: the night notice opens each stay first
            print("   야간 고지 확인 (알겠어요)"); time.sleep(1.5)
        results.append(ok(vc.screen_has("머물 수 있어요"), f"머무는 화면 · 남은 시간 {hero_text()} (추정)"))
        ok(vc.screen_has("실내 측정 아님"), "작은 날씨 섬과 원인 칩")

        step(2, "히터 22→26°C, 풍량 3→6 (해가 커지고 바람이 붐)")
        ms = vc.timed_write("islandFrame", {"temp": 26}, {"fan": 6}, wait=0.8)
        t = toast_text()
        results.append(ok(ms is not None and t, f"섬 반영 {ms} ms · 토스트 \"{t}\" · 남은 시간 {hero_text()}"))
        time.sleep(3.5)

        step(3, "비슷한 체감 카드대로: 26→21°C + 운전석 열선 2단 (풍량 6 그대로, 해가 작아짐)")
        ms = vc.timed_write("islandFrame", {"temp": 21}, {"seat": 2}, wait=0.8)
        t = toast_text()
        results.append(ok(ms is not None and t and "+" in t,
                          f"섬 반영 {ms} ms · 토스트 \"{t}\" · 남은 시간 {hero_text()}"))

        step(4, "배터리 8% (내일 몫 아래: 빨간 경고 배너, 짐 상자 윤곽선)")
        vc.set_car(soc=8)
        time.sleep(1.5)
        results.append(ok(vc.screen_has("공조를 꺼야") or vc.screen_has("내일 몫 아래"), "내일 몫 경고 배너"))
        vc.set_car(soc=19)

        step(5, "운전석 문 열기 (떠나기 전 영수증)")
        print("   영수증은 1분 이상 머문 뒤에 나와요. 잠시 기다립니다…", flush=True)
        time.sleep(50)
        vc.set_car(door_driver=True)
        time.sleep(1.5)
        results.append(ok(vc.screen_has("떠나기 전") and vc.screen_has("뒷좌석 직접 확인"),
                          "영수증 · '뒷좌석 직접 확인' 줄"))
        vc.set_car(door_driver=False)
        time.sleep(1.2)

        step(6, "기어 D (섬과 숫자가 바로 접힘)")
        ms = vc.timed_write("islandGone", {"gear": "Drive"}, wait=1.0)
        results.append(ok(ms is not None, f"섬 사라짐 {ms} ms · 주행 중에는 시스템이 앱 화면을 가림"))
    finally:
        vc.base_state(soc=19)
        vc.disconnect()

    print(f"\n{sum(results)}/{len(results)} 장면 확인")
    sys.exit(0 if all(results) else 1)


if __name__ == "__main__":
    main()
