'use client';

// ★ 核心：token 存储策略（docs/03 F8 完成标准）
// - accessToken：只存 React 内存（刷新页面丢失 → 用 refresh cookie 静默续期）
// - refreshToken：只在 HttpOnly cookie 里（JS 读不到，XSS 偷不走）
// - localStorage 明文存 token 被本项目禁止

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { refreshSession } from './api';
import type { AuthPayload } from './types';

interface AuthState {
  accessToken: string | null;
  user: AuthPayload['user'] | null;
  team: AuthPayload['team'] | null;
  ready: boolean;
  setSession: (p: AuthPayload) => void;
  setAccessToken: (t: string) => void;
  logout: () => Promise<void>;
}

const Ctx = createContext<AuthState>(null as never);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [user, setUser] = useState<AuthState['user']>(null);
  const [team, setTeam] = useState<AuthState['team']>(null);
  const [ready, setReady] = useState(false);

  // 页面加载时静默续期（内存 token 已丢，cookie 还在）——单飞防双挂载竞态
  useEffect(() => {
    (async () => {
      try {
        const data = await refreshSession();
        if (data) {
          setAccessToken(data.accessToken);
          setUser(data.user);
          setTeam(data.team);
        }
      } finally {
        setReady(true);
      }
    })();
  }, []);

  const setSession = useCallback((p: AuthPayload) => {
    setAccessToken(p.accessToken);
    setUser(p.user);
    setTeam(p.team);
  }, []);

  const logout = useCallback(async () => {
    if (accessToken) {
      // 让后端吊销刷新令牌（携带内存 access + cookie refresh）
      await fetch('/api/auth/logout', {
        method: 'POST',
        headers: { Authorization: `Bearer ${accessToken}` },
      }).catch(() => undefined);
    }
    setAccessToken(null);
    setUser(null);
    setTeam(null);
  }, [accessToken]);

  const value = useMemo(
    () => ({ accessToken, user, team, ready, setSession, setAccessToken, logout }),
    [accessToken, user, team, ready, setSession, logout],
  );
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth() {
  return useContext(Ctx);
}
