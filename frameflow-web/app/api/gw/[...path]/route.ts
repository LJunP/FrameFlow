import { NextRequest, NextResponse } from 'next/server';
import { buildGatewayUpstreamUrl } from '@/lib/security-contracts';

// 同源通用代理：浏览器 → Next(route handler) → Java API。
// 收益一：前端与 Java 之间零 CORS 配置；
// 收益二：API_BASE 是服务端环境变量，浏览器永远看不到后端拓扑。

const API_BASE = process.env.API_BASE ?? 'http://127.0.0.1:18080';

async function forward(req: NextRequest, path: string[]) {
  const url = buildGatewayUpstreamUrl(API_BASE, path, req.nextUrl.search);
  if (!url) {
    return NextResponse.json({ code: 'NOT_FOUND' }, { status: 404 });
  }
  const headers = new Headers();
  const auth = req.headers.get('authorization');
  if (auth) headers.set('Authorization', auth);
  const contentType = req.headers.get('content-type');
  if (contentType) headers.set('Content-Type', contentType);

  const init: RequestInit = { method: req.method, headers };
  if (!['GET', 'HEAD'].includes(req.method)) {
    init.body = await req.arrayBuffer();
  }
  const upstream = await fetch(url, init);
  const body = await upstream.arrayBuffer();
  const resp = new NextResponse(body, { status: upstream.status });
  const upstreamType = upstream.headers.get('content-type');
  if (upstreamType) resp.headers.set('Content-Type', upstreamType);
  return resp;
}

type Ctx = { params: Promise<{ path: string[] }> };

export async function GET(req: NextRequest, ctx: Ctx) {
  return forward(req, (await ctx.params).path);
}
export async function POST(req: NextRequest, ctx: Ctx) {
  return forward(req, (await ctx.params).path);
}
export async function PUT(req: NextRequest, ctx: Ctx) {
  return forward(req, (await ctx.params).path);
}
export async function DELETE(req: NextRequest, ctx: Ctx) {
  return forward(req, (await ctx.params).path);
}
