import { describe, expect, it } from "vitest";
import {
  apiPostType,
  buildCreatePostPayload,
  composeKindFromPost,
  validatePostPayload,
} from "./post-type";
import type { Post } from "@/types/post";

function articleJson(text: string) {
  return JSON.stringify({
    type: "doc",
    content: [{ type: "paragraph", content: [{ type: "text", text }] }],
  });
}

describe("compose kinds", () => {
  it("maps audio and quote to article for the API", () => {
    expect(apiPostType("audio")).toBe("article");
    expect(apiPostType("quote")).toBe("article");
    expect(apiPostType("micro")).toBe("micro");
  });

  it("treats quoted posts as quote kind and articles with audio as audio", () => {
    expect(
      composeKindFromPost({
        quoted_post_id: "p1",
        post_type: "article",
      } as Post)
    ).toBe("quote");
    expect(
      composeKindFromPost({
        post_type: "article",
        audio_url: "https://cdn.example/a.mp3",
      } as Post)
    ).toBe("audio");
    expect(composeKindFromPost({ post_type: "photo" } as Post)).toBe("photo");
  });

  it("requires audio file and quoted source", () => {
    expect(
      validatePostPayload({
        postType: "audio",
        title: "عنوان",
        contentJson: articleJson("متن"),
        status: "published",
        isPremium: false,
      })
    ).toBe("فایل صوتی لازم است");
    expect(
      validatePostPayload({
        postType: "quote",
        title: "",
        contentJson: articleJson("نظر"),
        status: "published",
        isPremium: false,
      })
    ).toBe("پست مورد نقل‌قول را انتخاب کنید");
  });

  it("sends quoted_post_id for quote and empty audio_url when cleared", () => {
    const quote = buildCreatePostPayload({
      postType: "quote",
      title: "",
      contentJson: articleJson("نظر من"),
      status: "published",
      isPremium: false,
      quotedPostId: "src-1",
    });
    expect(quote.post_type).toBe("article");
    expect(quote.quoted_post_id).toBe("src-1");
    expect(quote.title).toBe("نظر من");

    const cleared = buildCreatePostPayload({
      postType: "article",
      title: "عنوان",
      contentJson: articleJson("متن"),
      status: "published",
      isPremium: false,
      audioUrl: "",
      clearQuote: true,
    });
    expect(cleared.audio_url).toBe("");
    expect(cleared.quoted_post_id).toBe("");
    expect(cleared.quoted_comment_id).toBe("");
  });
});
