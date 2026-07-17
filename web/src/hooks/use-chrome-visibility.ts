"use client";

import { useCallback, useEffect, useRef, useState } from "react";

/** Hide chrome on scroll-down, show on scroll-up (Android ChromeVisibility). */
export function useChromeVisibility(threshold = 8) {
  const [visible, setVisible] = useState(true);
  const lastY = useRef(0);

  useEffect(() => {
    lastY.current = window.scrollY;
    const onScroll = () => {
      const y = window.scrollY;
      const delta = y - lastY.current;
      if (Math.abs(delta) < threshold) return;
      setVisible(delta < 0 || y < 48);
      lastY.current = y;
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, [threshold]);

  const show = useCallback(() => setVisible(true), []);

  return { visible, show };
}
