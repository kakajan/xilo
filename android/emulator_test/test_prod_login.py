#!/usr/bin/env python3
"""Login smoke test against production API build on emulator."""
from __future__ import annotations

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

# Avoid Windows cp1252 crashes on Persian UI text.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

OUT = Path(r"D:/Projects/Xilo/android/emulator_test")
ADB = r"C:/Users/Usher/AppData/Local/Android/Sdk/platform-tools/adb.exe"
UI_REMOTE = "/data/local/tmp/xilo_ui.xml"
EMAIL = "faslolkhitab@gmail.com"
PASSWORD = "A452586111!"


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


def dump():
    adb("shell", "uiautomator", "dump", UI_REMOTE)
    adb("pull", UI_REMOTE, str(OUT / "ui.xml"))
    return ET.parse(OUT / "ui.xml").getroot()


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


def type_text(value: str) -> None:
    for ch in value:
        if ch == " ":
            adb("shell", "input", "keyevent", "62")
        elif ch == "@":
            adb("shell", "input", "text", "\\@")
        elif ch in r"#!$&|;<>`()\"'":
            adb("shell", "input", "text", "\\" + ch)
        else:
            adb("shell", "input", "text", ch)


def find_edit_texts(root):
    edits = []
    for n in nodes(root):
        cls = n.attrib.get("class", "")
        if "EditText" in cls and bounds(n):
            edits.append(n)
    return edits


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    adb("shell", "am", "force-stop", "ir.xilo.app")
    adb("shell", "pm", "clear", "ir.xilo.app")
    adb("shell", "am", "start", "-n", "ir.xilo.app/.MainActivity")
    time.sleep(3)

    root = dump()
    txt = all_text(root)
    print("AUTH:", txt)
    screenshot("login_rebuild_01_auth.png")

    if "ورود به حساب" not in txt and "Login" not in txt:
        print("FAIL: auth screen not visible")
        return 1

    edits = find_edit_texts(root)
    if len(edits) < 2:
        print("FAIL: expected email/password fields, got", len(edits))
        return 1

    _, (ex, ey) = bounds(edits[0])
    tap(ex, ey)
    time.sleep(0.3)
    adb("shell", "input", "keyevent", "123")  # CTRL+A alternative: move end
    # Clear existing text
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    for _ in range(40):
        adb("shell", "input", "keyevent", "67")  # DEL
    type_text(EMAIL)
    time.sleep(0.3)

    _, (px, py) = bounds(edits[1])
    tap(px, py)
    time.sleep(0.3)
    for _ in range(40):
        adb("shell", "input", "keyevent", "67")
    type_text(PASSWORD)
    time.sleep(0.3)

    root = dump()
    if not tap_text(root, "ورود به حساب"):
        print("FAIL: login button not found")
        screenshot("login_rebuild_02_fail.png")
        return 1

    time.sleep(4)
    root = dump()
    txt = all_text(root)
    print("AFTER:", txt)
    screenshot("login_rebuild_02_after_login.png")

    fail_markers = ("Login failed", "ورود انجام نشد", "invalid email", "ایمیل یا رمز")
    still_auth = "ورود به حساب" in txt
    if still_auth or any(m in txt for m in fail_markers):
        print("FAIL: still on auth / error visible")
        return 1

    print("OK: login succeeded")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
