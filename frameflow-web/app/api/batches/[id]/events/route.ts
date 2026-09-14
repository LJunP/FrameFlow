import { NextRequest } from 'next/server';

// SSE 专用代理：EventSource 无法设置自定义请求头，因此 token 由前端通过
// 查询参数传入，本路由在服务端转成 Authorization: Bearer 再连 Java 的
// /api/v1/batches/{id}/events，并把上游事件流原样透传给浏览器。
//
// ★ 核心：必须"流式"转发（直接返回 upstream.body），不能像 /api/gw 那样
// await arrayBuffer()——那会把一次长连接的事件全部缓冲到结束才返回，
// SSE 退化成"最后一次性到达"，实时推送完全失效。

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

const API_BASE = process.env.API_BASE ?? 'http://127.0.0.1:18080';

function jsonError(code: string, status: number) {
  return new Response(JSON.stringify({ code }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

export async function GET(req: NextRequest, ctx: { params: Promise<{ id: string }> }) {
  const { id } = await ctx.params;
  // 只接受数字批次 ID，避免把用户输入拼进上游 URL 造成路径穿越/SSRF。
  if (!/^\d+$/.test(id)) {
    return jsonError('NOT_FOUND', 404);
  }
  const token = req.nextUrl.searchParams.get('access_token');
  if (!token) {
    return jsonError('UNAUTHENTICATED', 401);
  }

  let upstream: Response;
  try {
    upstream = await fetch(`${API_BASE}/api/v1/batches/${id}/events`, {
      // 不发送 Accept: text/event-stream——上游成功路径由接口 produces 决定，
      // 而失败（403/404）需要按 JSON 协商错误体；浏览器侧仍会收到事件流。
      headers: { Authorization: `Bearer ${token}` },
      // 浏览器关闭 EventSource 时中止上游请求，避免后端连接悬挂到 10 分钟超时。
      signal: req.signal,
      cache: 'no-store',
    });
  } catch {
    return jsonError('UPSTREAM_UNAVAILABLE', 503);
  }

  if (!upstream.ok || !upstream.body) {
    // 鉴权失败/批次不存在等：把状态码透传，浏览器 EventSource 会触发 onerror。
    return new Response(upstream.body, {
      status: upstream.status,
      headers: { 'Content-Type': upstream.headers.get('content-type') ?? 'application/json' },
    });
  }

  return new Response(upstream.body, {
    status: 200,
    headers: {
      'Content-Type': 'text/event-stream; charset=utf-8',
      // no-transform 阻止中间层压缩改写；禁用各级代理缓冲，保证事件即时到达。
      'Cache-Control': 'no-cache, no-transform',
      'X-Accel-Buffering': 'no',
    },
  });
}
