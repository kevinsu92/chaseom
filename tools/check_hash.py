"""Check that README's verification table and docs/evidence describe the APK in apk/.

    python tools/check_hash.py

Exit 0 when the sha256 of apk/*_release.apk equals the sha256 written in README.md and in
docs/evidence/measure_release.json; exit 1 (and say which differs) otherwise. Run it before submitting.
"""
import glob
import hashlib
import json
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")


def main():
    apks = glob.glob(os.path.join(ROOT, "apk", "*_release.apk"))
    if len(apks) != 1:
        print(f"expected one apk/*_release.apk, found {len(apks)}")
        return 1
    apk_sha = hashlib.sha256(open(apks[0], "rb").read()).hexdigest()
    readme = open(os.path.join(ROOT, "README.md"), encoding="utf-8").read()
    m = re.search(r"sha256 `([0-9a-f]{64})`", readme)
    readme_sha = m.group(1) if m else None
    evidence_sha = json.load(open(os.path.join(ROOT, "docs", "evidence", "measure_release.json"), encoding="utf-8")).get("apk_sha256")
    print(f"apk      {apk_sha}  {os.path.basename(apks[0])}")
    print(f"README   {readme_sha}")
    print(f"evidence {evidence_sha}")
    ok = apk_sha == readme_sha == evidence_sha
    print("OK: README and evidence match the APK" if ok else "MISMATCH: re-run tools/measure.py and update the README table")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
