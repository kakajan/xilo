import type { Metadata } from "next";
import { Skeleton } from "@/components/ui/skeleton";

const siteEn = process.env.NEXT_PUBLIC_SITE_NAME_EN || "aile";

export const metadata: Metadata = {
  title: `Write — ${siteEn}`,
};

export default function WriteLoading() {
  return (
    <div className="lg:flex lg:gap-8">
      <div className="flex-1 space-y-4">
        <Skeleton className="h-10 w-48" />
        <Skeleton className="h-12 w-full" />
        <Skeleton className="h-[500px] w-full rounded-xl" />
      </div>
      <div className="hidden lg:block w-64">
        <Skeleton className="h-96 w-full rounded-xl" />
      </div>
    </div>
  );
}
