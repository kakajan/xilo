import { apiFetch } from "@/lib/api-client";

export type CommentVote = "like" | "dislike";

export function commentLikeCount(reactions?: Record<string, number>, fallback = 0) {
  return reactions?.like ?? reactions?.heart ?? fallback;
}

export function commentDislikeCount(reactions?: Record<string, number>, fallback = 0) {
  return reactions?.dislike ?? fallback;
}

export function hasViewerReaction(
  viewerReactions: string[] | undefined,
  key: CommentVote | "heart"
) {
  if (!viewerReactions?.length) return false;
  if (key === "like" || key === "heart") {
    return viewerReactions.includes("like") || viewerReactions.includes("heart");
  }
  return viewerReactions.includes(key);
}

/** Toggle like/dislike with mutual exclusivity (Android CommentRepository parity). */
export async function toggleCommentVote(
  commentId: string,
  reaction: CommentVote,
  currentlyActive: boolean,
  oppositeActive: boolean
) {
  const opposite: CommentVote = reaction === "like" ? "dislike" : "like";
  if (oppositeActive && !currentlyActive) {
    await apiFetch(`/api/comment/${commentId}/reactions`, {
      method: "POST",
      body: JSON.stringify({ reaction: opposite }),
    });
  }
  await apiFetch(`/api/comment/${commentId}/reactions`, {
    method: "POST",
    body: JSON.stringify({ reaction }),
  });
}

export function nextVoteState(
  reaction: CommentVote,
  currentlyActive: boolean,
  oppositeActive: boolean,
  likeCount: number,
  dislikeCount: number
) {
  const becomingActive = !currentlyActive;
  if (reaction === "like") {
    return {
      liked: becomingActive,
      disliked: becomingActive ? false : oppositeActive,
      likeCount: Math.max(0, likeCount + (becomingActive ? 1 : -1)),
      dislikeCount:
        becomingActive && oppositeActive
          ? Math.max(0, dislikeCount - 1)
          : dislikeCount,
    };
  }
  return {
    liked: becomingActive ? false : oppositeActive,
    disliked: becomingActive,
    dislikeCount: Math.max(0, dislikeCount + (becomingActive ? 1 : -1)),
    likeCount:
      becomingActive && oppositeActive ? Math.max(0, likeCount - 1) : likeCount,
  };
}
