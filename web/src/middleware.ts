import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { isCrawlerJunkPath, isNoIndexPath } from "@/lib/public-url";

export function middleware(request: NextRequest) {
  const url = request.nextUrl.clone();
  const host = request.headers.get("host")?.split(":")[0]?.toLowerCase() || "";

  if (host === "www.aile.ir") {
    url.hostname = "aile.ir";
    url.protocol = "https:";
    url.port = "";
    return NextResponse.redirect(url, 301);
  }

  const proto = request.headers.get("x-forwarded-proto");
  if (host === "aile.ir" && proto === "http") {
    url.protocol = "https:";
    url.port = "";
    return NextResponse.redirect(url, 301);
  }

  const response = NextResponse.next();
  if (isNoIndexPath(url.pathname) || isCrawlerJunkPath(url.pathname)) {
    response.headers.set("X-Robots-Tag", "noindex, nofollow");
  }
  return response;
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|brand/|.*\\.(?:png|jpg|svg|webp|ico)$).*)"],
};
