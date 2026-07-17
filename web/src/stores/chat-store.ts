import { create } from "zustand";
import type { Chat } from "@/types/chat";

interface ChatState {
  activeChatId: string | null;
  typingUserIds: string[];
  setActiveChat: (id: string | null) => void;
  setTypingUsers: (ids: string[]) => void;
  upsertChatPreview: (chat: Chat) => void;
  chatsCache: Chat[];
  setChatsCache: (chats: Chat[]) => void;
}

export const useChatStore = create<ChatState>()((set) => ({
  activeChatId: null,
  typingUserIds: [],
  chatsCache: [],
  setActiveChat: (id) => set({ activeChatId: id }),
  setTypingUsers: (ids) => set({ typingUserIds: ids }),
  setChatsCache: (chats) => set({ chatsCache: chats }),
  upsertChatPreview: (chat) =>
    set((state) => {
      const rest = state.chatsCache.filter((c) => c.id !== chat.id);
      return { chatsCache: [chat, ...rest] };
    }),
}));
