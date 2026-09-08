import Link from "next/link";

export default function NotFound() {
  return (
    <div className="py-20 text-center">
      <meta name="robots" content="noindex, nofollow" />
      <h1 className="text-6xl font-bold text-muted-foreground">۴۰۴</h1>
      <p className="mt-4 text-lg text-muted-foreground">صفحه پیدا نشد</p>
      <Link href="/" className="mt-4 inline-block text-primary hover:underline">
        بازگشت به فید
      </Link>
    </div>
  );
}
