"use client";

import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import Link from "next/link";
import { useAuthStore } from "@/stores/auth-store";
import { useBrandStore } from "@/stores/brand-store";
import { Button } from "@/components/ui/button";

const registerSchema = z.object({
  username: z.string().min(3, "حداقل ۳ کاراکتر").max(32),
  email: z.string().email("ایمیل نامعتبر است"),
  password: z.string().min(8, "حداقل ۸ کاراکتر"),
});

type RegisterForm = z.infer<typeof registerSchema>;

export default function RegisterPage() {
  const router = useRouter();
  const { register: registerUser } = useAuthStore();
  const brandName = useBrandStore((s) => s.brand.name_fa);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterForm>({ resolver: zodResolver(registerSchema) });

  const onSubmit = async (data: RegisterForm) => {
    try {
      await registerUser(data);
      router.push("/");
    } catch (err) {
      setError("root", { message: (err as Error).message });
    }
  };

  return (
    <div className="mx-auto mt-16 max-w-md">
      <h1 className="mb-6 text-2xl font-bold">ساخت حساب {brandName}</h1>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <label className="mb-1 block text-sm font-medium">نام کاربری</label>
          <input
            {...register("username")}
            className="w-full min-h-11 rounded-lg border bg-background px-3 py-2"
            placeholder="alice"
            dir="ltr"
          />
          {errors.username && (
            <p className="mt-1 text-sm text-destructive">{errors.username.message}</p>
          )}
        </div>
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
