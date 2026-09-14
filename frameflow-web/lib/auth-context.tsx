'use client';

// ★ 核心：token 存储策略（docs/03 F8 完成标准）
// - accessToken：只存 React 内存（刷新页面丢失 → 用 refresh cookie 静默续期）
// - refreshToken：只在 HttpOnly cookie 里（JS 读不到，XSS 偷不走）
// - localStorage 明文存 token 被本项目禁止

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { refreshSession } from './api';
import { sameAuthTeam, sameAuthUser } from './security-contracts';
import type { AuthPayload } from './types';

interface AuthState {
  accessToken: string | null;
  user: AuthPayload['user'] | null;
  team: AuthPayload['team'] | null;
  ready: boolean;
  sessionError: string | null;
  setSession: (p: AuthPayload) => void;
  updateUser: (user: AuthPayload['user']) => void;
  clearSession: () => void;
  retrySession: () => Promise<void>;
  logout: () => Promise<void>;
}

const Ctx = createContext<AuthState>(null as never);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [user, setUser] = useState<AuthState['user']>(null);
  const [team, setTeam] = useState<AuthState['team']>(null);
  const [ready, setReady] = useState(false);
  const [sessionError, setSessionError] = useState<string | null>(null);

  const setSession = useCallback((p: AuthPayload) => {
    setAccessToken(p.accessToken);
    // 同值 principal 保留对象引用，避免一次正常 token 续期让所有依赖
    // user/team 的页面 effect 再发一轮相同请求；角色真的变化时仍会更新。
    setUser((current) => sameAuthUser(current, p.user) ? current : p.user);
    setTeam((current) => sameAuthTeam(current, p.team) ? current : p.team);
    setSessionError(null);
  }, []);

  const clearSession = useCallback(() => {
    setAccessToken(null);
    setUser(null);
    setTeam(null);
    setSessionError(null);
  }, []);

  // 资料编辑（如改昵称）后同步会话内的用户信息；token 与团队保持不变
  const updateUser = useCallback((next: AuthPayload['user']) => {
    setUser((current) => sameAuthUser(current, next) ? current : next);
  }, []);

  const retrySession = useCallback(async () => {
    setReady(false);
    setSessionError(null);
    try {
      const data = await refreshSession();
      if (data) setSession(data);
      else clearSession();
    } catch (reason) {
      // ★ 核心：临时网络/上游故障不等于退出登录。保留 HttpOnly Cookie 并给用户重试入口；若误判成未登录，会把有效会话赶回登录页。
      setSessionError(reason instanceof Error ? reason.message : '暂时无法验证会话');
    } finally {
      setReady(true);
    }
  }, [clearSession, setSession]);

  // 页面加载时静默续期（内存 token 已丢，cookie 还在）——单飞防双挂载竞态。
  useEffect(() => {
    void retrySession();
  }, [retrySession]);

  const logout = useCallback(async () => {
    const token = accessToken;
    // ★ 核心：先清理浏览器内存会话，再尽力通知服务端吊销 refresh 会话；否则上游慢或不可达时，用户会被卡在已登录界面。
    clearSession();
    await fetch('/api/auth/logout', {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      signal: AbortSignal.timeout(3000),
    }).catch(() => undefined);
  }, [accessToken, clearSession]);

  const value = useMemo(
    () => ({ accessToken, user, team, ready, sessionError, setSession, updateUser, clearSession, retrySession, logout }),
    [accessToken, user, team, ready, sessionError, setSession, updateUser, clearSession, retrySession, logout],
  );
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth() {
  return useContext(Ctx);
}
