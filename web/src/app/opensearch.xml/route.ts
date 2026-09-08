import { NextResponse } from "next/server";
import { canonicalSiteOrigin } from "@/lib/public-url";
import { siteNameFa } from "@/lib/seo";

export function GET() {
  const origin = canonicalSiteOrigin();
  const name = siteNameFa();
  const xml = `<?xml version="1.0" encoding="UTF-8"?>
<OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
  <ShortName>${escapeXml(name)}</ShortName>
  <Description>جستجو در ${escapeXml(name)}</Description>
  <InputEncoding>UTF-8</InputEncoding>
  <Image width="32" height="32" type="image/png">${origin}/brand/aile/favicon-32.png</Image>
  <Url type="text/html" method="get" template="${origin}/search?q={searchTerms}"/>
</OpenSearchDescription>
`;
  return new NextResponse(xml, {
    headers: {
      "Content-Type": "application/opensearchdescription+xml; charset=utf-8",
      "Cache-Control": "public, max-age=86400",
    },
  });
}

function escapeXml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}
