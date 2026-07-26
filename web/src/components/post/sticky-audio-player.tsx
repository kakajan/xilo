"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Music2, Pause, Play } from "lucide-react";

/** Playback speed options for the sticky post audio player. */
const RATES = [1, 1.25, 1.5] as const;

function formatTime(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return "0:00";
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
}

export function StickyAudioPlayer({
  src,
  title,
}: {
  src: string;
  title?: string;
}) {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [playing, setPlaying] = useState(false);
  const [current, setCurrent] = useState(0);
  const [duration, setDuration] = useState(0);
  const [rateIndex, setRateIndex] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!src?.trim()) {
      setError("آدرس فایل صوتی نامعتبر است");
      setReady(false);
      return;
    }

    const audio = new Audio(src);
    audio.preload = "metadata";
    audioRef.current = audio;
    setError(null);
    setReady(false);
    setPlaying(false);
    setCurrent(0);
    setDuration(0);

    const onTime = () => setCurrent(audio.currentTime);
    const onMeta = () => {
      setDuration(audio.duration || 0);
      setReady(true);
      setError(null);
    };
    const onEnded = () => setPlaying(false);
    const onPlay = () => setPlaying(true);
    const onPause = () => setPlaying(false);
    const onError = () => {
      setPlaying(false);
      setReady(false);
      setError("پخش صوت ممکن نیست. فایل در دسترس نیست.");
    };
    const onStalled = () => {
      if (!audio.error) return;
      setError("بارگذاری صوت متوقف شد");
    };

    audio.addEventListener("timeupdate", onTime);
    audio.addEventListener("loadedmetadata", onMeta);
    audio.addEventListener("ended", onEnded);
    audio.addEventListener("play", onPlay);
    audio.addEventListener("pause", onPause);
    audio.addEventListener("error", onError);
    audio.addEventListener("stalled", onStalled);

    return () => {
      audio.pause();
      audio.removeEventListener("timeupdate", onTime);
      audio.removeEventListener("loadedmetadata", onMeta);
      audio.removeEventListener("ended", onEnded);
      audio.removeEventListener("play", onPlay);
      audio.removeEventListener("pause", onPause);
      audio.removeEventListener("error", onError);
      audio.removeEventListener("stalled", onStalled);
      audioRef.current = null;
    };
  }, [src]);

  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.playbackRate = RATES[rateIndex];
    }
  }, [rateIndex]);

  const toggle = useCallback(() => {
    const audio = audioRef.current;
    if (!audio || error) return;
    if (audio.paused) {
      void audio.play().catch(() => {
        setError("پخش صوت ممکن نیست");
        setPlaying(false);
      });
    } else {
      audio.pause();
    }
  }, [error]);

  const onSeek = (value: number) => {
    const audio = audioRef.current;
    if (!audio || !ready) return;
    audio.currentTime = value;
    setCurrent(value);
  };

  const cycleRate = () => {
    setRateIndex((i) => (i + 1) % RATES.length);
  };

  const progress = duration > 0 ? (current / duration) * 100 : 0;

  return (
    <div
      className="sticky bottom-[8.5rem] z-40 -mx-4 border-t bg-background/95 px-3 py-2 backdrop-blur md:bottom-16"
      role="region"
      aria-label="پخش صوت پست"
    >
      <div className="flex items-center gap-2">
        <Music2 className="h-4 w-4 shrink-0 text-primary" aria-hidden />
        <span className="min-w-0 flex-1 truncate text-xs font-medium text-foreground/90">
          {title?.trim() || "صوت این پست"}
        </span>
        <button
          type="button"
          onClick={toggle}
          disabled={!!error}
          className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground disabled:opacity-50"
          aria-label={playing ? "توقف" : "پخش"}
        >
          {playing ? <Pause className="h-4 w-4" /> : <Play className="h-4 w-4 ms-0.5" />}
        </button>
        <button
          type="button"
          onClick={cycleRate}
          disabled={!!error || !ready}
          className="shrink-0 rounded-md border px-2 py-1 text-[11px] font-medium tabular-nums text-muted-foreground hover:bg-accent disabled:opacity-50"
          aria-label="سرعت پخش"
          dir="ltr"
        >
          {RATES[rateIndex]}×
        </button>
      </div>
      {error ? (
        <p className="mt-1.5 text-[11px] text-destructive" role="alert">
          {error}
        </p>
      ) : (
        <div className="mt-1.5 flex items-center gap-2">
          <span className="w-9 shrink-0 text-start text-[10px] tabular-nums text-muted-foreground" dir="ltr">
            {formatTime(current)}
          </span>
          <input
            type="range"
            min={0}
            max={duration || 0}
            step={0.1}
            value={current}
            onChange={(e) => onSeek(Number(e.target.value))}
            disabled={!ready}
            className="h-1.5 min-w-0 flex-1 cursor-pointer accent-primary disabled:opacity-50"
            aria-label="جابجایی در صوت"
            style={{
              background: `linear-gradient(to right, var(--primary) ${progress}%, var(--muted) ${progress}%)`,
            }}
          />
          <span className="w-9 shrink-0 text-end text-[10px] tabular-nums text-muted-foreground" dir="ltr">
            {formatTime(duration)}
          </span>
        </div>
      )}
    </div>
  );
}
