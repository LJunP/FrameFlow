// Typed API client for the FrameFlow Select Java backend.
// All endpoints are under /api/v1 and authenticated with a Bearer token.
// Product endpoints additionally require the X-Team-Id header.
//
// BASE_URL comes from the FRAMEFLOW_API env var (Next.js public env is exposed
// to the browser via NEXT_PUBLIC_ prefixed vars; FRAMEFLOW_API is read at build
// time / on the server, and we fall back to the local default).
//
// This client uses only the standard fetch API so it works on both the server
// (SSR / route handlers) and in the browser (client components).

import type {
  AnalysisRun,
  AnalysisRunStatus,
  Batch,
  BatchProgress,
  Candidate,
  ErrorBody,
  Finding,
  ProcessBatchResult,
  Project,
  QualityProfile,
  QualityProfileVersion,
  RankingEntry,
  RankingSnapshot,
  SelectionSet,
  SimilarityCluster,
  StartAnalysisResult,
  Team,
  TeamList,
  TokenPair,
  UploadSession,
  User,
} from "./types";

export const DEFAULT_API_BASE = "http://127.0.0.1:8080";

/** Resolve the backend base URL. */
export function resolveBaseUrl(): string {
  // FRAMEFLOW_API is the canonical env (server-side / build-time).
  if (typeof process !== "undefined" && process.env && process.env.FRAMEFLOW_API) {
    return process.env.FRAMEFLOW_API.replace(/\/$/, "");
  }
  // NEXT_PUBLIC_ variant is inlined into the browser bundle.
  if (typeof process !== "undefined" && process.env && process.env.NEXT_PUBLIC_FRAMEFLOW_API) {
    return process.env.NEXT_PUBLIC_FRAMEFLOW_API.replace(/\/$/, "");
  }
  return DEFAULT_API_BASE;
}

export function resolveMediaBase(): string {
  if (typeof process !== "undefined" && process.env && process.env.FRAMEFLOW_MEDIA) {
    return process.env.FRAMEFLOW_MEDIA.replace(/\/$/, "");
  }
  return resolveBaseUrl();
}

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly requestId?: string;
  constructor(status: number, body?: ErrorBody, fallback?: string) {
    super(body?.message || fallback || `HTTP ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.code = body?.code;
    this.requestId = body?.requestId;
  }
}

interface RequestOptions {
  token?: string | null;
  teamId?: number | null;
  baseUrl?: string;
  idempotencyKey?: string;
  accept?: string;
}

/**
 * FrameFlow API client. Create one per request (or keep a singleton) and pass
 * the current auth token + active team id. All methods are typed against the
 * OpenAPI contract.
 */
export class ApiClient {
  readonly baseUrl: string;
  readonly mediaBase: string;
  token: string | null = null;
  teamId: number | null = null;

  constructor(opts?: { token?: string | null; teamId?: number | null; baseUrl?: string }) {
    this.baseUrl = (opts?.baseUrl || resolveBaseUrl()).replace(/\/$/, "");
    this.mediaBase = resolveMediaBase();
    this.token = opts?.token ?? null;
    this.teamId = opts?.teamId ?? null;
  }

  /** Media URL for a candidate video in the review player. */
  candidateMediaUrl(candidateId: number | string): string {
    return `${this.mediaBase}/api/v1/media/candidates/${candidateId}/stream`;
  }

  private async request<T>(
    method: string,
    path: string,
    body?: unknown,
    opts: RequestOptions = {}
  ): Promise<T> {
    const token = opts.token !== undefined ? opts.token : this.token;
    const teamId = opts.teamId !== undefined ? opts.teamId : this.teamId;
    const base = opts.baseUrl || this.baseUrl;

    const headers: Record<string, string> = {};
    if (token) headers["Authorization"] = `Bearer ${token}`;
    if (teamId != null) headers["X-Team-Id"] = String(teamId);
    if (opts.idempotencyKey) headers["Idempotency-Key"] = opts.idempotencyKey;
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (opts.accept) headers["Accept"] = opts.accept;

    const url = `${base}${path}`;
    const res = await fetch(url, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });

    const contentType = res.headers.get("content-type") || "";
    let payload: unknown = undefined;
    const raw = await res.text();
    if (raw) {
      try {
        payload = JSON.parse(raw);
      } catch {
        payload = raw;
      }
    }

    if (!res.ok) {
      const errBody = typeof payload === "object" && payload ? (payload as ErrorBody) : undefined;
      throw new ApiError(res.status, errBody, `HTTP ${res.status} ${res.statusText}`);
    }

    if (opts.accept === "text/csv" || opts.accept === "application/json" && typeof payload === "string") {
      return payload as T;
    }
    return payload as T;
  }

  // ---- Auth ----
  register(input: { email: string; password: string; displayName: string }): Promise<User> {
    return this.request<User>("POST", "/api/v1/auth/register", input, { token: null });
  }
  login(input: { email: string; password: string }): Promise<TokenPair> {
    return this.request<TokenPair>("POST", "/api/v1/auth/login", input, { token: null });
  }
  me(): Promise<User> {
    return this.request<User>("GET", "/api/v1/auth/me");
  }

  // ---- Teams ----
  createTeam(name: string): Promise<Team> {
    return this.request<Team>("POST", "/api/v1/teams", { name }, { idempotencyKey: cryptoUuid() });
  }
  listMyTeams(): Promise<TeamList> {
    return this.request<TeamList>("GET", "/api/v1/teams");
  }

  // ---- Projects ----
  createProject(input: { name: string; description?: string }): Promise<{ id: number }> {
    return this.request<{ id: number }>("POST", "/api/v1/projects", input);
  }
  listProjects(): Promise<Project[]> {
    return this.request<Project[]>("GET", "/api/v1/projects");
  }
  getProject(projectId: number): Promise<Project> {
    return this.request<Project>("GET", `/api/v1/projects/${projectId}`);
  }

  // ---- Quality profiles ----
  listQualityProfiles(projectId: number): Promise<QualityProfile[]> {
    return this.request<QualityProfile[]>("GET", `/api/v1/projects/${projectId}/quality-profiles`);
  }
  createQualityProfile(projectId: number, input: { name: string; templateType?: string }): Promise<{ id: number }> {
    return this.request<{ id: number }>(
      "POST",
      `/api/v1/projects/${projectId}/quality-profiles`,
      input
    );
  }
  createProfileVersion(
    projectId: number,
    profileId: number,
    payload: string
  ): Promise<QualityProfileVersion> {
    return this.request<QualityProfileVersion>(
      "POST",
      `/api/v1/projects/${projectId}/quality-profiles/${profileId}/versions`,
      { payload }
    );
  }
  publishProfileVersion(versionId: number): Promise<unknown> {
    return this.request<unknown>("POST", `/api/v1/quality-profile-versions/${versionId}/publish`);
  }

  // ---- Batches ----
  listBatches(projectId: number): Promise<Batch[]> {
    return this.request<Batch[]>("GET", `/api/v1/projects/${projectId}/batches`);
  }
  createBatch(
    projectId: number,
    input: { name: string; promptText?: string; profileVersionId?: number }
  ): Promise<{ id: number }> {
    return this.request<{ id: number }>(
      "POST",
      `/api/v1/projects/${projectId}/batches`,
      input
    );
  }
  getBatch(batchId: number): Promise<Batch> {
    return this.request<Batch>("GET", `/api/v1/batches/${batchId}`);
  }
  addCandidates(batchId: number, input: { keys?: string[]; mediaTypes?: string[] }): Promise<number[]> {
    return this.request<number[]>("POST", `/api/v1/batches/${batchId}/candidates`, input);
  }
  listCandidates(batchId: number): Promise<Candidate[]> {
    return this.request<Candidate[]>("GET", `/api/v1/batches/${batchId}/candidates`);
  }

  // ---- Upload ----
  createUploadSession(candidateId: number): Promise<UploadSession> {
    return this.request<UploadSession>("POST", `/api/v1/candidates/${candidateId}/upload-session`);
  }
  completeUpload(
    candidateId: number,
    input: { sessionId: number; sizeBytes: number; contentDigest: string }
  ): Promise<Candidate> {
    return this.request<Candidate>(
      "POST",
      `/api/v1/candidates/${candidateId}/upload-complete`,
      input
    );
  }

  // ---- Analysis ----
  startAnalysis(batchId: number, candidateId?: number): Promise<StartAnalysisResult> {
    return this.request<StartAnalysisResult>(
      "POST",
      `/api/v1/batches/${batchId}/analysis-runs`,
      { candidateId }
    );
  }
  listAnalysisRuns(batchId: number): Promise<AnalysisRun[]> {
    return this.request<AnalysisRun[]>("GET", `/api/v1/batches/${batchId}/analysis-runs`);
  }
  getAnalysisRun(runId: number): Promise<AnalysisRun> {
    return this.request<AnalysisRun>("GET", `/api/v1/analysis-runs/${runId}`);
  }
  cancelAnalysisRun(runId: number): Promise<void> {
    return this.request<void>("POST", `/api/v1/analysis-runs/${runId}/cancel`);
  }
  listCandidateFindings(candidateId: number): Promise<Finding[]> {
    return this.request<Finding[]>("GET", `/api/v1/candidates/${candidateId}/findings`);
  }

  // ---- Batch engine (progress / process / rerun) ----
  processBatch(batchId: number): Promise<ProcessBatchResult> {
    return this.request<ProcessBatchResult>("POST", `/api/v1/batches/${batchId}/process`);
  }
  batchProgress(batchId: number): Promise<BatchProgress> {
    return this.request<BatchProgress>("POST", `/api/v1/batches/${batchId}/progress`);
  }
  rerunCandidate(candidateId: number): Promise<{ runId: number; mode?: string }> {
    return this.request<{ runId: number; mode?: string }>(
      "POST",
      `/api/v1/candidates/${candidateId}/rerun`
    );
  }

  // ---- Clustering / ranking / selection ----
  listSimilarityClusters(batchId: number): Promise<SimilarityCluster[]> {
    return this.request<SimilarityCluster[]>("GET", `/api/v1/batches/${batchId}/similarity-clusters`);
  }
  createSimilarityClusters(batchId: number, threshold?: number): Promise<SimilarityCluster[]> {
    return this.request<SimilarityCluster[]>(
      "POST",
      `/api/v1/batches/${batchId}/similarity-clusters`,
      { threshold }
    );
  }
  listRankingSnapshots(batchId: number): Promise<RankingSnapshot[]> {
    return this.request<RankingSnapshot[]>("GET", `/api/v1/batches/${batchId}/ranking-snapshots`);
  }
  createRankingSnapshot(batchId: number): Promise<RankingSnapshot | unknown> {
    return this.request<RankingSnapshot | unknown>(
      "POST",
      `/api/v1/batches/${batchId}/ranking-snapshots`,
      {}
    );
  }
  getRankingSnapshot(snapshotId: number): Promise<RankingEntry[]> {
    return this.request<RankingEntry[]>("GET", `/api/v1/ranking-snapshots/${snapshotId}`);
  }
  listSelectionSets(batchId: number): Promise<SelectionSet[]> {
    return this.request<SelectionSet[]>("GET", `/api/v1/batches/${batchId}/selection-sets`);
  }
  createSelectionSet(batchId: number, input: { name: string; topK: number }): Promise<SelectionSet> {
    return this.request<SelectionSet>(
      "POST",
      `/api/v1/batches/${batchId}/selection-sets`,
      input
    );
  }
  lockSelectionSet(selectionSetId: number): Promise<unknown> {
    return this.request<unknown>("POST", `/api/v1/selection-sets/${selectionSetId}/lock`);
  }
  exportSelectionSet(selectionSetId: number): Promise<string> {
    return this.request<string>(
      "GET",
      `/api/v1/selection-sets/${selectionSetId}/export`,
      undefined,
      { accept: "application/json" }
    );
  }
}

/** Small crypto UUID helper (works on server + modern browsers). */
export function cryptoUuid(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  // Fallback for older runtimes.
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/** Get the standard status color label for candidate decision states. */
export function candidateStatusTone(status?: string): string {
  switch (status) {
    case "AUTO_REJECT":
      return "red";
    case "ANALYSIS_ERROR":
      return "orange";
    case "REVIEW_REQUIRED":
      return "amber";
    case "SHORTLIST_CANDIDATE":
      return "green";
    case "ANALYZED":
      return "emerald";
    case "INVALID":
      return "rose";
    case "READY":
      return "sky";
    default:
      return "slate";
  }
}

// Re-export commonly used types for convenience.
export type {
  AnalysisRunStatus,
  AnalysisRun,
  Batch,
  BatchProgress,
  Candidate,
  Finding,
  ProcessBatchResult,
  Project,
  QualityProfile,
  QualityProfileVersion,
  RankingEntry,
  RankingSnapshot,
  SelectionSet,
  SimilarityCluster,
  StartAnalysisResult,
  Team,
  TeamList,
  TokenPair,
  UploadSession,
  User,
};
