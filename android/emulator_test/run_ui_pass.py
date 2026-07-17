#!/usr/bin/env python3
"""Second-pass emulator UI checks against running Xilo debug build."""
from __future__ import annotations

import json
import re
import subprocess
import time
from pathlib import Path

OUT = Path(r"D:/Projects/Xilo/android/emulator_test")
ADB = r"C:/Users/Usher/AppData/Local/Android/Sdk/platform-tools/adb.exe"
results: list[tuple[str, str, str]] = []


def sh(*args: str, check: bool = True) -> str:
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
    import xml.etree.ElementTree as ET

    sh("shell", "uiautomator", "dump", "/data/local/tmp/ui.xml")
    sh("pull", "/data/local/tmp/ui.xml", str(OUT / "ui.xml"))
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
    path = OUT / name
    path.write_bytes(subprocess.check_output([ADB, "exec-out", "screencap", "-p"]))
    print("shot", name, path.stat().st_size)


def summarize(root, label: str) -> None:
    rows = []
    for n in nodes(root):
        t = n.attrib.get("text", "")
        d = n.attrib.get("content-desc", "")
        if t or d:
            rows.append(
                f"{n.attrib.get('class', '').split('.')[-1]}: text={t!r} desc={d!r} "
                f"click={n.attrib.get('clickable')} {n.attrib.get('bounds')}"
            )
    (OUT / f"{label}.txt").write_text("\n".join(rows), encoding="utf-8")
    print(f"=== {label} ({len(rows)}) ===")
    for row in rows[:40]:
        print(row)


def tap(x: int, y: int) -> None:
    sh("shell", "input", "tap", str(x), str(y))


def tap_desc(root, substr: str) -> bool:
    for n in nodes(root):
        if substr in n.attrib.get("content-desc", "") and bounds(n):
            _, (x, y) = bounds(n)
            tap(x, y)
            return True
    return False


def tap_text(root, substr: str) -> bool:
    for n in nodes(root):
        if substr in n.attrib.get("text", "") and bounds(n):
            _, (x, y) = bounds(n)
            tap(x, y)
            return True
    return False


def wait(sec: float = 1.5) -> None:
    time.sleep(sec)


def main() -> None:
    sh("shell", "am", "start", "-n", "ir.xilo.app/.MainActivity")
    wait(2)

    # Feed
    root = dump()
    tap_desc(root, "فید")
    wait(1.5)
    root = dump()
    summarize(root, "pass2_feed")
    screenshot("pass2_feed.png")
    results.append(("feed_visible", "OK" if "Docker" in all_text(root) or "Bob" in all_text(root) else "FAIL", all_text(root)[:160]))

    # Settings gear on feed header
    if tap_desc(root, "Settings") or tap_desc(root, "تنظیمات"):
        wait(2)
        root = dump()
        summarize(root, "pass2_settings")
        screenshot("pass2_settings.png")
        results.append(("settings", "OK", all_text(root)[:200]))
        sh("shell", "input", "keyevent", "4")
        wait(1)
    else:
        # gear is often a clickable image without desc; try top-left hotspot
        tap(70, 180)
        wait(2)
        root = dump()
        summarize(root, "pass2_settings_hotspot")
        screenshot("pass2_settings.png")
        txt = all_text(root)
        results.append(("settings", "OK" if "ورود" not in txt else "FAIL", txt[:200]))
        if "فید" not in " ".join(n.attrib.get("content-desc", "") for n in nodes(root)):
            sh("shell", "input", "keyevent", "4")
            wait(1)

    # Create + publish post
    root = dump()
    tap_desc(root, "فید")
    wait(1)
    root = dump()
    if not tap_desc(root, "ایجاد پست جدید"):
        results.append(("create_post", "FAIL", "FAB missing"))
    else:
        wait(2)
        root = dump()
        summarize(root, "pass2_create")
        screenshot("pass2_create.png")
        edits = [n for n in nodes(root) if "EditText" in n.attrib.get("class", "")]
        if len(edits) >= 2:
            _, (x, y) = bounds(edits[0])
            tap(x, y)
            wait(0.2)
            sh("shell", "input", "text", "EmulatorPostTitle")
            wait(0.3)
            _, (x, y) = bounds(edits[1])
            tap(x, y)
            wait(0.2)
            sh("shell", "input", "text", "BodyFromEmulatorTest")
            wait(0.4)
            root = dump()
            if tap_text(root, "انتشار"):
                wait(3)
                root = dump()
                summarize(root, "pass2_after_publish")
                screenshot("pass2_after_publish.png")
                txt = all_text(root)
                results.append(
                    (
                        "publish_post",
                        "OK" if "EmulatorPostTitle" in txt or "فید" in " ".join(n.attrib.get("content-desc", "") for n in nodes(root)) else "PARTIAL",
                        txt[:220],
                    )
                )
            else:
                results.append(("publish_post", "FAIL", "publish button missing"))
        else:
            results.append(("create_fields", "FAIL", f"edits={len(edits)}"))
        # Ensure back to main if still on composer
        for _ in range(2):
            root = dump()
            if "ایجاد پست جدید" in all_text(root):
                sh("shell", "input", "keyevent", "4")
                wait(1)

    # Discover search
    root = dump()
    tap_desc(root, "اکتشاف")
    wait(2)
    root = dump()
    if tap_desc(root, "جستجو"):
        wait(1.5)
        root = dump()
        summarize(root, "pass2_search")
        screenshot("pass2_search.png")
        edits = [n for n in nodes(root) if "EditText" in n.attrib.get("class", "")]
        if edits:
            _, (x, y) = bounds(edits[0])
            tap(x, y)
            wait(0.2)
            sh("shell", "input", "text", "docker")
            wait(2)
            root = dump()
            screenshot("pass2_search_results.png")
            results.append(("search", "OK", all_text(root)[:220]))
        else:
            results.append(("search", "PARTIAL", all_text(root)[:180]))
        sh("shell", "input", "keyevent", "4")
        wait(1)
    else:
        results.append(("search", "FAIL", "search icon missing"))

    # Profile tab
    root = dump()
    if tap_desc(root, "پروفایل"):
        wait(2.5)
        root = dump()
        # Detect launcher leak
        launcher = any("Chrome" == n.attrib.get("text") for n in nodes(root))
        summarize(root, "pass2_profile")
        screenshot("pass2_profile.png")
        if launcher:
            results.append(("profile", "FAIL", "navigated to launcher"))
            sh("shell", "am", "start", "-n", "ir.xilo.app/.MainActivity")
            wait(2)
        else:
            results.append(("profile", "OK", all_text(root)[:220]))

    # Messages: confirm empty list / mock stories
    root = dump()
    tap_desc(root, "پیام‌ها")
    wait(2)
    root = dump()
    summarize(root, "pass2_messages")
    screenshot("pass2_messages.png")
    txt = all_text(root)
    has_stories = "آرش" in txt
    has_thread = "سلام" in txt or "API" in txt
    results.append(("messages_stories_mock", "OK" if has_stories else "FAIL", "stories present" if has_stories else "missing"))
    results.append(("messages_threads", "EMPTY" if not has_thread else "OK", txt[:220]))

    # Log fatals for our package only
    log = sh("logcat", "-d", "-t", "1000", check=False)
    fatals = [ln for ln in log.splitlines() if "FATAL EXCEPTION" in ln]
    ours = [ln for ln in fatals if "ir.xilo" in ln]
    results.append(("fatal_ir_xilo", "FAIL" if ours else "OK", str(len(ours))))

    print("--- RESULTS ---")
    for row in results:
        print(row)
    (OUT / "results_pass2.json").write_text(
        json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8"
    )


if __name__ == "__main__":
    main()
