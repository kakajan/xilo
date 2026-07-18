import { resolvePostBodyHTML } from "@/lib/tiptap-content";

interface PostBodyProps {
  content?: string;
  content_md?: string;
  excerpt?: string;
}

export function PostBody({ content, content_md, excerpt }: PostBodyProps) {
  const { html, plain } = resolvePostBodyHTML({ content, content_md, excerpt });

  if (!html && !plain) {
    return (
      <p className="text-muted-foreground">محتوایی برای این پست ثبت نشده است.</p>
    );
  }

  return (
    <div
      className="prose dark:prose-invert mb-8 max-w-none leading-relaxed [&_img]:rounded-lg"
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
}
