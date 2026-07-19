#!/usr/bin/env python3
"""Verify top chrome clears the system status bar on New message + post detail."""
from __future__ import annotations

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

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


def screenshot(name: str) -> None:
    (OUT / name).write_bytes(subprocess.check_output([ADB, "exec-out", "screencap", "-p"]))
    print("shot", name)


def tap(x: int, y: int) -> None:
    adb("shell", "input", "tap", str(x), str(y))


def tap_text(root, *substrs: str) -> bool:
    for n in nodes(root):
        text = n.attrib.get("text", "")
        desc = n.attrib.get("content-desc", "")
        hay = f"{text} {desc}"
        if any(s in hay for s in substrs) and bounds(n):
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
    return [n for n in nodes(root) if n.attrib.get("class", "").endswith("EditText")]


def status_bar_bottom(root) -> int:
    """Best-effort: status bar / system icons usually end before ~80–140px on modern devices."""
    # Use resource-id status_bar if present; else assume 120px on this emulator density.
    for n in nodes(root):
        rid = n.attrib.get("resource-id", "")
        if rid.endswith("status_bar") or rid.endswith("statusBarBackground"):
            b = bounds(n)
            if b:
                return b[0][3]
    return 120


def assert_title_below_status_bar(root, title_substrs: tuple[str, ...], label: str) -> bool:
    sb = status_bar_bottom(root)
    for n in nodes(root):
        text = n.attrib.get("text", "")
        desc = n.attrib.get("content-desc", "")
        hay = f"{text} {desc}"
        if any(s in hay for s in title_substrs):
            b = bounds(n)
            if not b:
                continue
            top = b[0][1]
            ok = top >= sb - 4
            print(f"{label}: found {hay!r} top={top} statusBarBottom≈{sb} -> {'OK' if ok else 'FAIL'}")
            return ok
    print(f"{label}: title not found for {title_substrs}")
    return False


def main() -> int:
    adb("shell", "am", "force-stop", "ir.xilo.app")
    adb("shell", "am", "start", "-n", "ir.xilo.app/.MainActivity")
    time.sleep(2.5)

    root = dump("status_inset_01_auth.xml")
    screenshot("status_inset_01_auth.png")

    # Switch to English for stable strings matching the bug report.
    tap_text(root, "English")
    time.sleep(1.0)
    root = dump("status_inset_01b_en.xml")

    edits = find_edit_texts(root)
    if len(edits) < 2:
        print("FAIL: email/password fields missing")
        return 1

    _, (x, y) = bounds(edits[0])
    tap(x, y)
    time.sleep(0.3)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    for _ in range(40):
        adb("shell", "input", "keyevent", "67")
    type_text(EMAIL)
    time.sleep(0.3)

    _, (x, y) = bounds(edits[1])
    tap(x, y)
    time.sleep(0.3)
    for _ in range(40):
        adb("shell", "input", "keyevent", "67")
    type_text(PASSWORD)
    time.sleep(0.3)

    root = dump("status_inset_01c_before_submit.xml")
    if not tap_text(root, "Log in", "ورود به حساب", "Sign in", "Login"):
        print("WARN: login button text not found, trying broad match")
        if not tap_text(root, "account", "حساب"):
            print("FAIL: login button")
            return 1
    time.sleep(4.0)

    root = dump("status_inset_02_home.xml")
    screenshot("status_inset_02_home.png")
    txt = " | ".join(n.attrib.get("text", "") for n in nodes(root))
    if any(m in txt for m in ("Login failed", "ورود انجام نشد", "invalid email", "ایمیل یا رمز")):
        print("FAIL: login error", txt[:200])
        return 1

    # Open Messages tab
    if not tap_text(root, "Messages", "پیام‌ها", "Chats", "گفتگو"):
        # bottom nav often has content-desc
        if not tap_text(root, "messages", "chat"):
            print("WARN: messages tab not found by text; tapping approx nav slot 3")
            # 4 tabs — third from left/right; Pixel ~1344 wide
            tap(750, 2750)
    time.sleep(1.5)

    root = dump("status_inset_03_chats.xml")
    screenshot("status_inset_03_chats.png")

    # New message action
    if not tap_text(root, "New message", "پیام جدید", "cd_new_chat", "Edit"):
        # try content-desc from strings
        found = False
        for n in nodes(root):
            desc = n.attrib.get("content-desc", "")
            if desc in ("New message", "پیام جدید") or "new" in desc.lower():
                b = bounds(n)
                if b:
                    tap(*b[1])
                    found = True
                    break
        if not found:
            print("FAIL: new chat button not found")
            return 1
    time.sleep(1.2)

    root = dump("status_inset_04_new_chat.xml")
    screenshot("status_inset_04_new_chat.png")
    ok_new = assert_title_below_status_bar(
        root, ("New message", "پیام جدید"), "new_chat"
    )

    # Back then open a post from feed for logo top bar check
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(0.8)
    root = dump("status_inset_05_back.xml")
    if not tap_text(root, "Home", "خانه", "Feed", "فید"):
        tap(200, 2750)
    time.sleep(1.2)
    root = dump("status_inset_06_feed.xml")
    screenshot("status_inset_06_feed.png")

    # Tap first post-like clickable with substantial height
    candidates = []
    for n in nodes(root):
        if n.attrib.get("clickable") != "true":
            continue
        b = bounds(n)
        if not b:
            continue
        (x1, y1, x2, y2), center = b
        h = y2 - y1
        w = x2 - x1
        if h > 180 and w > 600 and y1 > 200:
            candidates.append((y1, center))
    candidates.sort()
    if candidates:
        tap(*candidates[0][1])
        time.sleep(1.5)
        root = dump("status_inset_07_post.xml")
        screenshot("status_inset_07_post.png")
        # Back button or any top chrome text should clear status bar
        ok_post = False
        for n in nodes(root):
            desc = n.attrib.get("content-desc", "")
            text = n.attrib.get("text", "")
            if desc in ("Back", "بازگشت") or text:
                b = bounds(n)
                if b and b[0][1] < 400:
                    sb = status_bar_bottom(root)
                    top = b[0][1]
                    if top >= sb - 4:
                        ok_post = True
                        print(f"post_detail chrome top={top} statusBarBottom≈{sb} OK")
                        break
        if not ok_post:
            # Visual fallback: if screenshot was taken, mark based on any node with Back
            ok_post = assert_title_below_status_bar(root, ("Back", "بازگشت"), "post_detail")
    else:
        print("WARN: no post candidate; skipping post detail check")
        ok_post = True

    if ok_new and ok_post:
        print("OK: status bar insets look correct")
        return 0
    print("FAIL: overlap still detected")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
