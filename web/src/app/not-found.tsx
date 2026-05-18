import Link from "next/link";

export default function NotFound() {
  return (
    <div className="text-center py-20">
      <h1 className="text-6xl font-bold text-muted-foreground">404</h1>
      <p className="text-lg text-muted-foreground mt-4">Page not found</p>
      <Link href="/" className="text-primary hover:underline mt-4 inline-block">
        Go home
      </Link>
    </div>
  );
}
