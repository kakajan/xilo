import { describe, expect, it } from "vitest";
import { baseScale, clampOffset, clampUserScale } from "./avatar-crop-math";

describe("avatar-crop-math", () => {
  it("baseScale fills shorter side", () => {
    const scale = baseScale(400, 200, 200);
    expect(scale).toBeCloseTo(1, 3);
  });

  it("clampUserScale bounds zoom", () => {
    expect(clampUserScale(0.2)).toBeCloseTo(1, 3);
    expect(clampUserScale(9)).toBeCloseTo(4, 3);
    expect(clampUserScale(2.5)).toBeCloseTo(2.5, 3);
  });

  it("clampOffset keeps circle covered", () => {
    const { x, y } = clampOffset(10_000, -10_000, 400, 400, 200, 2);
    // baseScale=0.5, userScale=2 → displayed 400px vs circle 200 → max offset 100.
    expect(x).toBeCloseTo(100, 3);
    expect(y).toBeCloseTo(-100, 3);
    expect(x).toBeGreaterThanOrEqual(-100);
    expect(x).toBeLessThanOrEqual(100);
    expect(y).toBeGreaterThanOrEqual(-100);
    expect(y).toBeLessThanOrEqual(100);
  });
});
