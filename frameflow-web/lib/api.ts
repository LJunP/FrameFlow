'use client';

// 统一请求层：同源代理（/api/gw/*）+ 401 自动刷新一次后重试

import { useAuth } from './auth-context';
import { useCallback, useEffect, useMemo, useRef } from 'react';
import { classifyRefreshStatus } from './security-contracts';
import type { AuthPayload, UploadedPart } from './types';

const REFRESH_CLIENT_TIMEOUT_MS = 5000;

// ★ 核心：单飞刷新（single-flight）——React 开发模式 StrictMode 会双挂载
// effect，静默刷新若并发触发两次，第二次拿已被第一次轮换吊销的旧 token
// → 401 → cookie 被清 → 登录态意外丢失（E2E 实测抓到）。并发调用共享
// 同一个在途 Promise，轮换只发生一次。
let refreshInFlight: Promise<AuthPayload | null> | null = null;

export function refreshSession(): Promise<AuthPayload | null> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const resp = await fetch('/api/auth/refresh', {
          method: 'POST',
          signal: AbortSignal.timeout(REFRESH_CLIENT_TIMEOUT_MS),
        });
        const status = classifyRefreshStatus(resp.status);
        if (status === 'authenticated') return (await resp.json()) as AuthPayload;
        if (status === 'unauthenticated') return null;
        const detail = await resp.json().catch(() => ({}));
        throw new Error(detail.message || '会话服务暂时不可用，请重试');
      } catch (reason) {
        if (reason instanceof Error && reason.message.includes('会话服务')) throw reason;
        throw new Error('会话服务暂时不可用，请重试');
      } finally {
        refreshInFlight = null;
      }
    })();
  }
  return refreshInFlight;
}

/**
 * 批次分析进度的 SSE 订阅地址。
 *
 * ★ 核心：浏览器 EventSource 无法设置自定义请求头（带不了 Authorization），
 * 因此 access token 只能通过查询参数传给同源路由处理器
 * /api/batches/[id]/events，由它在服务端换成 Bearer 头再连 Java 后端。
 */
export function batchEventsUrl(batchId: string | number, accessToken: string): string {
  return `/api/batches/${batchId}/events?access_token=${encodeURIComponent(accessToken)}`;
}

/**
 * 全局搜索请求路径。
 *
 * ★ 核心：关键词统一在这里编码——搜索框内容可能含空格、`&`、`#`、中文，
 * 各处手写拼串漏掉 encodeURIComponent 会静默截断查询（如 "#" 之后的
 * 内容直接丢失），表现为"搜索结果莫名其妙对不上"。
 */
export function globalSearchPath(keyword: string, limit = 10): string {
  return `/search?q=${encodeURIComponent(keyword)}&limit=${limit}`;
}

export function useApi() {
  const { accessToken, setSession, clearSession } = useAuth();
  const tokenRef = useRef(accessToken);

  useEffect(() => {
    tokenRef.current = accessToken;
  }, [accessToken]);

  // ★ 核心：页面会把 API 方法放进 useEffect/useCallback 依赖；稳定引用可以避免每次状态更新都重新请求，造成红条闪烁和无限轮询。
  const request = useCallback(async function request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
    const doFetch = () =>
      fetch(`/api/gw${path}`, {
        ...init,
        headers: {
          'Content-Type': 'application/json',
          ...(tokenRef.current ? { Authorization: `Bearer ${tokenRef.current}` } : {}),
          ...(init.headers ?? {}),
        },
      });
    let resp: Response;
    try {
      resp = await doFetch();
    } catch (err) {
      // 网络级瞬断（连接复位/代理重启窗口）：幂等 GET 250ms 后重试一次
      const isGet = !init.method || init.method === 'GET';
      if (!isGet || !retry) throw err;
      await new Promise((r) => setTimeout(r, 250));
      resp = await doFetch();
    }
    if (resp.status === 401 && retry) {
      // access 过期：单飞静默换新，然后原样重试一次
      const data = await refreshSession();
      if (data) {
        // ★ 核心：续期返回的是完整 principal；必须同步 user/team/role，且 tokenRef 立即更新，避免旧角色 UI 与 effect 重发形成循环。
        tokenRef.current = data.accessToken;
        setSession(data);
        return request<T>(path, { ...init, headers: { ...init.headers, Authorization: `Bearer ${data.accessToken}` } }, false);
      }
      // refresh 明确返回 400/401 时，Cookie 已由 BFF 清除；同步清掉旧
      // principal，AppShell 才能回到登录页，而不是继续展示一个失效 Owner。
      tokenRef.current = null;
      clearSession();
    }
    if (!resp.ok) {
      let detail = `${resp.status}`;
      try {
        const body = await resp.json();
        detail = `${resp.status} ${body.code ?? ''} ${body.message ?? ''}`.trim();
      } catch { /* 非 JSON 错误体 */ }
      throw new Error(detail);
    }
    if (resp.status === 204) return undefined as T;
    return resp.json() as Promise<T>;
  }, [clearSession, setSession]);

  return useMemo(() => ({
    get: <T>(path: string) => request<T>(path),
    post: <T>(path: string, body?: unknown) =>
      request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
    put: <T>(path: string, body: unknown) =>
      request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
    del: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
  }), [request]);
}

/** XHR 直传（fetch 无上传进度；XHR 的 progress 事件是唯一顺手的姿势）。 */
export function putWithProgress(
  url: string,
  file: File,
  onProgress: (percent: number) => void,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', url);
    xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream');
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress(Math.round((e.loaded / e.total) * 100));
    };
    xhr.onload = () => (xhr.status < 300 ? resolve() : reject(new Error(`上传失败 HTTP ${xhr.status}`)));
    xhr.onerror = () => reject(new Error('网络错误（对象存储不可达？）'));
    xhr.send(file);
  });
}

// 分片并发度：太小吞吐差，太大在弱网/本地 MinIO 上容易触发超时与重试风暴
const MULTIPART_CONCURRENCY = 3;

/** 单片 PUT：与 SIMPLE 同一个 XHR 姿势，但必须把响应头 ETag 带回去——complete 合并靠它。 */
function putPart(
  url: string,
  blob: Blob,
  contentType: string,
  onBytes: (delta: number) => void,
): Promise<string> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', url);
    xhr.setRequestHeader('Content-Type', contentType);
    let loaded = 0;
    xhr.upload.onprogress = (e) => {
      // ★ 核心：并发分片各自触发 progress，只能累加增量；直接记 e.loaded 会被别的分片覆盖。
      onBytes(e.loaded - loaded);
      loaded = e.loaded;
    };
    xhr.onload = () => {
      if (xhr.status >= 300) return reject(new Error(`分片上传失败 HTTP ${xhr.status}`));
      // ★ 核心：浏览器跨域读响应头要求对象存储 CORS 暴露 ETag；拿不到 ETag 合并无从谈起。
      const etag = xhr.getResponseHeader('ETag');
      if (!etag) return reject(new Error('未读到分片 ETag（对象存储 CORS 需暴露 ETag 响应头）'));
      resolve(etag);
    };
    xhr.onerror = () => reject(new Error('网络错误（对象存储不可达？）'));
    xhr.send(blob);
  });
}

/**
 * MULTIPART 直传：按 partSizeBytes 切片 → 领每片预签名 URL → 小并发逐片 PUT
 * → 返回按分片号排序的 ETag 列表，交给 complete 合并。
 * fetchPartUrls 由调用方用 api.post 注入，保持本函数与请求层解耦、可单测。
 */
export async function uploadMultipartParts(opts: {
  file: File;
  partSizeBytes: number;
  fetchPartUrls: (partNumbers: number[]) => Promise<Record<string, string>>;
  onProgress: (percent: number) => void;
  skipParts?: UploadedPart[];
}): Promise<UploadedPart[]> {
  const { file, partSizeBytes, fetchPartUrls, onProgress, skipParts = [] } = opts;
  const partCount = Math.ceil(file.size / partSizeBytes);
  const done = new Map(skipParts.map((part) => [part.partNumber, part]));
  const partNumbers = Array.from({ length: partCount }, (_, i) => i + 1)
    .filter((n) => !done.has(n));
  if (partNumbers.length === 0) return [...done.values()].sort((a, b) => a.partNumber - b.partNumber);
  const partUrls = await fetchPartUrls(partNumbers);

  const uploaded: UploadedPart[] = [...done.values()];
  let uploadedBytes = skipParts.reduce((sum, part) => {
    const start = (part.partNumber - 1) * partSizeBytes;
    return sum + Math.min(partSizeBytes, file.size - start);
  }, 0);
  const report = () => onProgress(Math.min(100, Math.round((uploadedBytes / file.size) * 100)));

  let nextIndex = 0;
  async function worker() {
    // 单线程事件循环里 nextIndex++ 与 await 之间不会交错，各 worker 取片互不重叠
    while (nextIndex < partNumbers.length) {
      const index = nextIndex++;
      const partNumber = partNumbers[index];
      const url = partUrls[String(partNumber)];
      if (!url) throw new Error(`缺少分片 ${partNumber} 的上传地址`);
      const start = (partNumber - 1) * partSizeBytes;
      const blob = file.slice(start, Math.min(start + partSizeBytes, file.size));
      const etag = await putPart(url, blob, file.type || 'application/octet-stream', (delta) => {
        uploadedBytes += delta;
        report();
      });
      uploaded.push({ partNumber, etag });
    }
  }
  await Promise.all(
    Array.from({ length: Math.min(MULTIPART_CONCURRENCY, partNumbers.length) }, () => worker()),
  );
  // 后端 complete 会按分片号排序校验连续性，这里先排好，响应也更好读
  return uploaded.sort((a, b) => a.partNumber - b.partNumber);
}
