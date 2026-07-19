"use client";

import { useEffect } from "react";
import { NextIntlClientProvider } from "next-intl";
import { getMessages } from "@/i18n/messages";
import { useLocaleStore } from "@/stores/locale-store";
import { useAuthStore } from "@/stores/auth-store";

export function I18nProvider({ children }: { children: React.ReactNode }) {
  const locale = useLocaleStore((s) => s.locale);
  const hydrate = useLocaleStore((s) => s.hydrate);
  const setLocale = useLocaleStore((s) => s.setLocale);
  const preferredLanguage = useAuthStore((s) => s.user?.preferred_language);

  useEffect(() => {
    hydrate(preferredLanguage);
  }, [hydrate, preferredLanguage]);

  useEffect(() => {
    if (preferredLanguage) setLocale(preferredLanguage);
  }, [preferredLanguage, setLocale]);

  return (
    <NextIntlClientProvider
      locale={locale}
      messages={getMessages(locale)}
      timeZone="Asia/Tehran"
    >
      {children}
    </NextIntlClientProvider>
  );
}
