#!/usr/bin/env python3
"""Verify top chrome clears the system status bar after inset fix."""
from __future__ import annotations

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ADB = r"C:/Users/Usher/AppData/Local/Android/Sdk/platform-tools/adb.exe"
OUT = Path(r"D:/Projects/Xilo/android/emulator_test")
EMAIL = "faslolkhitab@gmail.com"
PASSWORD = "A452586111!"
UI_REMOTE = "/sdcard/xilo_ui.xml"
SB = 140


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


def dump(name: str):
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


def shot(name: str) -> None:
    (OUT / name).write_bytes(subprocess.check_output([ADB, "exec-out", "screencap", "-p"]))
    print("shot", name)


def tap(x: int, y: int) -> None:
    adb("shell", "input", "tap", str(x), str(y))


def tap_text(root, *substrs: str) -> bool:
    for n in nodes(root):
        hay = f"{n.attrib.get('text', '')} {n.attrib.get('content-desc', '')}"
        if any(s in hay for s in substrs) and bounds(n):
            tap(*bounds(n)[1])
            return True
    return False


def clear_field() -> None:
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    for _ in range(60):
        adb("shell", "input", "keyevent", "67")


def type_text(value: str) -> None:
    """Type one character at a time (Compose TextField-friendly)."""
    for ch in value:
        if ch == " ":
            adb("shell", "input", "keyevent", "62")
        elif ch == "@":
            adb("shell", "input", "text", "\\@")
        elif ch in r"#!$&|;<>()`\"'":
            adb("shell", "input", "text", "\\" + ch)
        else:
            adb("shell", "input", "text", ch)


def find_edits(root):
    return [n for n in nodes(root) if n.attrib.get("class", "").endswith("EditText")]


def assert_below(root, substrs: tuple[str, ...], label: str) -> bool:
    for n in nodes(root):
        hay = f"{n.attrib.get('text', '')} {n.attrib.get('content-desc', '')}"
        if any(s in hay for s in substrs):
            b = bounds(n)
            if not b:
                continue
            top = b[0][1]
            ok = top >= SB - 4
            print(f"{label}: {hay!r} top={top} sb≈{SB} -> {'OK' if ok else 'FAIL'}")
            return ok
    print(f"{label}: not found {substrs}")
    for n in nodes(root):
        t = n.attrib.get("text", "")
        d = n.attrib.get("content-desc", "")
        b = bounds(n)
        if (t or d) and b and b[0][1] < 400:
            print("  topnode", b[0][1], repr(t), repr(d))
    return False


def main() -> int:
    adb("shell", "am", "force-stop", "ir.xilo.app")
    adb("shell", "am", "start", "-n", "ir.xilo.app/.MainActivity")
    time.sleep(2.5)
    root = dump("v3_01.xml")

    tap_text(root, "English")
    time.sleep(1.0)
    root = dump("v3_01b.xml")
    edits = find_edits(root)
    print("edits", len(edits))
    if len(edits) < 2:
        print("FAIL: auth fields missing")
        return 1

    # Capture Sign-in coords before the IME covers the form.
    sign_in_center = None
    for n in nodes(root):
        if n.attrib.get("text", "") in ("Sign in", "Log in", "ورود به حساب") and bounds(n):
            sign_in_center = bounds(n)[1]
            break
    if sign_in_center is None:
        print("FAIL: sign-in coords missing")
        return 1

    _, (x, y) = bounds(edits[0])
    tap(x, y)
    time.sleep(0.2)
    clear_field()
    type_text(EMAIL)
    time.sleep(0.3)

    _, (x, y) = bounds(edits[1])
    tap(x, y)
    time.sleep(0.3)
    clear_field()
    type_text(PASSWORD)
    time.sleep(0.3)

    # Dismiss IME by tapping the title (do NOT send BACK — it leaves the app).
    if not tap_text(root, "Welcome", "خوش آمدید"):
        tap_text(root, "aile", "آیله")
    time.sleep(0.5)
    root = dump("v3_01c.xml")
    if not tap_text(root, "Sign in", "Log in", "ورود به حساب"):
        tap(*sign_in_center)
    time.sleep(5.0)

    root = dump("v3_02.xml")
    shot("v3_02.png")
    txt = " | ".join(n.attrib.get("text", "") for n in nodes(root))
    print("after login:", txt[:250])
    if any(m in txt for m in ("Login failed", "ورود انجام نشد")):
        print("FAIL: login error")
        return 1
    if find_edits(root):
        print("FAIL: still on auth")
        return 1

    if not tap_text(root, "Messages", "پیام‌ها", "Chats", "گفتگو"):
        tap(750, 2750)
    time.sleep(1.5)
    root = dump("v3_03.xml")
    shot("v3_03.png")

    found = False
    for n in nodes(root):
        desc = n.attrib.get("content-desc", "")
        if desc in ("New message", "پیام جدید") or "new" in desc.lower():
            b = bounds(n)
            if b:
                tap(*b[1])
                found = True
                print("tap", desc, b[0])
                break
    if not found:
        print("FAIL: new chat button")
        for n in nodes(root):
            d = n.attrib.get("content-desc", "")
            b = bounds(n)
            if d and b and b[0][1] < 500:
                print("cand", d, b[0])
        return 1
    time.sleep(1.3)
    root = dump("v3_04_new.xml")
    shot("v3_04_new.png")
    ok_new = assert_below(root, ("New message", "پیام جدید"), "new_chat")

    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(1.0)
    root = dump("v3_05.xml")
    if not tap_text(root, "Home", "خانه", "Feed", "فید"):
        tap(200, 2750)
    time.sleep(1.2)
    root = dump("v3_06_feed.xml")
    shot("v3_06_feed.png")
    for n in nodes(root):
        d = n.attrib.get("content-desc", "")
        b = bounds(n)
        if d and b and b[0][1] < 300:
            print(f"feed {d!r} top={b[0][1]} {'OK' if b[0][1] >= SB - 4 else 'FAIL'}")

    cands = []
    for n in nodes(root):
        if n.attrib.get("clickable") != "true":
            continue
        b = bounds(n)
        if not b:
            continue
        (x1, y1, x2, y2), c = b
        if (y2 - y1) > 180 and (x2 - x1) > 600 and y1 > 200:
            cands.append((y1, c))
    cands.sort()
    ok_post = True
    if cands:
        tap(*cands[0][1])
        time.sleep(1.5)
        root = dump("v3_07_post.xml")
        shot("v3_07_post.png")
        ok_post = False
        for n in nodes(root):
            d = n.attrib.get("content-desc", "")
            b = bounds(n)
            if not b:
                continue
            top = b[0][1]
            if top < 400 and (
                d in ("Back", "بازگشت", "aile", "آیله")
                or "aile" in d.lower()
                or "آیله" in d
            ):
                ok = top >= SB - 4
                print(f"post {d!r} top={top} -> {'OK' if ok else 'FAIL'}")
                ok_post = ok
                break
        if not ok_post:
            ok_post = assert_below(root, ("Back", "بازگشت", "آیله", "aile"), "post")
    else:
        print("WARN: no post candidate")

    print("RESULT", "OK" if ok_new and ok_post else "FAIL", dict(new=ok_new, post=ok_post))
    return 0 if ok_new and ok_post else 1


if __name__ == "__main__":
    raise SystemExit(main())
