"use client";

import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { useSession } from "@/lib/store";
import { useToast } from "@/components/ToastProvider";
import { Badge, Button, Card, CardBody, CardHeader, EmptyState, Spinner } from "@/components/ui";
import { VideoReviewPlayer, severityTone, type VideoPlayerHandle } from "@/components/VideoReviewPlayer";
import { ApiError } from "@/lib/api";
import type { Finding } from "@/lib/types";
import { cn, formatMs, prettyJson } from "@/lib/utils";

const SEVERITIES = ["ALL", "BLOCK", "REVIEW", "WARN", "HIGH", "MEDIUM", "LOW"];

export default function CandidateReviewPage() {
  const { id } = useParams<{ id: string }>();
  const candidateId = Number(id);
  const searchParams = useSearchParams();
  const batchId = searchParams.get("batch");
  const { api, token } = useSession();
  const { toast } = useToast();
  const playerRef = useRef<VideoPlayerHandle>(null);

  const [findings, setFindings] = useState<Finding[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<Finding | null>(null);
  const [severity, setSeverity] = useState("ALL");

  const loadFindings = useCallback(async () => {
    if (!api.token) return;
    setLoading(true);
    try {
      const list = await api.listCandidateFindings(candidateId);
      setFindings(list);
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Failed to load findings", "error");
    } finally {
      setLoading(false);
    }
  }, [api, candidateId, toast]);

  useEffect(() => {
    loadFindings();
  }, [loadFindings]);

  const mediaSrc = useMemo(() => api.candidateMediaUrl(candidateId), [api, candidateId]);

  const normalizedSeverity = (s?: string) => s?.toUpperCase() || "UNKNOWN";
  const visibleFindings = useMemo(
    () =>
      severity === "ALL"
        ? findings
        : findings.filter((f) => normalizedSeverity(f.severity) === severity),
    [findings, severity]
  );

  const handleSelect = (f: Finding) => {
    setSelected(f);
    if (f.startMs != null) playerRef.current?.seekTo(f.startMs);
  };

  const severityCounts = useMemo(() => {
    const counts: Record<string, number> = {};
    for (const f of findings) {
      const key = normalizedSeverity(f.severity);
      counts[key] = (counts[key] || 0) + 1;
    }
    return counts;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [findings]);

  return (
    <AppShell>
      <div className="mb-6">
        <Link
          href={batchId ? "/batches/" + batchId : "/dashboard"}
          className="text-xs text-slate-500 hover:text-slate-700"
        >
          ← {batchId ? "Batch #" + batchId : "Dashboard"}
        </Link>
        <div className="mt-1 flex items-center justify-between">
          <h1 className="text-xl font-bold text-slate-900">Candidate #{candidateId}</h1>
          <Badge tone="slate">findings {findings.length}</Badge>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Player + timeline */}
        <div className="lg:col-span-2">
          <VideoReviewPlayer ref={playerRef} src={mediaSrc} findings={findings} />
          <p className="mt-2 text-xs text-slate-400">
            Media is streamed from the backend media endpoint (best effort). The coloured
            timeline segments are Finding time ranges; clicking one seeks the player.
          </p>
        </div>

        {/* Findings list */}
        <div className="space-y-3">
          <Card>
            <CardHeader
              title="Findings"
              subtitle="Evidence-grounded issues with precise timecodes."
              actions={
                <Badge tone="slate">{visibleFindings.length} of {findings.length}</Badge>
              }
            />
            <CardBody>
              <div className="mb-3 flex flex-wrap gap-1">
                {SEVERITIES.map((s) => (
                  <button
                    key={s}
                    onClick={() => setSeverity(s)}
                    className={cn(
                      "rounded-full px-2 py-1 text-[11px] font-medium transition",
                      severity === s ? "bg-brand-600 text-white" : "bg-slate-100 text-slate-600 hover:bg-slate-200"
                    )}
                  >
                    {s}
                    {s !== "ALL" && severityCounts[s] ? " · " + severityCounts[s] : ""}
                  </button>
                ))}
              </div>

              {loading ? (
                <div className="flex justify-center py-8"><Spinner /></div>
              ) : visibleFindings.length === 0 ? (
                <EmptyState
                  icon="🔍"
                  title={findings.length === 0 ? "No findings yet" : "No findings match the filter"}
                  description={
                    findings.length === 0
                      ? "Run analysis on this candidate to generate timecoded findings."
                      : "Try a different severity filter."
                  }
                />
              ) : (
                <ul className="space-y-2">
                  {visibleFindings.map((f, i) => {
                    const key = f.id ?? i;
                    return (
                      <li key={key}>
                        <button
                          onClick={() => handleSelect(f)}
                          className={cn(
                            "w-full rounded-lg border px-3 py-2 text-left transition",
                            selected === f
                              ? "border-brand-500 bg-brand-50"
                              : "border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50"
                          )}
                        >
                          <div className="flex items-center justify-between gap-2">
                            <span className="truncate text-sm font-medium text-slate-800">
                              {f.summary || f.ruleId || "Finding"}
                            </span>
                            <Badge tone={severityTone(f.severity)}>{normalizedSeverity(f.severity)}</Badge>
                          </div>
                          <div className="mt-1 flex flex-wrap items-center gap-2 text-[11px] text-slate-400">
                            <span className="font-mono">{formatMs(f.startMs)}–{formatMs(f.endMs)}</span>
                            {f.dimension && <span>{f.dimension}</span>}
                            {f.ruleId && <span>· {f.ruleId}</span>}
                          </div>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              )}
            </CardBody>
          </Card>

          {selected && (
            <Card>
              <CardHeader title="Finding detail" />
              <CardBody className="space-y-2 text-sm">
                <div className="flex flex-wrap gap-2">
                  <Badge tone={severityTone(selected.severity)}>{normalizedSeverity(selected.severity)}</Badge>
                  {selected.findingType && <Badge tone="indigo">{selected.findingType}</Badge>}
                  {selected.verdict && <Badge tone="slate">{selected.verdict}</Badge>}
                </div>
                <p className="text-slate-700">{selected.summary}</p>
                <dl className="grid grid-cols-2 gap-2 text-xs">
                  <div><dt className="text-slate-400">Time range</dt><dd className="font-mono">{formatMs(selected.startMs)}–{formatMs(selected.endMs)}</dd></div>
                  <div><dt className="text-slate-400">Confidence</dt><dd>{selected.confidence != null ? Math.round(selected.confidence * 100) + "%" : "—"}</dd></div>
                  {selected.detectorId && <div><dt className="text-slate-400">Detector</dt><dd>{selected.detectorId}</dd></div>}
                  {selected.ruleId && <div><dt className="text-slate-400">Rule</dt><dd>{selected.ruleId}</dd></div>}
                </dl>
                {selected.evidence && (
                  <div>
                    <p className="mb-1 text-[11px] font-semibold uppercase tracking-wide text-slate-400">Evidence</p>
                    <pre className="overflow-x-auto rounded-lg bg-slate-50 p-2 font-mono text-[11px] text-slate-600">
                      {prettyJson(selected.evidence)}
                    </pre>
                  </div>
                )}
              </CardBody>
            </Card>
          )}

          <Button variant="outline" className="w-full" onClick={loadFindings}>
            Refresh findings
          </Button>
        </div>
      </div>
    </AppShell>
  );
}
