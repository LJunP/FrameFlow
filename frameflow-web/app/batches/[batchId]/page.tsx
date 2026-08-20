"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { useSession } from "@/lib/store";
import { useToast } from "@/components/ToastProvider";
import {
  Badge,
  Button,
  Card,
  CardBody,
  CardHeader,
  EmptyState,
  Spinner,
} from "@/components/ui";
import { ApiError } from "@/lib/api";
import { candidateStatusTone } from "@/lib/api";
import type { Batch, BatchProgress, Candidate } from "@/lib/types";
import { cn, formatBytes, formatDate } from "@/lib/utils";

type UploadState =
  | { phase: "pending" }
  | { phase: "registering" }
  | { phase: "hashing" }
  | { phase: "finalizing" }
  | { phase: "done"; candidateId: number }
  | { phase: "error"; message: string };

interface UploadItem {
  id: string;
  file: File;
  state: UploadState;
  retries: number;
}

const FILTERS = ["ALL", "READY", "ANALYZED", "AUTO_REJECT", "REVIEW_REQUIRED", "SHORTLIST_CANDIDATE", "ANALYSIS_ERROR", "INVALID", "DRAFT"];

export default function BatchPage() {
  const { batchId: batchIdRaw } = useParams<{ batchId: string }>();
  const batchId = Number(batchIdRaw);
  const router = useRouter();
  const { api, token } = useSession();
  const { toast } = useToast();

  const [batch, setBatch] = useState<Batch | null>(null);
  const [candidates, setCandidates] = useState<Candidate[]>([]);
  const [progress, setProgress] = useState<BatchProgress | null>(null);
  const [loading, setLoading] = useState(true);
  const [uploads, setUploads] = useState<UploadItem[]>([]);
  const [dragging, setDragging] = useState(false);
  const [filter, setFilter] = useState("ALL");
  const [processing, setProcessing] = useState(false);
  const [polling, setPolling] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const loadData = useCallback(async () => {
    if (!api.token) return;
    try {
      const [b, c] = await Promise.all([api.getBatch(batchId), api.listCandidates(batchId)]);
      setBatch(b);
      setCandidates(c);
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Failed to load batch", "error");
    } finally {
      setLoading(false);
    }
  }, [api, batchId, toast]);

  const loadProgress = useCallback(async () => {
    if (!api.token) return;
    try {
      const p = await api.batchProgress(batchId);
      setProgress(p);
      return p;
    } catch {
      return null;
    }
  }, [api, batchId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Poll progress while any candidate is still being analyzed.
  useEffect(() => {
    if (!polling) return;
    const t = setInterval(() => {
      loadProgress().then((p) => {
        const running = p?.candidateStatuses && p.candidateStatuses["ANALYZING"];
        if (!running) {
          setPolling(false);
          loadData();
        }
      });
    }, 4000);
    return () => clearInterval(t);
  }, [polling, loadProgress, loadData]);

  function addFiles(files: FileList | File[]) {
    const items: UploadItem[] = Array.from(files).map((f) => ({
      id: Math.random().toString(36).slice(2),
      file: f,
      state: { phase: "pending" },
      retries: 0,
    }));
    setUploads((prev) => [...prev, ...items]);
    items.forEach((it) => runUpload(it.id));
  }

  const runUpload = useCallback(
    async (itemId: string) => {
      setUploads((prev) =>
        prev.map((it) => (it.id === itemId ? { ...it, state: { phase: "registering" } } : it))
      );
      const item = uploads.find((u) => u.id === itemId) ?? getItem(itemId);
      if (!item) return;
      try {
        const ext = item.file.name.split(".").pop()?.toLowerCase() || "mp4";
        const mediaTypes = item.file.type || ("video/" + ext);
        const [newId] = await api.addCandidates(batchId, { keys: [item.file.name], mediaTypes: [mediaTypes] });
        setUploads((prev) =>
          prev.map((it) => (it.id === itemId ? { ...it, state: { phase: "hashing" } } : it))
        );
        const session = await api.createUploadSession(newId);
        setUploads((prev) =>
          prev.map((it) => (it.id === itemId ? { ...it, state: { phase: "finalizing" } } : it))
        );
        const digest = await sha256File(item.file);
        await api.completeUpload(newId, {
          sessionId: session.sessionId,
          sizeBytes: item.file.size,
          contentDigest: digest,
        });
        setUploads((prev) =>
          prev.map((it) => (it.id === itemId ? { ...it, state: { phase: "done", candidateId: newId } } : it))
        );
        toast("Uploaded " + item.file.name, "success");
        loadData();
      } catch (err) {
        const message = err instanceof ApiError ? err.message : "Upload failed";
        setUploads((prev) =>
          prev.map((it) =>
            it.id === itemId
              ? { ...it, state: { phase: "error", message }, retries: it.retries + 1 }
              : it
          )
        );
        toast("Upload failed: " + item.file.name, "error");
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [uploads, batchId, api, toast, loadData]
  );

  // Helper to read current item outside of state closure.
  const itemRef = useRef<UploadItem[]>([]);
  itemRef.current = uploads;
  function getItem(id: string): UploadItem | undefined {
    return itemRef.current.find((u) => u.id === id);
  }

  const handleStartAnalysis = async () => {
    setProcessing(true);
    try {
      const res = await api.processBatch(batchId);
      toast("Analysis started — " + res.total + " candidate(s) processing", "success");
      setPolling(true);
      setProgress({
        batchId,
        candidateStatuses: res.candidateStatuses,
        total: res.total,
        failed: res.failed,
        analyzed: res.analyzed,
      });
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Failed to start analysis", "error");
    } finally {
      setProcessing(false);
    }
  };

  const handleRerun = async (candidateId: number) => {
    try {
      await api.rerunCandidate(candidateId);
      toast("Rerun started", "success");
      setPolling(true);
      loadData();
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Rerun failed", "error");
    }
  };

  const visibleCandidates = filter === "ALL" ? candidates : candidates.filter((c) => c.status === filter);
  const counts = useMemoCounts(candidates);

  if (loading && !batch) {
    return (
      <AppShell>
        <p className="text-sm text-slate-500">Loading batch…</p>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <div className="mb-6">
        <Link href={batch ? "/projects/" + batch.projectId : "/dashboard"} className="text-xs text-slate-500 hover:text-slate-700">
          ← Project
        </Link>
        <div className="mt-1 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="text-xl font-bold text-slate-900">{batch?.name || "Batch"}</h1>
            {batch?.promptText && <p className="mt-0.5 max-w-2xl text-sm text-slate-500">{batch.promptText}</p>}
            {batch && (
              <p className="mt-1 text-xs text-slate-400">
                Created {formatDate(batch.createdAt)} · capacity {(batch.capacityLimit ?? "—")}
              </p>
            )}
          </div>
          <div className="flex items-center gap-2">
            <Badge tone={batchStatusTone(batch?.status)}>{batch?.status || "DRAFT"}</Badge>
            <Link href={"/batches/" + batchId + "/selection"}>
              <Button variant="outline">Selection &amp; Top-K</Button>
            </Link>
          </div>
        </div>
      </div>

      {/* Progress summary */}
      {progress && (
        <Card className="mb-6">
          <CardHeader title="Analysis progress" subtitle={progress.analyzed + " / " + progress.total + " analyzed"} />
          <CardBody>
            <div className="mb-3 h-2 overflow-hidden rounded-full bg-slate-100">
              <div
                className="h-full rounded-full bg-brand-500 transition-all"
                style={{ width: progress.total ? Math.round((progress.analyzed / progress.total) * 100) + "%" : "0%" }}
              />
            </div>
            <div className="flex flex-wrap gap-2 text-xs">
              {Object.entries(progress.candidateStatuses || {}).map(([status, n]) => (
                <span key={status} className="inline-flex items-center gap-1">
                  <Badge tone={candidateStatusTone(status)}>{status}</Badge>
                  <span className="font-semibold text-slate-700">{n}</span>
                </span>
              ))}
              <span className="text-slate-500">failed {progress.failed}</span>
            </div>
            {polling && (
              <p className="mt-2 flex items-center gap-2 text-xs text-slate-500">
                <Spinner size="sm" /> Polling analysis progress…
              </p>
            )}
          </CardBody>
        </Card>
      )}

      {/* Upload */}
      <Card className="mb-6">
        <CardHeader title="Upload candidate videos" subtitle="Add video files; each becomes a candidate record with an upload session." />
        <CardBody>
          <div
            onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
            onDragLeave={() => setDragging(false)}
            onDrop={(e) => {
              e.preventDefault();
              setDragging(false);
              if (e.dataTransfer.files?.length) addFiles(e.dataTransfer.files);
            }}
            onClick={() => fileInputRef.current?.click()}
            className={cn(
              "flex cursor-pointer flex-col items-center justify-center gap-1 rounded-xl border-2 border-dashed px-6 py-8 text-center transition",
              dragging ? "border-brand-500 bg-brand-50" : "border-slate-300 bg-slate-50/50 hover:border-brand-400"
            )}
          >
            <span className="text-2xl">⬆️</span>
            <p className="text-sm font-medium text-slate-700">Drop video files here, or click to browse</p>
            <p className="text-xs text-slate-400">Multiple files supported · mp4/mov/webm</p>
            <input
              ref={fileInputRef}
              type="file"
              multiple
              accept="video/*"
              className="hidden"
              onChange={(e) => {
                if (e.target.files?.length) addFiles(e.target.files);
                e.target.value = "";
              }}
            />
          </div>

          {uploads.length > 0 && (
            <ul className="mt-4 space-y-2">
              {uploads.map((u) => (
                <li key={u.id} className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 px-3 py-2">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-slate-800">{u.file.name}</p>
                    <p className="text-xs text-slate-400">{formatBytes(u.file.size)}</p>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    {uploadBadge(u.state)}
                    {u.state.phase === "error" && u.retries < 3 && (
                      <Button size="sm" variant="outline" onClick={() => runUpload(u.id)}>
                        Retry
                      </Button>
                    )}
                    {u.state.phase === "done" && u.state.candidateId != null && (
                      <Link href={"/candidates/" + u.state.candidateId}>
                        <Button size="sm" variant="subtle">Open</Button>
                      </Link>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </CardBody>
      </Card>

      {/* Actions */}
      <Card className="mb-6">
        <CardBody className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm text-slate-600">
            {candidates.length} candidate(s) added. Start analysis to run the configured quality pipeline.
          </p>
          <div className="flex items-center gap-2">
            <Button variant="outline" onClick={() => loadProgress()}>
              Refresh progress
            </Button>
            <Button onClick={handleStartAnalysis} loading={processing} disabled={candidates.length === 0}>
              Start analysis
            </Button>
          </div>
        </CardBody>
      </Card>

      {/* Candidate matrix */}
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-sm font-semibold text-slate-700">Candidate matrix</h2>
        <div className="flex flex-wrap gap-1">
          {FILTERS.map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className={cn(
                "rounded-full px-2.5 py-1 text-[11px] font-medium transition",
                filter === f ? "bg-brand-600 text-white" : "bg-slate-100 text-slate-600 hover:bg-slate-200"
              )}
            >
              {f}
              {f !== "ALL" && counts[f] != null ? " · " + counts[f] : ""}
            </button>
          ))}
        </div>
      </div>

      {visibleCandidates.length === 0 ? (
        <EmptyState
          icon="🎞️"
          title={candidates.length === 0 ? "No candidates yet" : "No candidates match this filter"}
          description={
            candidates.length === 0
              ? "Upload candidate videos above, then start analysis."
              : "Try a different decision/status filter."
          }
        />
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {visibleCandidates.map((c) => (
            <Link key={c.id} href={"/candidates/" + c.id}>
              <Card className="h-full transition hover:border-brand-400 hover:shadow-md">
                <CardBody>
                  <div className="mb-2 flex items-center justify-between">
                    <Badge tone={candidateStatusTone(c.status)}>{c.status || "DRAFT"}</Badge>
                    <span className="text-[11px] text-slate-400">#{c.id}</span>
                  </div>
                  <p className="truncate text-sm font-medium text-slate-800">{c.candidateKey || ("Candidate " + c.id)}</p>
                  <p className="mt-1 text-xs text-slate-400">
                    {c.mediaType || "video"} · {formatBytes(c.sizeBytes)}
                  </p>
                  <div className="mt-3 flex items-center justify-between">
                    <span className="text-[11px] text-slate-400">updated {formatDate(c.updatedAt)}</span>
                    <button
                      onClick={(e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        handleRerun(c.id);
                      }}
                      className="text-[11px] font-medium text-brand-600 hover:text-brand-700"
                    >
                      Rerun
                    </button>
                  </div>
                </CardBody>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </AppShell>
  );
}

function uploadBadge(state: UploadState) {
  if (state.phase === "pending") return <Badge tone="slate">Queued</Badge>;
  if (state.phase === "registering") return <Badge tone="sky"><Spinner size="sm" /> Registering</Badge>;
  if (state.phase === "hashing") return <Badge tone="sky"><Spinner size="sm" /> Hashing</Badge>;
  if (state.phase === "finalizing") return <Badge tone="sky"><Spinner size="sm" /> Finalizing</Badge>;
  if (state.phase === "done") return <Badge tone="green">Uploaded ✓</Badge>;
  return <Badge tone="red">Failed — {state.message}</Badge>;
}

function useMemoCounts(candidates: Candidate[]): Record<string, number> {
  const counts: Record<string, number> = {};
  for (const c of candidates) {
    const key = c.status || "DRAFT";
    counts[key] = (counts[key] || 0) + 1;
  }
  return counts;
}

function batchStatusTone(status?: string | null): string {
  switch (status) {
    case "ANALYZING":
      return "sky";
    case "READY":
      return "green";
    case "REVIEWING":
      return "amber";
    case "COMPLETED":
      return "emerald";
    case "FAILED":
    case "PARTIAL":
      return "red";
    case "CANCELLED":
      return "slate";
    default:
      return "slate";
  }
}

async function sha256File(file: File): Promise<string> {
  const data = await file.arrayBuffer();
  const digest = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}
