import { Suspense } from "react";
import { PostFeed } from "@/components/post/post-feed";
import { Skeleton } from "@/components/ui/skeleton";

export default function HomePage() {
  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold">فید</h1>
        <p className="mt-1 text-muted-foreground">تازه‌ترین نوشته‌های جامعه</p>
      </div>
      <Suspense fallback={<FeedSkeleton />}>
        <PostFeed />
      </Suspense>
    </div>
  );
}

function FeedSkeleton() {
  return (
    <div className="space-y-8">
      {Array.from({ length: 5 }).map((_, i) => (
        <div key={i} className="space-y-3">
          <div className="flex items-center gap-3">
            <Skeleton className="h-10 w-10 rounded-full" />
            <div>
              <Skeleton className="h-4 w-24" />
              <Skeleton className="mt-1 h-3 w-16" />
            </div>
          </div>
          <Skeleton className="h-6 w-3/4" />
          <Skeleton className="h-4 w-full" />
        </div>
      ))}
    </div>
  );
}
