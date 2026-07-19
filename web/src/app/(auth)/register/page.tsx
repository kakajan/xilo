"use client";

import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import Link from "next/link";
import { useAuthStore } from "@/stores/auth-store";
import { BrandLogo } from "@/components/brand/brand-logo";
import { Button } from "@/components/ui/button";
import { useTranslations } from "next-intl";
import { mapAuthApiError, passwordMeetsServerRules, passwordRulesHint } from "@/lib/auth-errors";

const registerSchema = z.object({
  email: z.string().email("ایمیل نامعتبر است"),
  password: z
    .string()
    .min(8, "رمز عبور باید حداقل ۸ کاراکتر باشد.")
    .refine(passwordMeetsServerRules, {
      message: passwordRulesHint(),
    }),
});

type RegisterForm = z.infer<typeof registerSchema>;

export default function RegisterPage() {
  const t = useTranslations("auth.register");
  const router = useRouter();
  const { register: registerUser } = useAuthStore();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterForm>({ resolver: zodResolver(registerSchema) });

  const onSubmit = async (data: RegisterForm) => {
    try {
      await registerUser({ email: data.email, password: data.password });
      router.push("/settings/username");
    } catch (err) {
      const mapped = mapAuthApiError((err as Error).message || "");
      if (mapped.field) {
        setError(mapped.field, { message: mapped.message });
      } else {
        setError("root", { message: mapped.message });
      }
    }
  };

  return (
    <div className="mx-auto mt-16 max-w-md">
      <div className="mb-6 flex flex-col items-center text-center">
        <BrandLogo variant="wordmark" className="mb-3 h-14 w-auto" alt="aile" />
        <h1 className="sr-only">{t("title", { brand: "aile" })}</h1>
        <p className="text-sm text-muted-foreground">
          فقط ایمیل و رمز کافی است. نام کاربری را بعد از ورود خودتان انتخاب می‌کنید.
        </p>
      </div>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <label className="mb-1 block text-sm font-medium">ایمیل</label>
          <input
            {...register("email")}
            type="email"
            className="w-full min-h-11 rounded-lg border bg-background px-3 py-2"
            placeholder="you@example.com"
            dir="ltr"
            autoComplete="email"
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
            autoComplete="new-password"
          />
          <p className="mt-1 text-xs text-muted-foreground">{passwordRulesHint()}</p>
          {errors.password && (
            <p className="mt-1 text-sm text-destructive">{errors.password.message}</p>
          )}
        </div>
        {errors.root && <p className="text-sm text-destructive">{errors.root.message}</p>}
        <Button type="submit" className="w-full min-h-11" disabled={isSubmitting}>
          {isSubmitting ? "در حال ساخت..." : "ثبت‌نام"}
        </Button>
      </form>
      <p className="mt-4 text-center text-sm text-muted-foreground">
        حساب دارید؟{" "}
        <Link href="/login" className="text-primary hover:underline">
          ورود
        </Link>
      </p>
    </div>
  );
}
