import { describe, expect, it } from "vitest";
import { useChromeVisibilityStore } from "./use-chrome-visibility";

describe("useChromeVisibilityStore", () => {
  it("defaults to visible: true and allows updating visibility", () => {
    expect(useChromeVisibilityStore.getState().visible).toBe(true);

    useChromeVisibilityStore.getState().setVisible(false);
    expect(useChromeVisibilityStore.getState().visible).toBe(false);

    useChromeVisibilityStore.getState().show();
    expect(useChromeVisibilityStore.getState().visible).toBe(true);
  });
});
