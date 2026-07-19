#!/usr/bin/env python3
"""Select English on auth, login, assert post-login chrome is English (not Persian)."""
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


def all_text(root) -> str:
    parts = []
    for n in nodes(root):
        t = n.attrib.get("text", "")
        d = n.attrib.get("content-desc", "")
        if t:
            parts.append(t)
        if d:
            parts.append(d)
    return " | ".join(parts)


def screenshot(name: str) -> None:
    (OUT / name).write_bytes(subprocess.check_output([ADB, "exec-out", "screencap", "-p"]))
    print("shot", name)


def tap(x: int, y: int) -> None:
    adb("shell", "input", "tap", str(x), str(y))


def tap_text(root, substr: str) -> bool:
    for n in nodes(root):
        label = n.attrib.get("text", "") or n.attrib.get("content-desc", "")
        if substr in label and bounds(n):
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
        if "EditText" in n.attrib.get("class", "") and bounds(n):
            edits.append(n)
    return edits


def clear_field() -> None:
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    for _ in range(50):
        adb("shell", "input", "keyevent", "67")


def wait_for(predicate, timeout: float = 12.0, interval: float = 0.8, dump_name: str = "ui.xml"):
    deadline = time.time() + timeout
    root = None
    while time.time() < deadline:
        root = dump(dump_name)
        if predicate(root):
            return root
        time.sleep(interval)
    return root


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    adb("shell", "am", "force-stop", "ir.xilo.app")
    adb("shell", "pm", "clear", "ir.xilo.app")
    adb("shell", "am", "start", "-n", "ir.xilo.app/.MainActivity")
    time.sleep(3)

    root = dump("i18n_en_01_auth.xml")
    txt = all_text(root)
    print("AUTH_FA:", txt)
    screenshot("i18n_en_01_auth.png")

    if "English" not in txt:
        print("FAIL: English language chip not found")
        return 1

    if not tap_text(root, "English"):
        print("FAIL: could not tap English")
        return 1

    root = wait_for(
        lambda r: "Sign in" in all_text(r) or "Welcome" in all_text(r),
        timeout=8,
        dump_name="i18n_en_02_auth_en.xml",
    )
    txt = all_text(root)
    print("AUTH_EN:", txt)
    screenshot("i18n_en_02_auth_en.png")

    if "Sign in" not in txt and "Welcome" not in txt:
        print("FAIL: auth UI did not switch to English")
        return 1

    edits = find_edit_texts(root)
    if len(edits) < 2:
        print("FAIL: expected email/password fields, got", len(edits))
        return 1

    _, (ex, ey) = bounds(edits[0])
    tap(ex, ey)
    time.sleep(0.3)
    clear_field()
    type_text(EMAIL)
    time.sleep(0.3)

    _, (px, py) = bounds(edits[1])
    tap(px, py)
    time.sleep(0.3)
    clear_field()
    type_text(PASSWORD)
    time.sleep(0.3)

    # Dismiss IME by tapping the title (do NOT send BACK — it leaves the app).
    if not tap_text(root, "Welcome"):
        tap_text(root, "aile")
    time.sleep(0.5)

    root = dump("i18n_en_03_before_submit.xml")
    if not tap_text(root, "Sign in"):
        if not tap_text(root, "ورود به حساب"):
            print("FAIL: login button not found")
            screenshot("i18n_en_03_fail.png")
            return 1

    def reached_main(r) -> bool:
        t = all_text(r)
        # Exact chrome labels only — avoid matching substrings inside onboarding copy.
        markers = (
            "Feed",
            "For you",
            "Search everything...",
            "فید",
            "برای شما",
            "جستجو در همه چیز...",
        )
        return any(f" | {m} |" in f" | {t} |" or t == m or t.startswith(m + " |") or t.endswith(" | " + m) for m in markers) or any(
            m in t.split(" | ") for m in markers
        )

    def login_failed(r) -> bool:
        t = all_text(r)
        return any(
            m in t
            for m in (
                "Login failed",
                "ورود انجام نشد",
                "invalid email",
                "ایمیل یا رمز",
                "No internet",
                "اتصال اینترنت",
            )
        )

    root = None
    deadline = time.time() + 35
    while time.time() < deadline:
        root = dump("i18n_en_04_after_login.xml")
        if login_failed(root):
            break
        if reached_main(root):
            break
        t = all_text(root)
        # Prefer skip; otherwise advance steps. Onboarding still has hardcoded FA labels.
        advanced = False
        for label in ("رد شدن", "Skip", "شروع به کار", "Get started", "ادامه", "Continue", "گام بعدی", "Next"):
            if label in t.split(" | ") or label in t:
                if tap_text(root, label):
                    advanced = True
                    time.sleep(1.0)
                    break
        if not advanced:
            time.sleep(1.0)

    txt = all_text(root)
    print("AFTER_LOGIN:", txt)
    screenshot("i18n_en_04_after_login.png")

    if login_failed(root):
        print("FAIL: login error visible")
        return 1
    if not reached_main(root):
        print("FAIL: did not reach main/feed UI")
        return 1

    tokens = set(txt.split(" | "))
    english_markers = ("Feed", "For you", "Search everything...", "Discover", "Messages", "Profile")
    persian_chrome = ("فید", "برای شما", "جستجو در همه چیز...", "اکتشاف", "پیام‌ها")

    found_en = [m for m in english_markers if m in tokens or m in txt]
    found_fa = [m for m in persian_chrome if m in tokens]
    print("EN_MARKERS:", found_en)
    print("FA_MARKERS:", found_fa)

    # Confirm persisted preference survived login.
    prefs = adb(
        "shell",
        "run-as",
        "ir.xilo.app",
        "cat",
        "/data/data/ir.xilo.app/shared_prefs/xilo_auth_prefs.xml",
    )
    print("PREFS_LANG:", "preferred_language\">en" in prefs or ">en</string>" in prefs)
    if 'preferred_language">en' not in prefs and ">en</string>" not in prefs:
        # looser check
        if "<string name=\"preferred_language\">en</string>" not in prefs:
            print("FAIL: preferred_language was not kept as en after login")
            print(prefs[:400])
            return 1

    if len(found_en) < 2:
        print("FAIL: expected English chrome after English login; got:", txt[:500])
        return 1
    if found_fa:
        print("FAIL: Persian chrome still visible after English login:", found_fa)
        return 1

    print("OK: English login keeps English UI")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
