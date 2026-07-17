"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/stores/auth-store";
import { useBrandStore } from "@/stores/brand-store";

const KEY = "xilo_onboarding_done";

export function OnboardingGate({ children }: { children: React.ReactNode }) {
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
    const done = localStorage.getItem(KEY) === "1";
    setShow(isAuthenticated && !done);
    setReady(true);
  }, [isAuthenticated]);

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
          <div className="flex gap-3 justify-center">
            {!last ? (
              <Button className="min-h-11 px-8" onClick={() => setStep((s) => s + 1)}>
                بعدی
              </Button>
            ) : (
              <Button
                className="min-h-11 px-8"
                onClick={() => {
                  localStorage.setItem(KEY, "1");
                  setShow(false);
                }}
              >
                شروع کنیم
              </Button>
            )}
            <Button
              variant="ghost"
              className="min-h-11"
              onClick={() => {
                localStorage.setItem(KEY, "1");
                setShow(false);
              }}
            >
              رد کردن
            </Button>
          </div>
        </div>
      </div>
      {children}
    </>
  );
}
