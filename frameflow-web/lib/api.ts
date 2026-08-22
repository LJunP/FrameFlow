'use client';

// 统一请求层：同源代理（/api/gw/*）+ 401 自动刷新一次后重试

import { useAuth } from './auth-context';
import type { AuthPayload } from './types';

// ★ 核心：单飞刷新（single-flight）——React 开发模式 StrictMode 会双挂载
// effect，静默刷新若并发触发两次，第二次拿已被第一次轮换吊销的旧 token
// → 401 → cookie 被清 → 登录态意外丢失（E2E 实测抓到）。并发调用共享
// 同一个在途 Promise，轮换只发生一次。
let refreshInFlight: Promise<AuthPayload | null> | null = null;

export function refreshSession(): Promise<AuthPayload | null> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const resp = await fetch('/api/auth/refresh', { method: 'POST' });
        return resp.ok ? ((await resp.json()) as AuthPayload) : null;
      } catch {
        return null;
      } finally {
        refreshInFlight = null;
      }
    })();
  }
  return refreshInFlight;
}

export function useApi() {
  const { accessToken, setAccessToken } = useAuth();

  async function request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
    const doFetch = () =>
      fetch(`/api/gw${path}`, {
        ...init,
        headers: {
          'Content-Type': 'application/json',
          ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
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
        setAccessToken(data.accessToken);
        return request<T>(path, { ...init, headers: { ...init.headers, Authorization: `Bearer ${data.accessToken}` } }, false);
      }
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
  }

  return {
    get: <T>(path: string) => request<T>(path),
    post: <T>(path: string, body?: unknown) =>
      request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
    put: <T>(path: string, body: unknown) =>
      request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  };
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
