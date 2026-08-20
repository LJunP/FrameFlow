"use client";

// HTML5 video review player with a timecoded findings timeline (BOOK-02 §6.8).
// Findings render as coloured segments on a seekable timeline; clicking one
// seeks the underlying <video> element. Exposes an imperative ref with
// seekTo(ms) so sibling Finding lists can drive the playhead.

import {
  forwardRef,
  useCallback,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from "react";
import type { Finding } from "@/lib/types";
import { cn, formatMs } from "@/lib/utils";

export interface VideoPlayerHandle {
  seekTo: (ms: number) => void;
}

export interface TimelineSegment {
  key: string;
  startMs: number;
  endMs: number;
  tone: string;
  title: string;
}

export function severityTone(severity?: string): string {
  switch (severity) {
    case "BLOCK":
    case "CRITICAL":
    case "HIGH":
      return "red";
    case "REVIEW":
    case "MEDIUM":
      return "amber";
    case "WARN":
    case "LOW":
      return "sky";
    default:
      return "slate";
  }
}

export function findingsToSegments(findings: Finding[]): TimelineSegment[] {
  return findings
    .filter((f) => f.startMs != null && f.endMs != null && f.endMs > f.startMs)
    .map((f) => ({
      key: String(f.id ?? (f.ruleId + "-" + f.startMs + "-" + f.endMs)),
      startMs: f.startMs as number,
      endMs: f.endMs as number,
      tone: severityTone(f.severity),
      title: f.summary || f.ruleId || "Finding",
    }));
}

const toneBg: Record<string, string> = {
  red: "bg-red-500 text-white",
  amber: "bg-amber-400 text-amber-950",
  sky: "bg-sky-400 text-sky-950",
  slate: "bg-slate-400 text-white",
};

export const VideoReviewPlayer = forwardRef<VideoPlayerHandle, {
  src?: string;
  findings: Finding[];
}>(function VideoReviewPlayer({ src, findings }, ref) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [duration, setDuration] = useState(0);
  const [currentTime, setCurrentTime] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const segments = useMemo(() => findingsToSegments(findings), [findings]);
  const maxMs = Math.max(duration * 1000, ...segments.map((s) => s.endMs), 1);

  const seekTo = useCallback((ms: number) => {
    const v = videoRef.current;
    if (v) {
      try {
        v.currentTime = ms / 1000;
        void v.play();
      } catch {
        /* ignore */
      }
    }
  }, []);

  useImperativeHandle(ref, () => ({ seekTo }), [seekTo]);

  const onTimeUpdate = useCallback(() => {
    const v = videoRef.current;
    if (v) setCurrentTime(v.currentTime);
  }, []);

  const onLoadedMetadata = useCallback(() => {
    const v = videoRef.current;
    if (v && Number.isFinite(v.duration)) setDuration(v.duration);
  }, []);

  const activeKey = useMemo(() => {
    const curMs = currentTime * 1000;
    let best: TimelineSegment | undefined;
    for (const s of segments) {
      if (curMs >= s.startMs && curMs <= s.endMs) {
        if (!best || s.endMs - s.startMs < best.endMs - best.startMs) best = s;
      }
    }
    return best?.key;
  }, [currentTime, segments]);

  return (
    <div className="space-y-3">
      <div className="overflow-hidden rounded-xl bg-black">
        <video
          ref={videoRef}
          src={src}
          controls
          preload="metadata"
          onTimeUpdate={onTimeUpdate}
          onLoadedMetadata={onLoadedMetadata}
          onError={() => setError("Unable to load the media stream.")}
          className="aspect-video w-full"
        >
          <track kind="captions" />
        </video>
        {error && (<p className="bg-red-50 px-4 py-2 text-sm text-red-700">{error}</p>)}
      </div>

      <div className="rounded-xl border border-slate-200 bg-white p-3">
        <div className="mb-2 flex items-center justify-between text-xs text-slate-500">
          <span className="font-medium">Finding timeline</span>
          <span>{formatMs(currentTime * 1000)} / {formatMs(duration ? duration * 1000 : null)}</span>
        </div>
        <div
          role="slider"
          aria-label="Seek player"
          aria-valuemin={0}
          aria-valuemax={Math.round(maxMs)}
          aria-valuenow={Math.round(currentTime * 1000)}
          tabIndex={0}
          onClick={(e) => {
            const rect = e.currentTarget.getBoundingClientRect();
            const ratio = Math.min(1, Math.max(0, (e.clientX - rect.left) / rect.width));
            seekTo(ratio * maxMs);
          }}
          onKeyDown={(e) => {
            if (e.key === "ArrowRight") seekTo(currentTime * 1000 + 1000);
            if (e.key === "ArrowLeft") seekTo(currentTime * 1000 - 1000);
          }}
          className="relative h-8 cursor-pointer rounded-md bg-slate-100 outline-none focus-visible:ring-2 focus-visible:ring-brand-500/40"
        >
          {segments.map((s) => {
            const left = (s.startMs / maxMs) * 100;
            const width = Math.max(1.2, ((s.endMs - s.startMs) / maxMs) * 100);
            const active = s.key === activeKey;
            return (
              <div
                key={s.key}
                title={s.title + " (" + formatMs(s.startMs) + "–" + formatMs(s.endMs) + ")"}
                onClick={(ev) => { ev.stopPropagation(); seekTo(s.startMs); }}
                className={(active ? "ring-2 ring-offset-1 ring-slate-800 " : "opacity-70 ") + (toneBg[s.tone] || "bg-slate-400 ") + "absolute top-1 h-6 rounded-sm border border-black/10 transition-all"}
                style={{ left: left + "%", width: width + "%" }}
              />
            );
          })}
          <div
            className="pointer-events-none absolute top-0 h-8 w-0.5 bg-brand-600"
            style={{ left: ((currentTime * 1000) / maxMs) * 100 + "%" }}
          />
        </div>

        {segments.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-1.5">
            {segments.map((s) => (
              <button
                key={s.key}
                onClick={() => seekTo(s.startMs)}
                className={(s.key === activeKey ? "ring-2 ring-slate-800 " : "opacity-80 hover:opacity-100 ") + (toneBg[s.tone] || "bg-slate-400 text-white") + " inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[11px]"}
              >
                <span className="font-mono">{formatMs(s.startMs)}</span>
                <span className="max-w-[160px] truncate">{s.title}</span>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
});
