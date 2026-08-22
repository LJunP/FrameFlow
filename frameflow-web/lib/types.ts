// 与后端契约对应的最小类型集（手写轻量映射，字段与 docs/api 一致）

export interface AuthPayload {
  accessToken: string;
  user: { id: number; email: string; displayName: string };
  team: { id: number; name: string; role: string };
  refreshToken?: string; // 仅登录/注册响应携带，由代理写入 HttpOnly cookie
}

export interface PageOf<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export interface Project {
  id: number;
  name: string;
  description: string | null;
  status: string;
  currentBriefId: number | null;
  lockVersion: number;
}

export interface Brief {
  id: number;
  projectId: number;
  content: string;
  createdBy: number;
  createdAt: string;
}

export interface Profile {
  id: number;
  name: string;
  description: string | null;
  latestVersion: number | null;
}

export interface Batch {
  id: number;
  projectId: number;
  profileVersionId: number;
  profileVersionNo: number | null;
  briefId: number;
  status: string;
  capacity: number;
  candidateCounts: Record<string, number>;
}

export interface Candidate {
  id: number;
  batchId: number;
  status: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  objectKey: string;
  uploadMode: string | null;
  etag: string | null;
  probeError: string | null;
}

export interface RegisterCandidateResponse {
  candidateId: number;
  mode: 'SIMPLE' | 'MULTIPART';
  uploadUrl: string | null;
  uploadId: string | null;
  partSizeBytes: number;
  expiresAt: string;
}

export interface Finding {
  id: number;
  dimension: string;
  detector: string;
  detectorVersion: string;
  passed: boolean;
  severity: 'BLOCKER' | 'WARNING' | 'INFO';
  timecodeMs: number | null;
  evidence: string | null;
  message: string | null;
  verdict: 'PASS' | 'VIOLATE' | 'UNKNOWN' | 'ERROR' | null;
}

export interface RankingEntry {
  candidateId: number;
  rankNo: number;
  clusterId: number;
  representative: boolean;
  score: number;
  breakdown: string;
  excludedReason: string | null;
}

export interface SelectionItem {
  candidateId: number;
  machinePick: boolean;
  humanAction: 'INCLUDE' | 'EXCLUDE' | null;
  note: string | null;
}

export interface SelectionSummary {
  id: number;
  batchId: number;
  snapshotId: number;
  status: 'DRAFT' | 'LOCKED';
  topK: number;
  lockedAt: string | null;
}

export interface SelectionDetail extends SelectionSummary {
  items: SelectionItem[];
}
