"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { useAuthStore } from "@/stores/auth-store";
import { useBrandStore } from "@/stores/brand-store";
import { Button } from "@/components/ui/button";
import { apiFetch } from "@/lib/api-client";
import { mapAuthApiError } from "@/lib/auth-errors";
import type { AuthResponse } from "@/types/user";

type LoginForm = {
  email: string;
  password: string;
};

export default function LoginPage() {
  const t = useTranslations("auth.login");
  const router = useRouter();
  const { login } = useAuthStore();
  const brandName = useBrandStore((s) => s.brand.name_fa);
  const [mode, setMode] = useState<"password" | "otp">("password");
  const [otpEmail, setOtpEmail] = useState("");
  const [otpCode, setOtpCode] = useState("");
  const [otpSent, setOtpSent] = useState(false);
  const [otpBusy, setOtpBusy] = useState(false);
  const [otpError, setOtpError] = useState<string | null>(null);

  const loginSchema = useMemo(
    () =>
      z.object({
        email: z.string().min(1, t("emailRequired")),
        password: z.string().min(1, t("passwordRequired")),
      }),
    [t]
  );

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({ resolver: zodResolver(loginSchema) });

  const onSubmit = async (data: LoginForm) => {
    try {
      await login(data);
      router.push("/");
    } catch (err) {
      const mapped = mapAuthApiError((err as Error).message || "");
      setError("root", { message: mapped.message || t("failed") });
    }
  };

  const requestOtp = async () => {
    setOtpBusy(true);
    setOtpError(null);
    try {
      await apiFetch("/api/auth/otp/request", {
        method: "POST",
        body: JSON.stringify({ email: otpEmail }),
      });
      setOtpSent(true);
    } catch (e) {
      const raw = e instanceof Error ? e.message : "";
      const lower = raw.toLowerCase();
      if (
        lower.includes("sms") ||
        lower.includes("ippanel") ||
        lower.includes("not initialized") ||
        lower.includes("not configured") ||
        lower.includes("unavailable")
      ) {
        setOtpError(t("otpUnavailable"));
      } else {
        setOtpError(mapAuthApiError(raw).message || t("sendCodeFailed"));
      }
    } finally {
      setOtpBusy(false);
    }
  };

  const verifyOtp = async () => {
    setOtpBusy(true);
    setOtpError(null);
    try {
      const res = await apiFetch<AuthResponse>("/api/auth/otp/verify-login", {
        method: "POST",
        body: JSON.stringify({ email: otpEmail, code: otpCode }),
      });
      useAuthStore.getState().applyAuthResponse(res);
      router.push("/");
    } catch (e) {
      setOtpError(e instanceof Error ? e.message : t("verifyFailed"));
    } finally {
      setOtpBusy(false);
    }
  };

  return (
    <div className="mx-auto mt-16 max-w-md">
      <h1 className="mb-2 text-2xl font-bold">{t("title", { brand: brandName })}</h1>
      <p className="mb-6 text-sm text-muted-foreground">{t("subtitle")}</p>

      <div className="mb-4 flex gap-2">
        <Button
          type="button"
          variant={mode === "password" ? "default" : "outline"}
          className="min-h-11 flex-1"
          onClick={() => setMode("password")}
        >
          {t("passwordTab")}
        </Button>
        <Button
          type="button"
          variant={mode === "otp" ? "default" : "outline"}
          className="min-h-11 flex-1"
          onClick={() => setMode("otp")}
        >
          {t("otpTab")}
        </Button>
      </div>

      {mode === "password" ? (
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <label className="mb-1 block text-sm font-medium">{t("emailOrUsername")}</label>
            <input
              {...register("email")}
              type="text"
              className="w-full min-h-11 rounded-lg border bg-background px-3 py-2"
              placeholder={t("emailOrUsernamePlaceholder")}
              dir="ltr"
              autoComplete="username"
            />
            {errors.email && (
              <p className="mt-1 text-sm text-destructive">{errors.email.message}</p>
            )}
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium">{t("password")}</label>
            <input
              {...register("password")}
              type="password"
              className="w-full min-h-11 rounded-lg border bg-background py-2 px-3"
              placeholder="••••••••"
              dir="ltr"
            />
            {errors.password && (
              <p className="mt-1 text-sm text-destructive">{errors.password.message}</p>
            )}
          </div>
          {errors.root && <p className="text-sm text-destructive">{errors.root.message}</p>}
          <Button type="submit" className="w-full min-h-11" disabled={isSubmitting}>
            {isSubmitting ? t("submitting") : t("submit")}
          </Button>
        </form>
      ) : (
        <div className="space-y-4">
          <div>
            <label className="mb-1 block text-sm font-medium">{t("email")}</label>
            <input
              type="email"
              value={otpEmail}
              onChange={(e) => setOtpEmail(e.target.value)}
              className="w-full min-h-11 rounded-lg border bg-background px-3 py-2"
              dir="ltr"
            />
          </div>
          {otpSent && (
            <div>
              <label className="mb-1 block text-sm font-medium">{t("code")}</label>
              <input
                value={otpCode}
                onChange={(e) => setOtpCode(e.target.value)}
                className="w-full min-h-11 rounded-lg border bg-background px-3 py-2 tracking-widest"
                dir="ltr"
              />
            </div>
          )}
          {otpError && <p className="text-sm text-destructive">{otpError}</p>}
          {!otpSent ? (
            <Button
              type="button"
              className="w-full min-h-11"
              disabled={otpBusy || !otpEmail}
              onClick={() => void requestOtp()}
            >
              {t("sendCode")}
            </Button>
          ) : (
            <Button
              type="button"
              className="w-full min-h-11"
              disabled={otpBusy || !otpCode}
              onClick={() => void verifyOtp()}
            >
              {t("verifyAndLogin")}
            </Button>
          )}
        </div>
      )}

      <p className="mt-4 text-center text-sm text-muted-foreground">
        {t("noAccount")}{" "}
        <Link href="/register" className="text-primary hover:underline">
          {t("registerLink")}
        </Link>
      </p>
    </div>
  );
}
