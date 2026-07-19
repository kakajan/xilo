import { describe, expect, it } from "vitest";
import { buildRealtimeEnvelope } from "./realtime-socket";

describe("buildRealtimeEnvelope", () => {
  it("builds a versioned chat.join envelope", () => {
    const raw = buildRealtimeEnvelope("chat.join", {
      chat_id: "11111111-1111-1111-1111-111111111111",
    });
    const parsed = JSON.parse(raw) as {
      version: string;
      event: string;
      request_id: string;
      data: { chat_id: string };
    };

    expect(parsed.version).toBe("1");
    expect(parsed.event).toBe("chat.join");
    expect(parsed.request_id).toBeTruthy();
    expect(parsed.data.chat_id).toBe("11111111-1111-1111-1111-111111111111");
  });
});
