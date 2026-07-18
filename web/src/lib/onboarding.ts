const PENDING_KEY = "xilo_onboarding_pending";

/** Mark that the next authenticated shell visit should show welcome slides (post-register only). */
export function markOnboardingPending() {
  if (typeof window === "undefined") return;
  localStorage.setItem(PENDING_KEY, "1");
}

export function isOnboardingPending(): boolean {
  if (typeof window === "undefined") return false;
  return localStorage.getItem(PENDING_KEY) === "1";
}

export function clearOnboardingPending() {
  if (typeof window === "undefined") return;
  localStorage.removeItem(PENDING_KEY);
}
