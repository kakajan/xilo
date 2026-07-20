/** Map API auth error strings to Persian field messages. */

const PASSWORD_RULES_HINT =
  "رمز عبور باید حداقل ۸ کاراکتر و شامل حرف بزرگ انگلیسی، عدد و کاراکتر ویژه باشد.";

export function passwordRulesHint(): string {
  return PASSWORD_RULES_HINT;
}

/** Zod-friendly checks matching backend/pkg/validator.ValidatePassword */
export function passwordMeetsServerRules(password: string): boolean {
  if (password.length < 8) return false;
  let hasUpper = false;
  let hasNumber = false;
  let hasSpecial = false;
  for (const ch of password) {
    if (ch >= "A" && ch <= "Z") hasUpper = true;
    else if (ch >= "0" && ch <= "9") hasNumber = true;
    else if (/[\p{P}\p{S}]/u.test(ch)) hasSpecial = true;
  }
  return hasUpper && hasNumber && hasSpecial;
}

export function mapAuthApiError(raw: string): {
  field?: "email" | "password";
  message: string;
} {
  const msg = raw.replace(/\s*\(\d+\)\s*$/, "").trim();
  const lower = msg.toLowerCase();

  if (lower.includes("uppercase")) {
    return { field: "password", message: "رمز عبور باید حداقل یک حرف بزرگ انگلیسی داشته باشد." };
  }
  if (lower.includes("at least 8") || lower.includes("8 characters")) {
    return { field: "password", message: "رمز عبور باید حداقل ۸ کاراکتر باشد." };
  }
  if (lower.includes("one number") || lower.includes("at least one number")) {
    return { field: "password", message: "رمز عبور باید حداقل یک عدد داشته باشد." };
  }
  if (lower.includes("special character")) {
    return { field: "password", message: "رمز عبور باید حداقل یک کاراکتر ویژه داشته باشد." };
  }
  if (lower.startsWith("password:") || lower.includes("password must")) {
    return { field: "password", message: PASSWORD_RULES_HINT };
  }
  if (lower.includes("invalid email or password") || lower.includes("login failed")) {
    return { message: "ایمیل یا رمز عبور نادرست است." };
  }
  if (lower.includes("email") && (lower.includes("invalid") || lower.includes("already"))) {
    return {
      field: "email",
      message: lower.includes("already")
        ? "این ایمیل قبلاً ثبت شده است."
        : "ایمیل نامعتبر است.",
    };
  }
  if (lower.includes("registration failed") || msg === "registration failed") {
    return { message: "ثبت‌نام انجام نشد. لطفاً دوباره تلاش کنید." };
  }
  return { message: msg || "خطایی رخ داد." };
}
