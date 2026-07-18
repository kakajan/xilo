import type { Metadata } from "next";
import { Skeleton } from "@/components/ui/skeleton";

const siteEn = process.env.NEXT_PUBLIC_SITE_NAME_EN || "aile";

export const metadata: Metadata = {
  title: `Write — ${siteEn}`,
};

export default function WriteLoading() {
  return (
    <div className="flex flex-col gap-6 md:flex-row md:items-start md:gap-8">
      <div className="min-w-0 flex-1 space-y-4">
        <Skeleton className="h-10 w-48" />
        <Skeleton className="h-12 w-full" />
        <Skeleton className="h-[500px] w-full rounded-xl" />
      </div>
      <div className="w-full md:w-72 lg:w-80">
        <Skeleton className="h-96 w-full rounded-xl" />
      </div>
    </div>
  );
}
