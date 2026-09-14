// 与后端契约对应的最小类型集（手写轻量映射，字段与 docs/api 一致）

export interface AuthPayload {
  accessToken: string;
  user: { id: number; email: string; displayName: string; emailVerified?: boolean };
  team: { id: number; name: string; role: string };
  refreshToken?: string; // 仅登录/注册响应携带，由代理写入 HttpOnly cookie
}

export interface UserTeam {
  id: number;
  name: string;
  role: string;
}

export interface TeamMember {
  userId: number;
  email: string;
  displayName: string;
  role: 'OWNER' | 'OPERATOR' | 'REVIEWER' | 'VIEWER';
}

/** 团队邀请记录。token 只在创建响应里出现一次；列表恒为 null。revokedAt 非空即已作废。 */
export interface TeamInvitation {
  id: number;
  email: string;
  role: 'OPERATOR' | 'REVIEWER' | 'VIEWER';
  token: string | null;
  expiresAt: string;
  acceptedAt: string | null;
  createdAt: string;
  revokedAt: string | null;
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

/** 项目页历史批次列表的行数据；candidateCounts 是各状态候选的数量统计。 */
export interface BatchSummary {
  id: number;
  projectId: number;
  profileVersionNo: number | null;
  status: string;
  capacity: number;
  candidateCounts: Record<string, number> | null;
}

/** 平台公开给用户选择的模型元数据；密钥和 endpoint 永远不进入浏览器契约。 */
export interface SemanticModelOption {
  id: string;
  label: string;
  description: string;
  provider: string;
  model: string;
  enabled: boolean;
}

export interface SemanticModelCatalog {
  defaultModelId: string;
  models: SemanticModelOption[];
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

/** 分片直传地址响应；key 是分片号的字符串形式（JSON 对象键只能是字符串）。 */
export interface UploadPartsResponse {
  partUrls: Record<string, string>;
}

/** complete 时上报的单片结果（对应后端 PartResult；分片号必须从 1 连续）。 */
export interface UploadedPart {
  partNumber: number;
  etag: string;
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

/** 全局搜索：按类型分组返回（对应 /api/v1/search，字段与 docs/api 契约一致）。 */
export interface SearchProjectHit {
  id: number;
  name: string;
  status: string;
  /** 匹配上下文：命中的是项目名称还是说明。 */
  matchedField: 'name' | 'description';
}

export interface SearchBatchHit {
  id: number;
  /** 回跳父级：批次详情挂在项目下。 */
  projectId: number;
  status: string;
  /** 匹配上下文：批次按所属项目名命中。 */
  projectName: string;
}

export interface SearchCandidateHit {
  id: number;
  /** 回跳父级：候选详情挂在批次下。 */
  batchId: number;
  fileName: string;
  status: string;
}

export interface SearchResponse {
  projects: SearchProjectHit[];
  batches: SearchBatchHit[];
  candidates: SearchCandidateHit[];
}
