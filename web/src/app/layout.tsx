import type { Metadata } from "next";
import { Providers } from "@/components/providers";
import { AppShell } from "@/components/layout/app-shell";
import { canonicalSiteOrigin } from "@/lib/public-url";
import { getWebsiteJsonLd, jsonLdString, siteNameFa } from "@/lib/seo";
import "@fontsource-variable/vazirmatn";
import "@fontsource/inter/400.css";
import "@fontsource/inter/500.css";
import "@fontsource/inter/600.css";
import "@fontsource/inter/700.css";
import "./globals.css";

const siteDisplay =
  process.env.NEXT_PUBLIC_SITE_DISPLAY ||
  `${process.env.NEXT_PUBLIC_SITE_NAME_FA || "آیله"} | ${process.env.NEXT_PUBLIC_SITE_NAME_EN || "aile"}`;

const siteUrl = canonicalSiteOrigin();

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: {
    default: siteDisplay,
    template: `%s — ${siteNameFa()}`,
  },
  description: "پلتفرم مدرن وبلاگ و گفتگو",
  openGraph: {
    type: "website",
    locale: "fa_IR",
    siteName: siteNameFa(),
  },
  twitter: {
    card: "summary_large_image",
  },
  alternates: {
    types: {
      "application/opensearchdescription+xml": "/opensearch.xml",
    },
  },
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
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: jsonLdString(getWebsiteJsonLd()) }}
        />
        <Providers>
          <AppShell>{children}</AppShell>
        </Providers>
      </body>
    </html>
  );
}
