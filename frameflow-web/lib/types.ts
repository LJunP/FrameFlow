// TypeScript domain types mirroring docs/04-api/openapi/frameflow-v1.yaml
// and the product module controllers (frameflow-modules/product).

export interface User {
  id: number;
  email: string;
  displayName: string;
  status: "ACTIVE" | "DISABLED" | "PENDING";
  lastLoginAt?: string | null;
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: "Bearer";
}

export interface Team {
  id: number;
  name: string;
  createdAt: string;
}

export interface TeamMembershipSummary {
  teamId: number;
  name: string;
  role: "OWNER" | "OPERATOR" | "REVIEWER" | "VIEWER";
  status: "ACTIVE";
  createdAt: string;
}

export interface TeamList {
  items: TeamMembershipSummary[];
}

export interface Project {
  id: number;
  teamId: number;
  name: string;
  description?: string | null;
  status?: string | null;
  archivedAt?: string | null;
  createdBy: number;
  createdAt: string;
  updatedAt: string;
}

export interface QualityProfile {
  id: number;
  teamId: number;
  projectId: number;
  name: string;
  templateType?: string | null;
  status?: string | null;
  createdBy: number;
  createdAt: string;
  updatedAt: string;
}

export interface QualityProfileVersion {
  id: number;
  profileId: number;
  version: number;
  status?: string | null;
  payload?: string | null;
  payloadDigest?: string | null;
  publishedAt?: string | null;
  createdBy: number;
  createdAt: string;
}

export interface Batch {
  id: number;
  teamId: number;
  projectId: number;
  name: string;
  status?: string | null;
  promptText?: string | null;
  profileVersionId?: number | null;
  briefId?: number | null;
  capacityLimit?: number | null;
  failureCount?: number | null;
  createdBy: number;
  startedAt?: string | null;
  completedAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

export type CandidateStatus =
  | "DRAFT"
  | "PENDING"
  | "READY"
  | "UPLOADING"
  | "INVALID"
  | "ANALYZING"
  | "ANALYZED"
  | "AUTO_REJECT"
  | "REVIEW_REQUIRED"
  | "SHORTLIST_CANDIDATE"
  | "ANALYSIS_ERROR"
  | string;

export interface Candidate {
  id: number;
  teamId: number;
  batchId: number;
  parentCandidateId?: number | null;
  createdBy: number;
  candidateKey?: string | null;
  status: CandidateStatus;
  mediaType?: string | null;
  sizeBytes?: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface Finding {
  id?: number;
  analysisRunId?: number;
  candidateId?: number;
  ruleId?: string;
  detectorId?: string;
  detectorVersion?: string;
  dimension?: string;
  findingType?: string;
  verdict?: string;
  severity?: string;
  automationAction?: string;
  summary?: string;
  confidence?: number;
  startMs?: number;
  endMs?: number;
  evidence?: string;
  origin?: string;
  createdAt?: string;
}

export type AnalysisRunStatus =
  | "PENDING"
  | "QUEUED"
  | "RUNNING"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED"
  | "PARTIAL"
  | string;

export interface AnalysisRun {
  id: number;
  teamId: number;
  batchId: number;
  candidateVersionId?: number | null;
  createdBy: number;
  status: AnalysisRunStatus;
  commandId?: string | null;
  resultDigest?: string | null;
  decision?: string | null;
  qualityVector?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SimilarityCluster {
  id: number;
  batchId: number;
  representativeCandidateId?: number | null;
  clusterKey?: string | null;
  algorithmVersion?: string | null;
  threshold?: number | null;
  createdAt: string;
}

export interface RankingSnapshot {
  id: number;
  batchId: number;
  createdBy: number;
  snapshotVersion?: number | null;
  algorithmVersion?: string | null;
  weights?: string | null;
  featureSchemaVersion?: string | null;
  lockedAt?: string | null;
  createdAt: string;
}

export interface RankingEntry {
  rankingSnapshotId?: number;
  candidateId: number;
  rank?: number;
  baseScore?: number;
  finalScore?: number;
  breakdown?: string | null;
}

export interface SelectionSet {
  id: number;
  batchId: number;
  lockedBy?: number | null;
  createdBy: number;
  name?: string | null;
  status?: string | null;
  theme?: string | null;
  topK?: number | null;
  lockedAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface BatchProgress {
  batchId: number;
  candidateStatuses?: { [status: string]: number };
  total: number;
  failed: number;
  analyzed: number;
}

export interface ProcessBatchResult {
  batchId: number;
  candidateStatuses: { [status: string]: number };
  total: number;
  failed: number;
  analyzed: number;
}

export interface ErrorBody {
  code?: string;
  message?: string;
  requestId?: string;
  traceId?: string;
}

// Endpoint micro-results
export interface StartAnalysisResult {
  runId: number;
  commandId?: string;
  mode?: string;
}
export interface UploadSession {
  sessionId: number;
  status?: string;
}
