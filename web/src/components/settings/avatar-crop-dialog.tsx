"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  baseScale,
  canvasToPngBlob,
  clampOffset,
  clampUserScale,
  loadImageForCrop,
  renderSquareCrop,
} from "@/lib/avatar-crop-math";

type AvatarCropDialogProps = {
  file: File;
  onDismiss: () => void;
  onConfirm: (blob: Blob) => void;
};

type PointerState = {
  pointers: Map<number, { x: number; y: number }>;
  lastPinchDist: number | null;
};

export function AvatarCropDialog({ file, onDismiss, onConfirm }: AvatarCropDialogProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const imageRef = useRef<HTMLImageElement | null>(null);
  const transformRef = useRef({ scale: 1, offsetX: 0, offsetY: 0 });
  const cropDiameterRef = useRef(0);
  const pointerState = useRef<PointerState>({ pointers: new Map(), lastPinchDist: null });
  const dragRef = useRef<{ x: number; y: number } | null>(null);

  const [loadFailed, setLoadFailed] = useState(false);
  const [ready, setReady] = useState(false);
  const [isExporting, setIsExporting] = useState(false);

  const paint = useCallback(() => {
    const canvas = canvasRef.current;
    const img = imageRef.current;
    const container = containerRef.current;
    if (!canvas || !img || !container) return;

    const dpr = window.devicePixelRatio || 1;
    const w = container.clientWidth;
    const h = container.clientHeight;
    if (w <= 0 || h <= 0) return;

    canvas.width = Math.round(w * dpr);
    canvas.height = Math.round(h * dpr);
    canvas.style.width = `${w}px`;
    canvas.style.height = `${h}px`;

    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    const cropDiameter = Math.min(w, h) * 0.72;
    cropDiameterRef.current = cropDiameter;
    const { scale, offsetX, offsetY } = transformRef.current;
    const displayScale = baseScale(img.naturalWidth, img.naturalHeight, cropDiameter) * scale;
    const drawnW = img.naturalWidth * displayScale;
    const drawnH = img.naturalHeight * displayScale;
    const left = (w - drawnW) / 2 + offsetX;
    const top = (h - drawnH) / 2 + offsetY;

    ctx.clearRect(0, 0, w, h);
    ctx.fillStyle = "#000";
    ctx.fillRect(0, 0, w, h);
    ctx.drawImage(img, left, top, drawnW, drawnH);

    const cx = w / 2;
    const cy = h / 2;
    const radius = cropDiameter / 2;

    ctx.save();
    ctx.beginPath();
    ctx.rect(0, 0, w, h);
    ctx.arc(cx, cy, radius, 0, Math.PI * 2, true);
    ctx.fillStyle = "rgba(0,0,0,0.62)";
    ctx.fill("evenodd");
    ctx.restore();

    ctx.beginPath();
    ctx.arc(cx, cy, radius, 0, Math.PI * 2);
    ctx.strokeStyle = "rgba(255,255,255,0.9)";
    ctx.lineWidth = 2;
    ctx.stroke();
  }, []);

  const applyTransform = useCallback(
    (nextScale: number, nextX: number, nextY: number) => {
      const img = imageRef.current;
      if (!img) return;
      const scale = clampUserScale(nextScale);
      const clamped = clampOffset(
        nextX,
        nextY,
        img.naturalWidth,
        img.naturalHeight,
        cropDiameterRef.current,
        scale
      );
      transformRef.current = { scale, offsetX: clamped.x, offsetY: clamped.y };
      paint();
    },
    [paint]
  );

  useEffect(() => {
    let cancelled = false;
    setLoadFailed(false);
    setReady(false);
    transformRef.current = { scale: 1, offsetX: 0, offsetY: 0 };

    void loadImageForCrop(file)
      .then((img) => {
        if (cancelled) return;
        imageRef.current = img;
        setReady(true);
        requestAnimationFrame(paint);
      })
      .catch(() => {
        if (!cancelled) setLoadFailed(true);
      });

    return () => {
      cancelled = true;
      imageRef.current = null;
    };
  }, [file, paint]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container || !ready) return;
    const ro = new ResizeObserver(() => {
      const img = imageRef.current;
      if (!img) return;
      const w = container.clientWidth;
      const h = container.clientHeight;
      cropDiameterRef.current = Math.min(w, h) * 0.72;
      applyTransform(
        transformRef.current.scale,
        transformRef.current.offsetX,
        transformRef.current.offsetY
      );
    });
    ro.observe(container);
    paint();
    return () => ro.disconnect();
  }, [ready, paint, applyTransform]);

  useEffect(() => {
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && !isExporting) onDismiss();
    };
    window.addEventListener("keydown", onKey);
    return () => {
      document.body.style.overflow = prev;
      window.removeEventListener("keydown", onKey);
    };
  }, [isExporting, onDismiss]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !ready) return;
    const onWheelNative = (e: WheelEvent) => {
      if (isExporting) return;
      e.preventDefault();
      const zoom = e.deltaY < 0 ? 1.08 : 1 / 1.08;
      applyTransform(
        transformRef.current.scale * zoom,
        transformRef.current.offsetX,
        transformRef.current.offsetY
      );
    };
    canvas.addEventListener("wheel", onWheelNative, { passive: false });
    return () => canvas.removeEventListener("wheel", onWheelNative);
  }, [ready, isExporting, applyTransform]);

  const onPointerDown = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (isExporting || !ready) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    pointerState.current.pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (pointerState.current.pointers.size === 1) {
      dragRef.current = { x: e.clientX, y: e.clientY };
      pointerState.current.lastPinchDist = null;
    } else if (pointerState.current.pointers.size === 2) {
      dragRef.current = null;
      const pts = [...pointerState.current.pointers.values()];
      pointerState.current.lastPinchDist = Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y);
    }
  };

  const onPointerMove = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!pointerState.current.pointers.has(e.pointerId)) return;
    pointerState.current.pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });

    if (pointerState.current.pointers.size === 2) {
      const pts = [...pointerState.current.pointers.values()];
      const dist = Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y);
      const last = pointerState.current.lastPinchDist;
      if (last && last > 0) {
        const zoom = dist / last;
        applyTransform(
          transformRef.current.scale * zoom,
          transformRef.current.offsetX,
          transformRef.current.offsetY
        );
      }
      pointerState.current.lastPinchDist = dist;
      return;
    }

    const drag = dragRef.current;
    if (!drag) return;
    const dx = e.clientX - drag.x;
    const dy = e.clientY - drag.y;
    dragRef.current = { x: e.clientX, y: e.clientY };
    applyTransform(
      transformRef.current.scale,
      transformRef.current.offsetX + dx,
      transformRef.current.offsetY + dy
    );
  };

  const onPointerUp = (e: React.PointerEvent<HTMLCanvasElement>) => {
    pointerState.current.pointers.delete(e.pointerId);
    if (pointerState.current.pointers.size < 2) {
      pointerState.current.lastPinchDist = null;
    }
    if (pointerState.current.pointers.size === 0) {
      dragRef.current = null;
    } else if (pointerState.current.pointers.size === 1) {
      const remaining = [...pointerState.current.pointers.values()][0];
      dragRef.current = { x: remaining.x, y: remaining.y };
    }
  };

  const handleConfirm = async () => {
    const img = imageRef.current;
    if (!img || isExporting) return;
    setIsExporting(true);
    try {
      const { scale, offsetX, offsetY } = transformRef.current;
      const cropped = renderSquareCrop(img, cropDiameterRef.current, scale, offsetX, offsetY);
      const blob = await canvasToPngBlob(cropped);
      onConfirm(blob);
    } catch {
      setIsExporting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-[200] flex flex-col bg-black"
      role="dialog"
      aria-modal="true"
      aria-labelledby="avatar-crop-title"
    >
      <div className="relative min-h-0 flex-1" ref={containerRef}>
        {loadFailed ? (
          <div className="flex h-full flex-col items-center justify-center gap-4 px-6 text-center text-white">
            <p>بارگذاری تصویر ناموفق بود</p>
            <button
              type="button"
              onClick={onDismiss}
              className="rounded-lg px-4 py-2 text-sm text-white hover:bg-white/10"
            >
              انصراف
            </button>
          </div>
        ) : !ready ? (
          <div className="flex h-full items-center justify-center">
            <div className="h-8 w-8 animate-spin rounded-full border-2 border-white/30 border-t-white" />
          </div>
        ) : (
          <canvas
            ref={canvasRef}
            className="absolute inset-0 h-full w-full touch-none cursor-grab active:cursor-grabbing"
            onPointerDown={onPointerDown}
            onPointerMove={onPointerMove}
            onPointerUp={onPointerUp}
            onPointerCancel={onPointerUp}
          />
        )}

        {ready && !loadFailed && (
          <div className="pointer-events-none absolute inset-x-0 top-0 px-5 pt-4 text-center">
            <h2 id="avatar-crop-title" className="text-base font-bold text-white">
              عکس پروفایل
            </h2>
            <p className="mt-1 text-sm text-white/75">جابه‌جا کنید و بزرگ‌نمایی کنید</p>
          </div>
        )}
      </div>

      <div className="flex shrink-0 items-center justify-between px-3 py-5">
        <button
          type="button"
          onClick={onDismiss}
          disabled={isExporting}
          className="rounded-lg px-4 py-2 text-sm text-white hover:bg-white/10 disabled:opacity-50"
        >
          انصراف
        </button>
        <button
          type="button"
          onClick={() => void handleConfirm()}
          disabled={!ready || loadFailed || isExporting}
          className="rounded-lg px-4 py-2 text-sm font-bold text-white hover:bg-white/10 disabled:opacity-50"
        >
          {isExporting ? (
            <span className="inline-flex h-5 w-5 animate-spin rounded-full border-2 border-white/30 border-t-white" />
          ) : (
            "تنظیم"
          )}
        </button>
      </div>
    </div>
  );
}
