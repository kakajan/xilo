export default function Loading() {
  return (
    <div className="animate-pulse space-y-8 py-8">
      {[1, 2, 3, 4, 5].map((i) => (
        <div key={i} className="space-y-3">
          <div className="flex items-center gap-3">
            <div className="h-8 w-8 rounded-full bg-muted" />
            <div>
              <div className="h-4 w-24 bg-muted rounded" />
              <div className="h-3 w-16 bg-muted rounded mt-1" />
            </div>
          </div>
          <div className="h-6 w-3/4 bg-muted rounded" />
          <div className="h-4 w-full bg-muted rounded" />
          <div className="h-4 w-1/2 bg-muted rounded" />
        </div>
      ))}
    </div>
  );
}
