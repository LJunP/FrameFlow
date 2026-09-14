import type { AuthPayload } from './types';

const REDIRECT_BASE = 'https://frameflow.invalid';
const WORKSPACE_ROUTE = /^\/(?:workspace|account|team|projects|batches|candidates|selections)(?:\/|$)/;
const SAFE_GATEWAY_SEGMENT = /^[A-Za-z0-9_-]+$/;
const SAFE_REDIRECT_SEGMENT = /^[A-Za-z0-9_-]+$/;

/**
 * 认证完成后的去向只能是已知工作台页面。
 *
 * ★ 核心：必须让 WHATWG URL 解析器参与判断；只检查“以 / 开头”会放过
 * `/\\evil.example`，浏览器会把它规范化为站外地址。
 */
export function safeNextPath(value: string | null): string {
  if (!value || value.includes('\\')) return '/workspace';
  try {
    const target = new URL(value, REDIRECT_BASE);
    const segments = target.pathname.split('/').filter(Boolean);
    if (
      target.origin !== REDIRECT_BASE
      || !WORKSPACE_ROUTE.test(target.pathname)
      || segments.some((segment) => !SAFE_REDIRECT_SEGMENT.test(segment))
    ) {
      return '/workspace';
    }
    return `${target.pathname}${target.search}${target.hash}`;
  } catch {
    return '/workspace';
  }
}

/**
 * 把 Next catch-all 参数转换成唯一允许的 Java API 地址。
 *
 * ★ 核心：参数已经被 Next 解码过一次，仍可能包含 `%2e%2e`。逐段白名单
 * 在 URL 解析前拒绝它，再编码和校验最终前缀，防止二次解码穿越 `/api/v1/`。
 */
export function buildGatewayUpstreamUrl(
  apiBase: string,
  path: readonly string[],
  search: string,
): URL | null {
  if (
    !path.length
    || path[0].toLowerCase() === 'auth'
    || path.some((segment) => !SAFE_GATEWAY_SEGMENT.test(segment))
  ) {
    return null;
  }

  const base = new URL('/api/v1/', apiBase);
  const target = new URL(path.map(encodeURIComponent).join('/'), base);
  if (target.origin !== base.origin || !target.pathname.startsWith(base.pathname)) {
    return null;
  }
  target.search = search;
  return target;
}

export type RefreshStatus = 'authenticated' | 'unauthenticated' | 'transient-error';

/** 只有明确的请求/凭据无效才能销毁 refresh Cookie；限流和 5xx 必须可重试。 */
export function classifyRefreshStatus(status: number): RefreshStatus {
  if (status >= 200 && status < 300) return 'authenticated';
  if (status === 400 || status === 401) return 'unauthenticated';
  return 'transient-error';
}

/**
 * local 的生产镜像常通过 HTTP 访问，不能仅用 NODE_ENV 决定 Secure Cookie。
 * 远程 HTTPS 环境显式设 true；local 设 false；非法值直接拒绝启动，
 * 避免拼写错误悄悄降级 Cookie 安全性。
 */
export function resolveSecureCookie(
  nodeEnv: string | undefined,
  configured: string | undefined,
): boolean {
  if (configured === undefined || configured.trim() === '') return nodeEnv === 'production';
  const normalized = configured.trim().toLowerCase();
  if (normalized === 'true') return true;
  if (normalized === 'false') return false;
  throw new Error('FRAMEFLOW_COOKIE_SECURE must be true or false');
}

export function sameAuthUser(
  left: AuthPayload['user'] | null,
  right: AuthPayload['user'],
): boolean {
  return Boolean(
    left
    && left.id === right.id
    && left.email === right.email
    && left.displayName === right.displayName
    && Boolean(left.emailVerified) === Boolean(right.emailVerified),
  );
}

export function sameAuthTeam(
  left: AuthPayload['team'] | null,
  right: AuthPayload['team'],
): boolean {
  return Boolean(
    left
    && left.id === right.id
    && left.name === right.name
    && left.role === right.role,
  );
}
