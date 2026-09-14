import { NextRequest, NextResponse } from 'next/server';
import { classifyRefreshStatus, resolveSecureCookie } from '@/lib/security-contracts';

// ★ 核心（token 安全策略的服务端半边）：
// 登录/注册/刷新/登出走这里——refreshToken 只在本路由与浏览器 cookie
// 之间流转，通过 Set-Cookie(HttpOnly) 下发，响应体里绝不回传它。

const API_BASE = process.env.API_BASE ?? 'http://127.0.0.1:18080';
const REFRESH_COOKIE = 'ff_refresh';
const AUTH_TIMEOUT_MS = 3500;
const LOGOUT_TIMEOUT_MS = 1800;
const SECURE_COOKIE = resolveSecureCookie(process.env.NODE_ENV, process.env.FRAMEFLOW_COOKIE_SECURE);

function upstreamUnavailable() {
  return NextResponse.json(
    { code: 'AUTH_UPSTREAM_UNAVAILABLE', message: '认证服务暂时不可用，请稍后重试' },
    { status: 503 },
  );
}

function setRefreshCookie(resp: NextResponse, refreshToken: string) {
  resp.cookies.set(REFRESH_COOKIE, refreshToken, {
    httpOnly: true,
    sameSite: 'lax',
    secure: SECURE_COOKIE,
    path: '/api/auth',
    maxAge: 60 * 60 * 24 * 14,
  });
}

function clearRefreshCookie(resp: NextResponse) {
  resp.cookies.set(REFRESH_COOKIE, '', {
    httpOnly: true,
    sameSite: 'lax',
    secure: SECURE_COOKIE,
    path: '/api/auth',
    maxAge: 0,
  });
}

async function callLogout(authorization: string, signal: AbortSignal): Promise<Response> {
  return fetch(`${API_BASE}/api/v1/auth/logout`, {
    method: 'POST',
    headers: { Authorization: authorization },
    signal,
  });
}

async function tryRevokeSession(
  authorization: string | null,
  refreshToken: string | undefined,
): Promise<void> {
  // 一个总 deadline 覆盖“旧 access 登出 → 必要时 refresh → 新 access 登出”，
  // 避免三个串行请求各自计时后超过浏览器等待上限。
  const signal = AbortSignal.timeout(LOGOUT_TIMEOUT_MS);
  try {
    if (authorization) {
      const logout = await callLogout(authorization, signal);
      if (logout.ok || logout.status !== 401) return;
    }

    if (!refreshToken) return;
    // access 已过期或浏览器内存已丢失时，用 HttpOnly refresh 换一张短期 access，
    // 随即吊销该用户全部 refresh；轮换产生的新 refresh 从不写回浏览器。
    const refreshed = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
      signal,
    });
    if (!refreshed.ok) return;
    const data = (await refreshed.json()) as { accessToken?: unknown };
    if (typeof data.accessToken !== 'string') return;
    await callLogout(`Bearer ${data.accessToken}`, signal);
  } catch {
    // 本地 HttpOnly Cookie 的清理由调用方无条件完成；上游吊销是有界尽力动作。
  }
}

export async function POST(req: NextRequest, ctx: { params: Promise<{ action: string }> }) {
  const { action } = await ctx.params;

  if (action === 'password-reset-request' || action === 'password-reset-confirm') {
    const body = await req.json();
    const upstreamPath = action === 'password-reset-request'
      ? 'auth/password-reset/request'
      : 'auth/password-reset/confirm';
    let upstream: Response;
    try {
      upstream = await fetch(`${API_BASE}/api/v1/${upstreamPath}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
        signal: AbortSignal.timeout(AUTH_TIMEOUT_MS),
      });
    } catch {
      return upstreamUnavailable();
    }
    if (upstream.status === 204) return new NextResponse(null, { status: 204 });
    return NextResponse.json(await upstream.json().catch(() => ({})), { status: upstream.status });
  }

  if (action === 'login' || action === 'register' || action === 'accept-invitation') {
    const body = await req.json();
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (action === 'register') {
      // 注册幂等键在服务端生成（客户端重试不会造成双注册）
      headers['Idempotency-Key'] = crypto.randomUUID();
    }
    // 接受邀请与登录同享"双令牌 → HttpOnly Cookie"路径：成功后受邀人直接进工作台
    const upstreamPath =
      action === 'accept-invitation' ? 'invitations/accept' : `auth/${action}`;
    let upstream: Response;
    try {
      upstream = await fetch(`${API_BASE}/api/v1/${upstreamPath}`, {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
        signal: AbortSignal.timeout(AUTH_TIMEOUT_MS),
      });
    } catch {
      return upstreamUnavailable();
    }
    if (!upstream.ok) {
      return NextResponse.json(await upstream.json().catch(() => ({})), { status: upstream.status });
    }
    const data = await upstream.json(); // { user, team, accessToken, refreshToken }
    const resp = NextResponse.json({
      user: data.user,
      team: data.team,
      accessToken: data.accessToken,
    });
    setRefreshCookie(resp, data.refreshToken);
    return resp;
  }

  if (action === 'refresh') {
    const refreshToken = req.cookies.get(REFRESH_COOKIE)?.value;
    if (!refreshToken) {
      return NextResponse.json({ code: 'UNAUTHENTICATED' }, { status: 401 });
    }
    let upstream: Response;
    try {
      upstream = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
        signal: AbortSignal.timeout(AUTH_TIMEOUT_MS),
      });
    } catch {
      // ★ 核心：临时故障不能销毁仍可能有效的 refresh Cookie；否则一次 503/超时就会把用户永久登出。
      return upstreamUnavailable();
    }
    if (!upstream.ok) {
      const resp = NextResponse.json(await upstream.json().catch(() => ({})), { status: upstream.status });
      if (classifyRefreshStatus(upstream.status) === 'unauthenticated') clearRefreshCookie(resp);
      return resp;
    }
    const data = await upstream.json();
    const resp = NextResponse.json({
      user: data.user,
      team: data.team,
      accessToken: data.accessToken,
    });
    setRefreshCookie(resp, data.refreshToken); // 轮换后的新 refresh
    return resp;
  }

  if (action === 'logout') {
    const auth = req.headers.get('authorization');
    const refreshToken = req.cookies.get(REFRESH_COOKIE)?.value;
    // ★ 核心：服务端吊销必须有界，且上限短于浏览器等待时间；无论上游成功、超时还是不可达，本响应都要及时清掉 HttpOnly Cookie。
    await tryRevokeSession(auth, refreshToken);
    const resp = NextResponse.json({ ok: true });
    clearRefreshCookie(resp);
    return resp;
  }

  return NextResponse.json({ code: 'NOT_FOUND' }, { status: 404 });
}
