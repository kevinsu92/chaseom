"""Shared helpers for the tools in this folder.

Talks to two things only:
  - the Pleos Connect emulator's Virtual Console HTTP API (forwarded to 127.0.0.1:8088), which plays
    the role of the car (gear, battery, climate, seats, doors, windows, lights);
  - adb, to start the app, read the screen (uiautomator) and read the app's PROBE timing lines.

The app itself is never given fake values: every change goes through the emulator's vehicle HAL,
exactly as a real car signal would.
"""
import os
import re
import shutil
import statistics
import subprocess
import threading
import time
import urllib.request
import uuid

VC = "http://127.0.0.1:8088"
PACKAGES = ("app.chaseom", "app.chaseom.debug")  # release first

_stop = threading.Event()
_client = "chaseom-tools-" + uuid.uuid4().hex[:8]


# ---- adb -----------------------------------------------------------------------------------------

def _adb_path():
    if os.environ.get("ADB"):
        return os.environ["ADB"]
    found = shutil.which("adb")
    if found:
        return found
    for root in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT"),
                 os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk"),
                 os.path.expanduser("~/Library/Android/sdk"), os.path.expanduser("~/Android/Sdk")):
        if root:
            cand = os.path.join(root, "platform-tools", "adb.exe" if os.name == "nt" else "adb")
            if os.path.exists(cand):
                return cand
    raise SystemExit("adb not found: put platform-tools on PATH or set ADB / ANDROID_HOME.")


ADB = None


def adb(*args, binary=False):
    global ADB
    ADB = ADB or _adb_path()
    r = subprocess.run([ADB, *args], capture_output=True)
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


def require_device():
    if "\tdevice" not in adb("devices"):
        raise SystemExit("No emulator found. Start the Pleos Connect emulator first (README section 3).")


def installed_package(preferred=None):
    pkgs = adb("shell", "pm", "list", "packages").split()
    for p in ([preferred] if preferred else []) + list(PACKAGES):
        if p and f"package:{p}" in pkgs:
            return p
    raise SystemExit("Chaseom is not installed: adb install -r apk/Chaseom_Chaseom_release.apk")


def launch_component(pkg):
    out = adb("shell", "cmd", "package", "resolve-activity", "--brief", pkg).strip().splitlines()
    return out[-1].strip() if out and "/" in out[-1] else f"{pkg}/.MainActivity"


# ---- Virtual Console -----------------------------------------------------------------------------

def post(path):
    req = urllib.request.Request(VC + path, method="POST", data=b"")
    with urllib.request.urlopen(req, timeout=5) as r:
        return r.read()


def _heartbeat():
    # The Virtual Console only accepts writes while a client session is alive.
    while not _stop.is_set():
        try:
            post(f"/browser/session?client={_client}")
        except Exception:  # noqa: BLE001
            pass
        time.sleep(1)


def connect():
    """Forward the VC port and keep a session open. Call once at start."""
    require_device()
    adb("forward", "tcp:8088", "tcp:8088")
    threading.Thread(target=_heartbeat, daemon=True).start()
    time.sleep(2.5)
    try:
        urllib.request.urlopen(VC + "/vehicle/state", timeout=5).read()
    except Exception as e:  # noqa: BLE001
        raise SystemExit(f"Virtual Console not reachable on {VC}: {e}")


def disconnect():
    _stop.set()


def set_car(**kw):
    """Convenience writes. Keys: gear, soc, power, temp, fan, ac, recirc, seat, seat_passenger,
    door_driver, window_driver, cabin_light, rear_left, rear_right, charge_port."""
    m = {
        "gear": lambda v: f"/vehicle/gear?gear={v}",
        "soc": lambda v: f"/vehicle/ev-battery?field=soc&value={v}",
        "power": lambda v: f"/vehicle/hvac?power={str(v).lower()}",
        "temp": lambda v: f"/vehicle/hvac?driver_temp={v}",
        "fan": lambda v: f"/vehicle/hvac?fan_speed={v}",
        "ac": lambda v: f"/vehicle/hvac?ac={str(v).lower()}",
        "recirc": lambda v: f"/vehicle/hvac?recirc={str(v).lower()}",
        "seat": lambda v: f"/vehicle/hvac?driver_seat={v}",
        "seat_passenger": lambda v: f"/vehicle/hvac?passenger_seat={v}",
        "door_driver": lambda v: f"/vehicle/body?door_fl={str(v).lower()}",
        "window_driver": lambda v: f"/vehicle/body?window_fl={v}",
        "cabin_light": lambda v: f"/vehicle/body?cabin_light={str(v).lower()}",
        "rear_left": lambda v: f"/vehicle/seat?area=rear_left&kind=occupied&value={str(v).lower()}",
        "rear_right": lambda v: f"/vehicle/seat?area=rear_right&kind=occupied&value={str(v).lower()}",
        "charge_port": lambda v: f"/vehicle/ev-battery?field=charge_port_open&value={str(v).lower()}",
    }
    for k, v in kw.items():
        post(m[k](v))


def base_state(soc=15):
    """The demo starting point: parked, climate on 22 C / fan 3, SOC 15 %, everything closed."""
    set_car(gear="Park", power=True, temp=22, fan=3, ac=False, recirc=False, seat=0, seat_passenger=0,
            soc=soc, door_driver=False, window_driver=0, cabin_light=False, rear_left=False, rear_right=False,
            charge_port=False)


# ---- screen --------------------------------------------------------------------------------------

def screen_xml():
    adb("shell", "uiautomator", "dump", "/sdcard/chaseom_ui.xml")
    return adb("shell", "cat", "/sdcard/chaseom_ui.xml")


def screen_texts():
    return [t for t in re.findall(r'text="([^"]*)"', screen_xml()) if t.strip()]


def screen_has(text):
    return text in screen_xml()


def tap_text(text, timeout=5.0):
    """Tap the first view whose text equals `text`."""
    end = time.time() + timeout
    while time.time() < end:
        m = re.search(r'text="' + re.escape(text) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', screen_xml())
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            return True
        time.sleep(0.5)
    return False


def shot(out_dir, name):
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, f"{name}.png"), "wb") as f:
        f.write(adb("exec-out", "screencap", "-p", binary=True))


# ---- window size (QA R-02b: Pleos splits the app area 1:1 when two apps are open, ~840 dp each) --------

LARGE_BOUNDS = ("853", "90", "2535", "1300")  # Pleos Connect 2.1.0 app area, 1682 x 1210
SMALL_BOUNDS = ("853", "90", "1694", "1300")  # 841 x 1210 = one half of a 1:1 split (density 160 -> 841 dp)


def task_id(pkg):
    """Task id of [pkg]'s activity, from `dumpsys activity containers`."""
    m = re.search(re.escape(pkg) + r"/\S*Activity t(\d+)", adb("shell", "dumpsys", "activity", "containers"))
    return m.group(1) if m else None


def task_bounds(pkg):
    for line in adb("shell", "dumpsys", "activity", "containers").splitlines():
        if pkg + "/" in line and "Activity" in line:
            b = re.findall(r" bounds=(\[[^ ]+)", line)
            if b:
                return b[0]
    return None


class small_window:
    """`with vc.small_window(pkg):` resizes the app task to the 841 dp half and always restores it."""

    def __init__(self, pkg):
        self.pkg = pkg
        self.tid = None

    def __enter__(self):
        self.tid = task_id(self.pkg)
        if self.tid is None:
            raise SystemExit(f"{self.pkg} is not running")
        adb("shell", "am", "task", "resize", self.tid, *SMALL_BOUNDS)
        time.sleep(4)
        return self

    def __exit__(self, *exc):
        adb("shell", "am", "task", "resize", self.tid, *LARGE_BOUNDS)
        time.sleep(2)


# ---- timing (PROBE lines: wall clock of a vehicle callback or of a drawn frame) -------------------

def clock_offset_ms():
    """device wall clock - host wall clock, from the fastest of 8 round trips."""
    best = None
    for _ in range(8):
        t0 = time.time() * 1000
        dev = float(adb("shell", "date", "+%s%3N").strip())
        t1 = time.time() * 1000
        if best is None or t1 - t0 < best[0]:
            best = (t1 - t0, dev - (t0 + t1) / 2)
    return best[1]


def probe_events():
    out = []
    for line in adb("logcat", "-d", "-v", "epoch", "-s", "PROBE:*").splitlines():
        p = line.split()
        if len(p) > 5 and "PROBE" in line:
            try:
                out.append((float(p[0]) * 1000, line.split("PROBE", 1)[1].lstrip(": ").strip()))
            except ValueError:
                pass
    return out


def first_probe(tag, since_host_ms, offset_ms):
    """Milliseconds from a host-side write to the first PROBE line starting with `tag`."""
    for ts, m in probe_events():
        dt = ts - offset_ms - since_host_ms
        if m.startswith(tag) and dt > -50:
            return round(dt, 1)
    return None


def timed_write(tag, *writes, wait=2.0):
    """Clear the log, apply VC writes, return ms until the app logs `tag` (or None)."""
    adb("logcat", "-c")
    off = clock_offset_ms()
    t = time.time() * 1000
    for w in writes:
        set_car(**w)
    time.sleep(wait)
    return first_probe(tag, t, off)


# ---- process cost --------------------------------------------------------------------------------

def cpu_samples(pid, seconds):
    """App CPU % (100 = one core) from `top`, about one sample per second."""
    vals = []
    for _ in range(seconds):
        for line in adb("shell", "top", "-b", "-n", "1", "-p", pid, "-o", "PID,%CPU").splitlines():
            f = line.split()
            if f and f[0] == pid:
                vals.append(float(f[1]))
        time.sleep(0.6)
    return vals


def pss_mb(pkg):
    for line in adb("shell", "dumpsys", "meminfo", pkg).splitlines():
        if "TOTAL PSS" in line or line.strip().startswith("TOTAL"):
            nums = [int(x) for x in line.replace(":", " ").split() if x.isdigit()]
            if nums:
                return round(nums[0] / 1024, 1)
    return None


def summary(vals):
    vals = [v for v in vals if v is not None]
    if not vals:
        return None
    return {"median": round(statistics.median(vals), 1), "max": round(max(vals), 1), "n": len(vals)}
