"use client";

import { useParams, useRouter } from "next/navigation";
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
  Textarea,
} from "@/components/ui";
import { ApiError } from "@/lib/api";
import type { Batch, Project, QualityProfile, QualityProfileVersion } from "@/lib/types";
import { cn, formatDate } from "@/lib/utils";

type Tab = "profiles" | "batches";

const DEFAULT_PROFILE_PAYLOAD = JSON.stringify(
  {
    name: "ECOMMERCE_SHORT_AD_V1",
    rules: [
      {
        ruleId: "duration_range",
        ruleType: "HARD_CONSTRAINT",
        severity: "BLOCK",
        parameters: { minSeconds: 10, maxSeconds: 60 },
      },
      {
        ruleId: "no_audio",
        ruleType: "DETECTOR_THRESHOLD",
        severity: "REVIEW",
        parameters: { silentSeconds: 5 },
      },
    ],
    rankingWeights: { visualQuality: 0.5, briefAlignment: 0.3, audioQuality: 0.2 },
  },
  null,
  2
);

export default function ProjectPage() {
  const { id } = useParams<{ id: string }>();
  const projectId = Number(id);
  const router = useRouter();
  const { api, token } = useSession();
  const { toast } = useToast();

  const [tab, setTab] = useState<Tab>("profiles");
  const [project, setProject] = useState<Project | null>(null);
  const [profiles, setProfiles] = useState<QualityProfile[]>([]);
  const [batches, setBatches] = useState<Batch[]>([]);
  const [versions, setVersions] = useState<Record<number, QualityProfileVersion[]>>({});
  const [loading, setLoading] = useState(true);

  const [profileModal, setProfileModal] = useState(false);
  const [profileName, setProfileName] = useState("");
  const [versionModal, setVersionModal] = useState(false);
  const [activeProfile, setActiveProfile] = useState<QualityProfile | null>(null);
  const [versionPayload, setVersionPayload] = useState(DEFAULT_PROFILE_PAYLOAD);
  const [batchModal, setBatchModal] = useState(false);
  const [batchName, setBatchName] = useState("");
  const [batchPrompt, setBatchPrompt] = useState("");
  const [batchProfileVersionId, setBatchProfileVersionId] = useState<string>("");
  const [saving, setSaving] = useState(false);
  const [payloadError, setPayloadError] = useState<string | null>(null);

  const publishedVersions = Object.values(versions).flat().filter((v) => v.status === "PUBLISHED");

  const loadAll = useCallback(async () => {
    if (!api.token) return;
    setLoading(true);
    try {
      const [proj, profs, batchList] = await Promise.all([
        api.getProject(projectId),
        api.listQualityProfiles(projectId),
        api.listBatches(projectId),
      ]);
      setProject(proj);
      setProfiles(profs);
      setBatches(batchList);
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Failed to load project", "error");
    } finally {
      setLoading(false);
    }
  }, [api, projectId, toast]);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  const handleCreateProfile = async () => {
    if (!profileName.trim()) return;
    setSaving(true);
    try {
      await api.createQualityProfile(projectId, { name: profileName.trim() });
      toast("Quality profile created", "success");
      setProfileModal(false);
      setProfileName("");
      await loadAll();
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Failed to create profile", "error");
    } finally {
      setSaving(false);
    }
  };

  const handleCreateVersion = async () => {
    if (!activeProfile) return;
    setPayloadError(null);
    try {
      JSON.parse(versionPayload);
    } catch {
      setPayloadError("Payload must be valid JSON.");
      return;
    }
    setSaving(true);
    try {
      const v = await api.createProfileVersion(projectId, activeProfile.id, versionPayload);
      setVersions((prev) => ({ ...prev, [activeProfile.id]: [...(prev[activeProfile.id] || []), v] }));
      toast("Draft version saved (v" + v.version + ")", "success");
      setVersionModal(false);
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Failed to save version", "error");
    } finally {
      setSaving(false);
    }
  };

  const handlePublish = async (versionId: number) => {
    try {
      await api.publishProfileVersion(versionId);
      toast("Version published", "success");
      setVersions((prev) =>
        Object.fromEntries(
          Object.entries(prev).map(([pid, vs]) => [
            pid,
            vs.map((v) => (v.id === versionId ? { ...v, status: "PUBLISHED" } : v)),
          ])
        )
      );
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Failed to publish", "error");
    }
  };

  const handleCreateBatch = async () => {
    if (!batchName.trim()) return;
    if (!batchProfileVersionId) {
      toast("Select a published quality profile version", "warning");
      return;
    }
    setSaving(true);
    try {
      const res = await api.createBatch(projectId, {
        name: batchName.trim(),
        promptText: batchPrompt.trim() || undefined,
        profileVersionId: Number(batchProfileVersionId),
      });
      toast("Batch created", "success");
      setBatchModal(false);
      setBatchName("");
      setBatchPrompt("");
      setBatchProfileVersionId("");
      router.push("/batches/" + res.id);
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Failed to create batch", "error");
    } finally {
      setSaving(false);
    }
  };

  if (loading && !project) {
    return (
      <AppShell>
        <p className="text-sm text-slate-500">Loading project…</p>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <div className="mb-6">
        <button onClick={() => router.push("/dashboard")} className="text-xs text-slate-500 hover:text-slate-700">
          ← Dashboard
        </button>
        <div className="mt-1 flex items-center justify-between">
          <div>
            <h1 className="text-xl font-bold text-slate-900">{project?.name || "Project"}</h1>
            {project?.description && <p className="text-sm text-slate-500">{project.description}</p>}
          </div>
          <Badge tone="slate">{project?.status || "ACTIVE"}</Badge>
        </div>
      </div>

      <div className="mb-6 flex w-fit gap-1 rounded-lg bg-slate-200/60 p-1">
        {(["profiles", "batches"] as Tab[]).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={cn(
              "rounded-md px-4 py-1.5 text-sm font-medium transition",
              tab === t ? "bg-white text-slate-900 shadow-sm" : "text-slate-600 hover:text-slate-800"
            )}
          >
            {t === "profiles" ? "Quality Profiles" : "Batches"}
          </button>
        ))}
      </div>

      {tab === "profiles" ? (
        <div className="space-y-4">
          <div className="flex items-center justify-end">
            <Button onClick={() => setProfileModal(true)}>+ New profile</Button>
          </div>
          {profiles.length === 0 ? (
            <EmptyState
              icon="test-tube"
              title="No quality profiles yet"
              description="Define reusable quality standards that can be versioned and published."
              action={<Button onClick={() => setProfileModal(true)}>Create profile</Button>}
            />
          ) : (
            profiles.map((p) => (
              <Card key={p.id}>
                <CardHeader
                  title={p.name}
                  subtitle={"template: " + (p.templateType || "custom") + " · created " + formatDate(p.createdAt)}
                  actions={
                    <Button
                      size="sm"
                      onClick={() => {
                        setActiveProfile(p);
                        setVersionPayload(DEFAULT_PROFILE_PAYLOAD);
                        setPayloadError(null);
                        setVersionModal(true);
                      }}
                    >
                      New version
                    </Button>
                  }
                />
                <CardBody>
                  <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">Versions</h4>
                  {!versions[p.id] || versions[p.id].length === 0 ? (
                    <p className="text-xs text-slate-400">No versions recorded in this session yet.</p>
                  ) : (
                    <ul className="divide-y divide-slate-100">
                      {versions[p.id].map((v) => (
                        <li key={v.id} className="flex items-center justify-between py-2">
                          <div className="flex items-center gap-2">
                            <span className="text-sm font-medium text-slate-800">v{v.version}</span>
                            {v.payloadDigest && (
                              <span className="text-xs text-slate-400">digest {v.payloadDigest.slice(0, 10)}…</span>
                            )}
                            <Badge tone={v.status === "PUBLISHED" ? "green" : "amber"}>{v.status || "DRAFT"}</Badge>
                          </div>
                          {v.status !== "PUBLISHED" && (
                            <Button size="sm" variant="outline" onClick={() => handlePublish(v.id)}>
                              Publish
                            </Button>
                          )}
                        </li>
                      ))}
                    </ul>
                  )}
                </CardBody>
              </Card>
            ))
          )}
        </div>
      ) : (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <p className="text-sm text-slate-500">
              {batches.length} batch{batches.length === 1 ? "" : "es"}
            </p>
            <Button
              onClick={() => setBatchModal(true)}
              disabled={publishedVersions.length === 0}
              title={publishedVersions.length === 0 ? "Publish a quality profile version first" : undefined}
            >
              + New batch
            </Button>
          </div>
          {batches.length === 0 ? (
            <EmptyState
              icon="film"
              title="No batches yet"
              description="A batch groups candidate videos with a shared prompt and quality profile version."
              action={
                publishedVersions.length === 0 ? (
                  <p className="text-xs text-slate-400">Publish a quality profile version to enable batch creation.</p>
                ) : (
                  <Button onClick={() => setBatchModal(true)}>Create batch</Button>
                )
              }
            />
          ) : (
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {batches.map((b) => (
                <button key={b.id} onClick={() => router.push("/batches/" + b.id)} className="text-left">
                  <Card className="h-full transition hover:border-brand-400 hover:shadow-md">
                    <CardBody>
                      <div className="flex items-start justify-between gap-2">
                        <h3 className="font-semibold text-slate-800">{b.name}</h3>
                        <Badge tone={batchStatusTone(b.status)}>{b.status || "DRAFT"}</Badge>
                      </div>
                      {b.promptText && <p className="mt-1 line-clamp-2 text-xs text-slate-500">{b.promptText}</p>}
                      <p className="mt-3 text-[11px] text-slate-400">Updated {formatDate(b.updatedAt)}</p>
                    </CardBody>
                  </Card>
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      <Modal open={profileModal} onClose={() => setProfileModal(false)} title="Create quality profile">
        <Field label="Profile name" required>
          <Input value={profileName} onChange={(e) => setProfileName(e.target.value)} placeholder="ECOMMERCE_SHORT_AD_V1" autoFocus />
        </Field>
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="outline" onClick={() => setProfileModal(false)}>Cancel</Button>
          <Button onClick={handleCreateProfile} loading={saving}>Create</Button>
        </div>
      </Modal>

      <Modal
        open={versionModal}
        onClose={() => setVersionModal(false)}
        title={"New version — " + (activeProfile?.name || "")}
        footer={
          <>
            <Button variant="outline" onClick={() => setVersionModal(false)}>Cancel</Button>
            <Button onClick={handleCreateVersion} loading={saving}>Save draft version</Button>
          </>
        }
      >
        <p className="mb-3 text-xs text-slate-500">
          Edit the quality criteria as JSON. Publishing a version makes it referenceable by batches.
        </p>
        <Field label="Payload (JSON)" required>
          <Textarea value={versionPayload} onChange={(e) => setVersionPayload(e.target.value)} rows={18} className="font-mono text-xs" spellCheck={false} />
        </Field>
        {payloadError && <p role="alert" className="mt-2 text-sm text-red-600">{payloadError}</p>}
      </Modal>

      <Modal open={batchModal} onClose={() => setBatchModal(false)} title="Create batch">
        <div className="space-y-4">
          <Field label="Batch name" required>
            <Input value={batchName} onChange={(e) => setBatchName(e.target.value)} placeholder="Round 3 — sunscreen shorts" autoFocus />
          </Field>
          <Field label="Prompt / brief (snapshot)" hint="Copied into the batch as an immutable snapshot.">
            <Textarea value={batchPrompt} onChange={(e) => setBatchPrompt(e.target.value)} rows={3} />
          </Field>
          <Field label="Quality profile version" required hint="Only published versions can be referenced.">
            <Select value={batchProfileVersionId} onChange={(e) => setBatchProfileVersionId(e.target.value)}>
              <option value="">Select a published version…</option>
              {publishedVersions.map((v) => (
                <option key={v.id} value={v.id}>
                  v{v.version} · profile #{v.profileId}{v.publishedAt ? " · " + formatDate(v.publishedAt) : ""}
                </option>
              ))}
            </Select>
          </Field>
        </div>
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="outline" onClick={() => setBatchModal(false)}>Cancel</Button>
          <Button onClick={handleCreateBatch} loading={saving}>Create batch</Button>
        </div>
      </Modal>
    </AppShell>
  );
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
