"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
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
  Field,
  Input,
  Modal,
  Select,
} from "@/components/ui";
import { ApiError } from "@/lib/api";
import type { RankingEntry, RankingSnapshot, SelectionSet, SimilarityCluster } from "@/lib/types";
import { cn, formatDate, prettyJson } from "@/lib/utils";

export default function SelectionPage() {
  const { batchId: raw } = useParams<{ batchId: string }>();
  const batchId = Number(raw);
  const { api, token } = useSession();
  const { toast } = useToast();

  const [clusters, setClusters] = useState<SimilarityCluster[]>([]);
  const [snapshots, setSnapshots] = useState<RankingSnapshot[]>([]);
  const [selectionSets, setSelectionSets] = useState<SelectionSet[]>([]);
  const [entries, setEntries] = useState<RankingEntry[]>([]);
  const [activeSnapshot, setActiveSnapshot] = useState<RankingSnapshot | null>(null);
  const [loading, setLoading] = useState(true);

  const [selModal, setSelModal] = useState(false);
  const [selName, setSelName] = useState("");
  const [selTopK, setSelTopK] = useState("10");
  const [exportOpen, setExportOpen] = useState(false);
  const [exportText, setExportText] = useState("");
  const [busy, setBusy] = useState(false);
  const [threshold, setThreshold] = useState("0.85");

  const loadAll = useCallback(async () => {
    if (!api.token) return;
    try {
      const [c, s, ss] = await Promise.all([
        api.listSimilarityClusters(batchId),
        api.listRankingSnapshots(batchId),
        api.listSelectionSets(batchId),
      ]);
      setClusters(c);
      setSnapshots(s);
      setSelectionSets(ss);
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Failed to load selection data", "error");
    } finally {
      setLoading(false);
    }
  }, [api, batchId, toast]);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  const handleComputeClusters = async () => {
    setBusy(true);
    try {
      const result = await api.createSimilarityClusters(batchId, Number(threshold) || undefined);
      setClusters(result);
      toast("Similarity clusters computed", "success");
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Failed to compute clusters", "error");
    } finally {
      setBusy(false);
    }
  };

  const handleCreateSnapshot = async () => {
    setBusy(true);
    try {
      await api.createRankingSnapshot(batchId);
      toast("Ranking snapshot created", "success");
      const s = await api.listRankingSnapshots(batchId);
      setSnapshots(s);
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Failed to create snapshot", "error");
    } finally {
      setBusy(false);
    }
  };

  const handleViewSnapshot = async (snap: RankingSnapshot) => {
    setActiveSnapshot(snap);
    try {
      const ents = await api.getRankingSnapshot(snap.id);
      setEntries(ents);
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Failed to load ranking entries", "error");
    }
  };

  const handleCreateSelection = async () => {
    if (!selName.trim()) return;
    setBusy(true);
    try {
      await api.createSelectionSet(batchId, { name: selName.trim(), topK: Number(selTopK) || 10 });
      toast("Selection set created (draft)", "success");
      setSelModal(false);
      setSelName("");
      const ss = await api.listSelectionSets(batchId);
      setSelectionSets(ss);
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Failed to create selection set", "error");
    } finally {
      setBusy(false);
    }
  };

  const handleLock = async (id: number) => {
    try {
      await api.lockSelectionSet(id);
      toast("Selection set locked", "success");
      const ss = await api.listSelectionSets(batchId);
      setSelectionSets(ss);
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Failed to lock selection set", "error");
    }
  };

  const handleExport = async (id: number) => {
    try {
      const result = await api.exportSelectionSet(id);
      setExportText(typeof result === "string" ? result : JSON.stringify(result, null, 2));
      setExportOpen(true);
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Failed to export selection set", "error");
    }
  };

  return (
    <AppShell>
      <div className="mb-6">
        <Link href={"/batches/" + batchId} className="text-xs text-slate-500 hover:text-slate-700">
          ← Batch #{batchId}
        </Link>
        <h1 className="mt-1 text-xl font-bold text-slate-900">Selection &amp; Top-K</h1>
        <p className="text-sm text-slate-500">
          Cluster duplicates, inspect ranking breakdowns, and build a locked selection set (BOOK-02 §5.9).
        </p>
      </div>

      {loading ? (
        <p className="text-sm text-slate-500">Loading…</p>
      ) : (
        <div className="grid gap-6 lg:grid-cols-2">
          {/* Similarity clusters */}
          <Card>
            <CardHeader
              title="Similarity clusters"
              subtitle="Near-duplicate candidate groups to avoid Top-K homogeneity."
              actions={
                <Button size="sm" variant="outline" onClick={handleComputeClusters} loading={busy}>
                  Compute clusters
                </Button>
              }
            />
            <CardBody>
              <div className="mb-3 flex items-center gap-2">
                <label className="text-xs text-slate-500">threshold</label>
                <Input
                  type="number"
                  step="0.01"
                  value={threshold}
                  onChange={(e) => setThreshold(e.target.value)}
                  className="h-8 w-24"
                />
              </div>
              {clusters.length === 0 ? (
                <EmptyState
                  title="No clusters yet"
                  description="Run duplicate clustering to group near-duplicate candidates."
                />
              ) : (
                <ul className="space-y-2">
                  {clusters.map((c) => (
                    <li key={c.id} className="rounded-lg border border-slate-200 px-3 py-2">
                      <div className="flex items-center justify-between">
                        <span className="text-sm font-medium text-slate-800">
                          Cluster #{c.id}
                        </span>
                        <Badge tone="slate">rep #{c.representativeCandidateId ?? "—"}</Badge>
                      </div>
                      <p className="mt-1 text-[11px] text-slate-400">
                        key {c.clusterKey || "—"} · {c.algorithmVersion || ""} · threshold {c.threshold ?? "—"} · {formatDate(c.createdAt)}
                      </p>
                    </li>
                  ))}
                </ul>
              )}
            </CardBody>
          </Card>

          {/* Ranking / Top-K compare */}
          <Card>
            <CardHeader
              title="Ranking breakdown"
              subtitle="Snapshots fix a candidate score set; inspect per-candidate score composition."
              actions={
                <Button size="sm" variant="outline" onClick={handleCreateSnapshot} loading={busy}>
                  New snapshot
                </Button>
              }
            />
            <CardBody>
              {snapshots.length === 0 ? (
                <EmptyState title="No ranking snapshots" description="Create a snapshot over the analyzed candidates." />
              ) : (
                <>
                  <div className="mb-3 flex flex-wrap gap-1">
                    {snapshots.map((s) => (
                      <button
                        key={s.id}
                        onClick={() => handleViewSnapshot(s)}
                        className={cn(
                          "rounded-full px-2.5 py-1 text-[11px] font-medium",
                          activeSnapshot?.id === s.id
                            ? "bg-brand-600 text-white"
                            : "bg-slate-100 text-slate-600 hover:bg-slate-200"
                        )}
                      >
                        snapshot #{s.id} · v{s.snapshotVersion ?? "?"}
                      </button>
                    ))}
                  </div>
                  {activeSnapshot && (
                    <table className="w-full text-left text-sm">
                      <thead>
                        <tr className="border-b border-slate-100 text-[11px] uppercase text-slate-400">
                          <th className="py-1">Rank</th>
                          <th className="py-1">Candidate</th>
                          <th className="py-1">Base</th>
                          <th className="py-1">Final</th>
                          <th className="py-1">Breakdown</th>
                        </tr>
                      </thead>
                      <tbody>
                        {entries.map((e) => (
                          <tr key={e.candidateId} className="border-b border-slate-50">
                            <td className="py-1.5 font-mono text-xs">{e.rank ?? "—"}</td>
                            <td className="py-1.5 text-slate-800">#{e.candidateId}</td>
                            <td className="py-1.5 font-mono text-xs">{e.baseScore?.toFixed(2)}</td>
                            <td className="py-1.5 font-mono text-xs font-semibold">{e.finalScore?.toFixed(2)}</td>
                            <td className="py-1.5 text-[11px] text-slate-400">{e.breakdown || "—"}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </>
              )}
            </CardBody>
          </Card>
        </div>
      )}

      {/* Selection sets */}
      <Card className="mt-6">
        <CardHeader
          title="Selection sets"
          subtitle="The human-finalized Top-K. Locking makes it immutable; exporting emits JSON/CSV."
          actions={<Button onClick={() => setSelModal(true)}>+ New selection set</Button>}
        />
        <CardBody>
          {selectionSets.length === 0 ? (
            <EmptyState
              icon="✅"
              title="No selection sets yet"
              description="Build a Top-K selection set from the ranked candidates."
              action={<Button onClick={() => setSelModal(true)}>Create selection set</Button>}
            />
          ) : (
            <ul className="divide-y divide-slate-100">
              {selectionSets.map((s) => (
                <li key={s.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
                  <div>
                    <p className="text-sm font-medium text-slate-800">
                      {s.name || ("Selection set #" + s.id)}
                    </p>
                    <p className="text-xs text-slate-400">
                      batch #{s.batchId} · topK {s.topK ?? "—"} · created {formatDate(s.createdAt)}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge tone={s.status === "LOCKED" ? "green" : "amber"}>{s.status || "DRAFT"}</Badge>
                    {s.status !== "LOCKED" ? (
                      <Button size="sm" variant="outline" onClick={() => handleLock(s.id)}>Lock</Button>
                    ) : (
                      <Button size="sm" variant="outline" onClick={() => handleExport(s.id)}>Export</Button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </CardBody>
      </Card>

      <Modal open={selModal} onClose={() => setSelModal(false)} title="Create selection set (Top-K)">
        <div className="space-y-4">
          <Field label="Name" required>
            <Input value={selName} onChange={(e) => setSelName(e.target.value)} placeholder="Final shortlist — round 3" autoFocus />
          </Field>
          <Field label="Top-K" hint="Number of candidates to keep.">
            <Select value={selTopK} onChange={(e) => setSelTopK(e.target.value)}>
              {["5", "10", "20", "50"].map((n) => (
                <option key={n} value={n}>Top {n}</option>
              ))}
            </Select>
          </Field>
        </div>
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="outline" onClick={() => setSelModal(false)}>Cancel</Button>
          <Button onClick={handleCreateSelection} loading={busy}>Create</Button>
        </div>
      </Modal>

      <Modal open={exportOpen} onClose={() => setExportOpen(false)} title="Exported selection set">
        <pre className="max-h-[60vh] overflow-auto rounded-lg bg-slate-50 p-3 font-mono text-[11px] text-slate-600">
          {exportText}
        </pre>
        <div className="mt-4 flex justify-end">
          <Button
            variant="outline"
            onClick={() => {
              const blob = new Blob([exportText], { type: "application/json" });
              const url = URL.createObjectURL(blob);
              const a = document.createElement("a");
              a.href = url;
              a.download = "selection-set.json";
              a.click();
              URL.revokeObjectURL(url);
            }}
          >
            Download
          </Button>
        </div>
      </Modal>
    </AppShell>
  );
}
