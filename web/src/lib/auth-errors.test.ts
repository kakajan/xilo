import { describe, expect, it } from "vitest";
import { mapAuthApiError, passwordMeetsServerRules } from "./auth-errors";

describe("passwordMeetsServerRules", () => {
  it("rejects missing uppercase", () => {
    expect(passwordMeetsServerRules("weakpass1!")).toBe(false);
  });
  it("accepts strong password", () => {
    expect(passwordMeetsServerRules("Str0ng!Pass")).toBe(true);
  });
});

describe("mapAuthApiError", () => {
  it("maps uppercase password error to password field", () => {
    const m = mapAuthApiError(
      "password: password must contain at least one uppercase letter (400)"
    );
    expect(m.field).toBe("password");
    expect(m.message).toMatch(/حرف بزرگ/);
  });
  it("maps invalid credentials", () => {
    const m = mapAuthApiError("invalid email or password (401)");
    expect(m.message).toMatch(/نادرست/);
  });
});
