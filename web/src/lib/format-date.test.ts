import { describe, expect, it } from "vitest";
import { formatDateString, zonedWallClockDate } from "./format-date";

describe("zonedWallClockDate", () => {
  it("uses Tehran wall clock so UTC evening is the next local date", () => {
    // 20:30 UTC on 8 Sep 2026 == 00:00 on 9 Sep in Asia/Tehran (+03:30)
    const wall = zonedWallClockDate(new Date("2026-09-08T20:30:00.000Z"));
    expect(wall.getFullYear()).toBe(2026);
    expect(wall.getMonth()).toBe(8);
    expect(wall.getDate()).toBe(9);
    expect(wall.getHours()).toBe(0);
    expect(wall.getMinutes()).toBe(0);
  });
});

describe("formatDateString", () => {
  it("formats gregorian dates in Asia/Tehran, not process TZ", () => {
    expect(
      formatDateString("2026-09-08T20:30:00.000Z", {
        calendar: "gregorian",
        pattern: "yyyy-MM-dd HH:mm",
      }),
    ).toBe("2026-09-09 00:00");
  });
});
