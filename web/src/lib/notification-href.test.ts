import { describe, expect, it } from "vitest";
import { hrefForNotification } from "./notification-href";
import type { Notification } from "@/types/notification";

function notif(partial: Partial<Notification> & Pick<Notification, "type" | "data">): Notification {
  return {
    id: "1",
    user_id: "u",
    title: "t",
    body: "b",
    is_read: false,
    created_at: "2026-01-01T00:00:00Z",
    ...partial,
  };
}

describe("hrefForNotification", () => {
  it("builds post comment deep link with author + slug + reply", () => {
    const href = hrefForNotification(
      notif({
        type: "post_comment",
        data: {
          slug: "hello",
          post_author_username: "usher",
          comment_id: "c1",
        },
      })
    );
    expect(href).toBe("/usher/hello?reply=c1");
  });

  it("builds comment reply deep link", () => {
    const href = hrefForNotification(
      notif({
        type: "comment_reply",
        data: {
          post_slug: "hello",
          post_author_username: "usher",
          comment_id: "c2",
        },
      })
    );
    expect(href).toBe("/usher/hello?reply=c2");
  });

  it("does not use bare /{slug} for post_comment without author", () => {
    const href = hrefForNotification(
      notif({
        type: "post_comment",
        data: { slug: "hello", comment_id: "c1" },
      })
    );
    expect(href).toBe("/notifications");
  });
});
