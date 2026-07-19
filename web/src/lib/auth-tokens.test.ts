import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

function memoryStorage(): Storage {
  const map = new Map<string, string>();
  return {
    get length() {
      return map.size;
    },
    clear: () => map.clear(),
    getItem: (key: string) => (map.has(key) ? map.get(key)! : null),
    key: (index: number) => [...map.keys()][index] ?? null,
    removeItem: (key: string) => {
      map.delete(key);
    },
    setItem: (key: string, value: string) => {
      map.set(key, value);
    },
  };
}

describe("auth-tokens", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.stubGlobal("window", {});
    vi.stubGlobal("localStorage", memoryStorage());
    vi.stubGlobal("sessionStorage", memoryStorage());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("stores and reads tokens from localStorage", async () => {
    const { setAuthTokens, getAccessToken, getRefreshToken } = await import("./auth-tokens");
    setAuthTokens("access-1", "refresh-1");
    expect(getAccessToken()).toBe("access-1");
    expect(getRefreshToken()).toBe("refresh-1");
    expect(localStorage.getItem("xilo_access_token")).toBe("access-1");
    expect(localStorage.getItem("xilo_refresh_token")).toBe("refresh-1");
  });

  it("migrates legacy sessionStorage tokens once", async () => {
    sessionStorage.setItem("xilo_access_token", "legacy-access");
    sessionStorage.setItem("xilo_refresh_token", "legacy-refresh");
    const { getAccessToken, getRefreshToken } = await import("./auth-tokens");

    expect(getAccessToken()).toBe("legacy-access");
    expect(getRefreshToken()).toBe("legacy-refresh");
    expect(localStorage.getItem("xilo_access_token")).toBe("legacy-access");
    expect(sessionStorage.getItem("xilo_access_token")).toBeNull();
  });

  it("clears both storages", async () => {
    const { setAuthTokens, clearAuthTokens, getAccessToken, getRefreshToken } =
      await import("./auth-tokens");
    setAuthTokens("a", "r");
    sessionStorage.setItem("xilo_access_token", "stale");
    clearAuthTokens();
    expect(getAccessToken()).toBeNull();
    expect(getRefreshToken()).toBeNull();
    expect(sessionStorage.getItem("xilo_access_token")).toBeNull();
  });
});
