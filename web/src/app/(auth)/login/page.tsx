"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import Link from "next/link";
import { useAuthStore } from "@/stores/auth-store";
import { useBrandStore } from "@/stores/brand-store";
import { Button } from "@/components/ui/button";
import { apiFetch } from "@/lib/api-client";
import type { AuthResponse } from "@/types/user";

const loginSchema = z.object({
  email: z.string().email("ایمیل نامعتبر است"),
  password: z.string().min(1, "رمز عبور لازم است"),
});

type LoginForm = z.infer<typeof loginSchema>;

export default function LoginPage() {
  const router = useRouter();
  const { login } = useAuthStore();
  const brandName = useBrandStore((s) => s.brand.name_fa);
  const [mode, setMode] = useState<"password" | "otp">("password");
  const [otpEmail, setOtpEmail] = useState("");
  const [otpCode, setOtpCode] = useState("");
  const [otpSent, setOtpSent] = useState(false);
  const [otpBusy, setOtpBusy] = useState(false);
  const [otpError, setOtpError] = useState<string | null>(null);

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
      setError("root", { message: (err as Error).message || "ورود ناموفق بود" });
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
      setOtpError(e instanceof Error ? e.message : "ارسال کد ناموفق بود");
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
      useAuthStore.setState({
        user: res.user,
        isAuthenticated: true,
        isLoading: false,
      });
      router.push("/");
    } catch (e) {
      setOtpError(e instanceof Error ? e.message : "تأیید کد ناموفق بود");
    } finally {
      setOtpBusy(false);
    }
  };

  return (
    <div className="mx-auto mt-16 max-w-md">
      <h1 className="mb-2 text-2xl font-bold">ورود به {brandName}</h1>
      <p className="mb-6 text-sm text-muted-foreground">با ایمیل و رمز، یا کد یک‌بارمصرف</p>

      <div className="mb-4 flex gap-2">
        <Button
          type="button"
          variant={mode === "password" ? "default" : "outline"}
          className="min-h-11 flex-1"
          onClick={() => setMode("password")}
        >
          رمز عبور
        </Button>
        <Button
          type="button"
          variant={mode === "otp" ? "default" : "outline"}
          className="min-h-11 flex-1"
          onClick={() => setMode("otp")}
        >
          کد یک‌بارمصرف
        </Button>
      </div>

      {mode === "password" ? (
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <label className="mb-1 block text-sm font-medium">ایمیل</label>
            <input
              {...register("email")}
              type="email"
              className="w-full min-h-11 rounded-lg border bg-background px-3 py-2"
              placeholder="you@example.com"
              dir="ltr"
            />
            {errors.email && (
              <p className="mt-1 text-sm text-destructive">{errors.email.message}</p>
            )}
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium">رمز عبور</label>
            <input
              {...register("password")}
              type="password"
              className="w-full min-h-11 rounded-lg border bg-background px-3 py-2"
              placeholder="••••••••"
              dir="ltr"
            />
            {errors.password && (
              <p className="mt-1 text-sm text-destructive">{errors.password.message}</p>
            )}
          </div>
          {errors.root && <p className="text-sm text-destructive">{errors.root.message}</p>}
          <Button type="submit" className="w-full min-h-11" disabled={isSubmitting}>
            {isSubmitting ? "در حال ورود..." : "ورود"}
          </Button>
        </form>
      ) : (
        <div className="space-y-4">
          <div>
            <label className="mb-1 block text-sm font-medium">ایمیل</label>
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
              <label className="mb-1 block text-sm font-medium">کد</label>
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
              ارسال کد
            </Button>
          ) : (
            <Button
              type="button"
              className="w-full min-h-11"
              disabled={otpBusy || !otpCode}
              onClick={() => void verifyOtp()}
            >
              تأیید و ورود
            </Button>
          )}
        </div>
      )}

      <p className="mt-4 text-center text-sm text-muted-foreground">
        حساب ندارید؟{" "}
        <Link href="/register" className="text-primary hover:underline">
          ثبت‌نام
        </Link>
      </p>
    </div>
  );
}
