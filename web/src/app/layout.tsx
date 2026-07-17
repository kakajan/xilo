import type { Metadata } from "next";
import { Inter, Vazirmatn } from "next/font/google";
import { Providers } from "@/components/providers";
import { AppShell } from "@/components/layout/app-shell";
import "./globals.css";

const inter = Inter({
  subsets: ["latin"],
  variable: "--font-inter",
  display: "swap",
});

const vazirmatn = Vazirmatn({
  subsets: ["arabic"],
  variable: "--font-vazirmatn",
  display: "swap",
  weight: ["100", "200", "300", "400", "500", "600", "700", "800", "900"],
});

const siteDisplay =
  process.env.NEXT_PUBLIC_SITE_DISPLAY ||
  `${process.env.NEXT_PUBLIC_SITE_NAME_FA || "آیله"} | ${process.env.NEXT_PUBLIC_SITE_NAME_EN || "aile"}`;

export const metadata: Metadata = {
  title: siteDisplay,
  description: "پلتفرم مدرن وبلاگ و گفتگو",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="fa" dir="rtl" suppressHydrationWarning>
      <body className={`${inter.variable} ${vazirmatn.variable} font-sans antialiased`}>
        <Providers>
          <AppShell>{children}</AppShell>
        </Providers>
      </body>
    </html>
  );
}
