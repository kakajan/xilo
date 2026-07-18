"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/stores/auth-store";
import { useBrandStore } from "@/stores/brand-store";
import { clearOnboardingPending, isOnboardingPending } from "@/lib/onboarding";

export function OnboardingGate({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const brandName = useBrandStore((s) => s.brand.name_fa);
  const slides = [
    {
      title: `به ${brandName} خوش آمدید`,
      body: "فید شخصی‌سازی‌شده از نوشته‌ها و گفتگوهای جامعه.",
    },
    {
      title: "اکتشاف",
      body: "نظرات برجسته را مثل توییت کشف کنید و وارد گفتگو شوید.",
    },
    {
      title: "پیام‌ها",
      body: "گفتگوی مستقیم با نویسندگان — ساده و شبیه تلگرام.",
    },
  ];
  const [ready, setReady] = useState(false);
  const [step, setStep] = useState(0);
  const [show, setShow] = useState(false);

  useEffect(() => {
    // Only after first registration (pending flag), never on ordinary login.
    // Defer until the user leaves /settings so username onboarding is not blocked.
    const deferForSettings = pathname.startsWith("/settings");
    setShow(isAuthenticated && isOnboardingPending() && !deferForSettings);
    setReady(true);
  }, [isAuthenticated, pathname]);

  const dismiss = () => {
    clearOnboardingPending();
    setShow(false);
  };

  if (!ready) return <>{children}</>;
  if (!show) return <>{children}</>;

  const slide = slides[step];
  const last = step === slides.length - 1;

  return (
    <>
      <div className="fixed inset-0 z-[100] flex items-center justify-center bg-background/95 p-6 backdrop-blur-sm">
        <div className="w-full max-w-md space-y-6 text-center">
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-primary text-2xl font-bold text-primary-foreground">
            {brandName.charAt(0)}
          </div>
          <h1 className="text-2xl font-bold">{slide.title}</h1>
          <p className="text-muted-foreground leading-relaxed">{slide.body}</p>
          <div className="flex justify-center gap-2">
            {slides.map((_, i) => (
              <span
                key={i}
                className={`h-2 w-2 rounded-full ${i === step ? "bg-primary" : "bg-muted"}`}
              />
            ))}
          </div>
          <div className="flex justify-center gap-3">
            {!last ? (
              <Button className="min-h-11 px-8" onClick={() => setStep((s) => s + 1)}>
                بعدی
              </Button>
            ) : (
              <Button className="min-h-11 px-8" onClick={dismiss}>
                شروع کنیم
              </Button>
            )}
            <Button variant="ghost" className="min-h-11" onClick={dismiss}>
              رد کردن
            </Button>
          </div>
        </div>
      </div>
      {children}
    </>
  );
}
