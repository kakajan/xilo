"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

/** Legacy route — Saved Hub lives at /saved */
export default function BookmarksRedirectPage() {
  const router = useRouter();
  useEffect(() => {
    router.replace("/saved");
  }, [router]);
  return null;
}
