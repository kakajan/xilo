import { useState, useCallback } from "react";

export function useDebounce<T extends (...args: unknown[]) => unknown>(fn: T, delay: number) {
  const [timeoutId, setTimeoutId] = useState<ReturnType<typeof setTimeout>>();

  return useCallback(
    (...args: Parameters<T>) => {
      if (timeoutId) clearTimeout(timeoutId);
      const id = setTimeout(() => fn(...args), delay);
      setTimeoutId(id);
    },
    [fn, delay]
  );
}
