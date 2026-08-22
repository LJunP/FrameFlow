import { NextRequest, NextResponse } from 'next/server';

// ★ 核心（token 安全策略的服务端半边）：
// 登录/注册/刷新/登出走这里——refreshToken 只在本路由与浏览器 cookie
// 之间流转，通过 Set-Cookie(HttpOnly) 下发，响应体里绝不回传它。

const API_BASE = process.env.API_BASE ?? 'http://127.0.0.1:18080';
const REFRESH_COOKIE = 'ff_refresh';

function setRefreshCookie(resp: NextResponse, refreshToken: string) {
  resp.cookies.set(REFRESH_COOKIE, refreshToken, {
    httpOnly: true,
    sameSite: 'lax',
    path: '/api/auth',
    maxAge: 60 * 60 * 24 * 14,
  });
}

function clearRefreshCookie(resp: NextResponse) {
  resp.cookies.set(REFRESH_COOKIE, '', { httpOnly: true, path: '/api/auth', maxAge: 0 });
}

export async function POST(req: NextRequest, ctx: { params: Promise<{ action: string }> }) {
  const { action } = await ctx.params;

  if (action === 'login' || action === 'register') {
    const body = await req.json();
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (action === 'register') {
      // 注册幂等键在服务端生成（客户端重试不会造成双注册）
      headers['Idempotency-Key'] = crypto.randomUUID();
    }
    const upstream = await fetch(`${API_BASE}/api/v1/auth/${action}`, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
    });
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
    const upstream = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
    if (!upstream.ok) {
      const resp = NextResponse.json(await upstream.json().catch(() => ({})), { status: upstream.status });
      clearRefreshCookie(resp);
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
    const refreshToken = req.cookies.get(REFRESH_COOKIE)?.value;
    const auth = req.headers.get('authorization');
    if (refreshToken && auth) {
      await fetch(`${API_BASE}/api/v1/auth/logout`, {
        method: 'POST',
        headers: { Authorization: auth },
      }).catch(() => undefined);
    }
    const resp = NextResponse.json({ ok: true });
    clearRefreshCookie(resp);
    return resp;
  }

  return NextResponse.json({ code: 'NOT_FOUND' }, { status: 404 });
}
