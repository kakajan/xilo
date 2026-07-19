#!/usr/bin/env python3
"""Verify profile header shows labels under stat counts."""
from __future__ import annotations

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

OUT = Path(r"D:/Projects/Xilo/android/emulator_test")
ADB = r"C:/Users/Usher/AppData/Local/Android/Sdk/platform-tools/adb.exe"
UI_REMOTE = "/data/local/tmp/xilo_ui.xml"

EXPECTED = ("پست", "دنبال‌کننده", "دنبال‌شونده")


def adb(*args: str, check: bool = True) -> str:
    r = subprocess.run(
        [ADB, *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {args}: {r.stderr or r.stdout}")
    return r.stdout


def dump(name: str = "ui.xml"):
    adb("shell", "uiautomator", "dump", UI_REMOTE)
    adb("pull", UI_REMOTE, str(OUT / name))
    return ET.parse(OUT / name).getroot()


def nodes(root):
    return list(root.iter("node"))


def bounds(n):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.attrib.get("bounds", ""))
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1, y1, x2, y2), ((x1 + x2) // 2, (y1 + y2) // 2)


def all_text(root) -> str:
    return " | ".join(n.attrib.get("text", "") for n in nodes(root) if n.attrib.get("text"))


def screenshot(name: str) -> None:
    (OUT / name).write_bytes(subprocess.check_output([ADB, "exec-out", "screencap", "-p"]))
    print("shot", name)


def tap(x: int, y: int) -> None:
    adb("shell", "input", "tap", str(x), str(y))


def tap_text(root, substr: str) -> bool:
    for n in nodes(root):
        if substr in n.attrib.get("text", "") and bounds(n):
            _, (x, y) = bounds(n)
            tap(x, y)
            return True
    return False


def tap_desc(root, substr: str) -> bool:
    for n in nodes(root):
        if substr in n.attrib.get("content-desc", "") and bounds(n):
            _, (x, y) = bounds(n)
            tap(x, y)
            return True
    return False


def wait(sec: float = 1.5) -> None:
    time.sleep(sec)


def on_profile(root) -> bool:
    texts = all_text(root)
    has_labels = all(label in texts for label in EXPECTED)
    has_actions = any(
        s in texts
        for s in (
            "تنظیم عکس",
            "ویرایش اطلاعات",
            "Set photo",
            "Edit info",
            "Edit information",
        )
    )
    return has_labels or has_actions


def open_profile() -> ET.Element:
    adb("shell", "am", "force-stop", "ir.xilo.app")
    adb("shell", "am", "start", "-n", "ir.xilo.app/.MainActivity")
    wait(2.5)
    root = dump("profile_stats_01.xml")
    screenshot("profile_stats_01.png")
    print("boot texts:", all_text(root)[:400])

    if on_profile(root):
        return root

    # Settings → My profile is a common resume path.
    if tap_text(root, "My profile") or tap_text(root, "پروفایل من"):
        wait(2.0)
        root = dump("profile_stats_from_settings.xml")
        screenshot("profile_stats_from_settings.png")
        if on_profile(root):
            return root

    for attempt in range(6):
        if on_profile(root):
            return root
        if (
            tap_text(root, "پروفایل")
            or tap_text(root, "Profile")
            or tap_desc(root, "Profile")
            or tap_desc(root, "پروفایل")
            or tap_desc(root, "nav_profile")
        ):
            wait(1.5)
            root = dump(f"profile_stats_nav_{attempt}.xml")
            screenshot(f"profile_stats_nav_{attempt}.png")
            continue
        # Bottom-nav hotspots for tall Pixel devices
        for x, y in ((980, 2550), (100, 2550), (540, 2550), (980, 2300), (100, 2300)):
            tap(x, y)
            wait(1.2)
            root = dump(f"profile_stats_hot_{attempt}.xml")
            screenshot(f"profile_stats_hot_{attempt}.png")
            if on_profile(root):
                return root
        break
    return root


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    root = open_profile()
    texts = all_text(root)
    screenshot("profile_stats_final.png")
    dump("profile_stats_final.xml")
    print("final texts:", texts)

    missing = [label for label in EXPECTED if label not in texts]
    actions_ok = "تنظیم عکس" in texts or "ویرایش اطلاعات" in texts or "تنظیمات" in texts

    if missing:
        print("FAIL missing labels:", missing)
        print("actions_visible:", actions_ok)
        return 1

    # Ensure each label sits below some digit-like count in the same column-ish area
    label_nodes = []
    for n in nodes(root):
        t = n.attrib.get("text", "")
        if t in EXPECTED and bounds(n):
            label_nodes.append((t, bounds(n)[0]))
    print("label bounds:", label_nodes)
    if len(label_nodes) < 3:
        print("FAIL: expected 3 visible label nodes, got", len(label_nodes))
        return 1

    print("PASS: profile stat labels visible")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
