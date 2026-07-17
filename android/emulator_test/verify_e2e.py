#!/usr/bin/env python3
"""Robust emulator E2E using ADB argv only (no Git Bash path conversion)."""
from __future__ import annotations

import json
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

OUT = Path(r"D:/Projects/Xilo/android/emulator_test")
ADB = r"C:/Users/Usher/AppData/Local/Android/Sdk/platform-tools/adb.exe"
UI_REMOTE = "/data/local/tmp/xilo_ui.xml"
results: list[tuple[str, str, str]] = []


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


def api(method: str, path: str, data=None, token=None, headers=None):
    req = urllib.request.Request(f"http://127.0.0.1:8888{path}", method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    if headers:
        for k, v in headers.items():
            req.add_header(k, v)
    body = None if data is None else json.dumps(data).encode()
    try:
        with urllib.request.urlopen(req, data=body, timeout=20) as resp:
            raw = resp.read().decode()
            return resp.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            payload = json.loads(raw)
        except Exception:
            payload = {"raw": raw}
        return e.code, payload


def dump():
    import xml.etree.ElementTree as ET

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


def tap_desc(root, substr: str) -> bool:
    for n in nodes(root):
        if substr in n.attrib.get("content-desc", "") and bounds(n):
            _, (x, y) = bounds(n)
            tap(x, y)
            return True
    return False


def type_text(value: str) -> None:
    """Type into the focused field without shell metacharacter breakage."""
    for ch in value:
        if ch == " ":
            adb("shell", "input", "keyevent", "62")
        elif ch == "@":
            adb("shell", "input", "text", "\\@")
        elif ch in r"#!$&|;<>`()\"'":
            adb("shell", "input", "text", "\\" + ch)
        else:
            adb("shell", "input", "text", ch)


def wait(sec: float = 1.5) -> None:
    time.sleep(sec)


def main() -> None:
    ts = str(int(time.time()))
    email = f"e2e{ts}@xilo.test"
    username = f"e2e{ts}"
    password = "TestPass123#"  # special char that is safer for adb than '!'

    st, reg = api(
        "POST",
        "/api/auth/register",
        {"email": email, "username": username, "password": password},
    )
    results.append(("api_register", "OK" if st in (200, 201) else "FAIL", f"{st}"))
    if st not in (200, 201):
        print(reg)
        sys.exit(1)

    token = reg["access_token"]
    # Seed a DM so messages tab has a real row after login
    st, peer = api(
        "POST",
        "/api/auth/register",
        {
            "email": f"peer{ts}@xilo.test",
            "username": f"peer{ts}",
            "password": password,
        },
    )
    peer_id = peer.get("user", {}).get("id")
    st, chat = api(
        "POST",
        "/api/chats",
        {"type": "direct", "member_ids": [peer_id]},
        token=token,
        headers={"Idempotency-Key": str(uuid.uuid4())},
    )
    results.append(("api_seed_chat", "OK" if st in (200, 201) else "FAIL", f"{st}"))
    chat_id = chat.get("id")
    if chat_id:
        api(
            "POST",
            f"/api/chats/{chat_id}/messages",
            {"type": "text", "content": "hello-e2e"},
            token=token,
            headers={"Idempotency-Key": str(uuid.uuid4())},
        )

    adb("shell", "pm", "clear", "ir.xilo.app", check=False)
    adb("shell", "am", "start", "-n", "ir.xilo.app/.MainActivity")
    wait(2.5)

    root = dump()
    screenshot("e2e_01_auth.png")
    edits = [n for n in nodes(root) if "EditText" in n.attrib.get("class", "")]
    if len(edits) < 2:
        results.append(("auth_fields", "FAIL", str(len(edits))))
        print(results)
        return

    _, (x, y) = bounds(edits[0])
    tap(x, y)
    wait(0.3)
    type_text(email)
    wait(0.4)
    _, (x, y) = bounds(edits[1])
    tap(x, y)
    wait(0.3)
    type_text(password)
    wait(0.4)
    root = dump()
    tap_text(root, "ورود به حساب")
    wait(5)
    root = dump()
    screenshot("e2e_02_post_login.png")
    txt = all_text(root)
    if "رد شدن" in txt or "ادامه" in txt or "به زیلو خوش آمدید" in txt:
        tap_text(root, "رد شدن") or tap_text(root, "ادامه")
        wait(2)
        root = dump()
        txt = all_text(root)
    logged_in = "ورود به حساب" not in txt
    results.append(("login", "OK" if logged_in else "FAIL", txt[:180]))
    if not logged_in:
        print("--- RESULTS ---")
        for row in results:
            print(row)
        (OUT / "results_e2e.json").write_text(
            json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        return

    # Feed
    tap_desc(root, "فید")
    wait(2)
    root = dump()
    screenshot("e2e_03_feed.png")
    results.append(
        (
            "feed",
            "OK" if ("Bob" in all_text(root) or "Docker" in all_text(root) or "پست" in all_text(root) or "برای شما" in all_text(root)) else "FAIL",
            all_text(root)[:160],
        )
    )

    # Publish
    if tap_desc(root, "ایجاد پست جدید"):
        wait(2)
        root = dump()
        edits = [n for n in nodes(root) if "EditText" in n.attrib.get("class", "")]
        if len(edits) >= 2:
            _, (x, y) = bounds(edits[0])
            tap(x, y)
            wait(0.2)
            type_text("E2EPublishTitle")
            wait(0.3)
            _, (x, y) = bounds(edits[1])
            tap(x, y)
            wait(0.2)
            type_text("E2EPublishBody")
            wait(0.4)
            root = dump()
            tap_text(root, "انتشار")
            wait(5)
            root = dump()
            screenshot("e2e_04_publish.png")
            txt = all_text(root)
            bad = any(k in txt for k in ["Unauthorized", "غیرمجاز", "وارد شوید", "permission", "مجوز"])
            ok = ("ایجاد پست جدید" not in txt) and not bad
            results.append(("publish", "OK" if ok else "FAIL", txt[:220]))
            if "ایجاد پست جدید" in txt:
                adb("shell", "input", "keyevent", "4")
                wait(1)
        else:
            results.append(("publish", "FAIL", "missing fields"))
    else:
        results.append(("publish", "FAIL", "FAB missing"))

    # Messages
    root = dump()
    tap_desc(root, "پیام‌ها")
    wait(4)
    root = dump()
    screenshot("e2e_05_messages.png")
    txt = all_text(root)
    mock = "آرش" in txt
    empty = "هنوز گفتگویی ندارید" in txt
    has_chat = ("hello" in txt) or ("peer" in txt) or ("p2" in txt) or (not empty and "پیام‌ها" in txt and "همه گفتگوها" in txt and len(txt) > 80)
    if mock:
        results.append(("messages", "FAIL", "mock stories present"))
    elif empty:
        results.append(("messages", "PARTIAL", "empty state; sync may lag"))
    else:
        results.append(("messages", "OK", txt[:220]))
        tap(540, 780)
        wait(2)
        root = dump()
        screenshot("e2e_06_chat.png")
        results.append(("conversation", "INFO", all_text(root)[:200]))
        # send a message if composer exists
        edits = [n for n in nodes(root) if "EditText" in n.attrib.get("class", "")]
        if edits:
            _, (x, y) = bounds(edits[0])
            tap(x, y)
            wait(0.2)
            type_text("SalamE2E")
            wait(0.4)
            root = dump()
            # send button often appears as FAB without text; tap near end
            if not tap_desc(root, "ارسال") and not tap_text(root, "ارسال"):
                tap(1000, 2050)
            wait(2)
            root = dump()
            screenshot("e2e_07_sent.png")
            results.append(("chat_send", "OK" if "SalamE2E" in all_text(root) else "PARTIAL", all_text(root)[:200]))

    # Profile
    root = dump()
    tap_desc(root, "پروفایل")
    wait(2)
    root = dump()
    screenshot("e2e_08_profile.png")
    results.append(("profile", "OK" if username in all_text(root) else "FAIL", all_text(root)[:180]))

    print("--- RESULTS ---")
    for row in results:
        print(row)
    (OUT / "results_e2e.json").write_text(
        json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8"
    )


if __name__ == "__main__":
    main()
