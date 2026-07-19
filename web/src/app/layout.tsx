import type { Metadata } from "next";
import { Providers } from "@/components/providers";
import { AppShell } from "@/components/layout/app-shell";
import "@fontsource-variable/vazirmatn";
import "@fontsource/inter/400.css";
import "@fontsource/inter/500.css";
import "@fontsource/inter/600.css";
import "@fontsource/inter/700.css";
import "./globals.css";

const siteDisplay =
  process.env.NEXT_PUBLIC_SITE_DISPLAY ||
  `${process.env.NEXT_PUBLIC_SITE_NAME_FA || "آیله"} | ${process.env.NEXT_PUBLIC_SITE_NAME_EN || "aile"}`;

export const metadata: Metadata = {
  title: siteDisplay,
  description: "پلتفرم مدرن وبلاگ و گفتگو",
  icons: {
    icon: [
      { url: "/brand/aile/favicon-32.png", sizes: "32x32", type: "image/png" },
      { url: "/brand/aile/app-icon-192.png", sizes: "192x192", type: "image/png" },
    ],
    apple: [{ url: "/brand/aile/app-icon-192.png", sizes: "192x192", type: "image/png" }],
  },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="fa" dir="rtl" suppressHydrationWarning>
      <body className="font-sans antialiased">
        <Providers>
          <AppShell>{children}</AppShell>
        </Providers>
      </body>
    </html>
  );
}
