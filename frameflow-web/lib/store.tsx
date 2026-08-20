"use client";

// Client-side session store: access token + active team selection.
//
// SECURITY NOTE: the access token is persisted to localStorage for developer
// convenience in this MVP web shell. In production this should move behind a
// BFF / HttpOnly cookie so the token is never readable from JS (see README).

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { ApiClient } from "./api";
import type { TeamList, TokenPair, User } from "./types";

const ACCESS_TOKEN_KEY = "frameflow.accessToken";
const REFRESH_TOKEN_KEY = "frameflow.refreshToken";
const TEAM_ID_KEY = "frameflow.teamId";

export interface Session {
  user: User | null;
  token: string | null;
  refreshToken: string | null;
  teamId: number | null;
  teams: TeamList["items"] | null;
  api: ApiClient;
  login: (pair: TokenPair) => void;
  logout: () => void;
  selectTeam: (teamId: number) => void;
  refreshUser: () => Promise<void>;
  setTeams: (items: TeamList["items"]) => void;
}

const SessionContext = createContext<Session | null>(null);

function readStorage(key: string): string | null {
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [teams, setTeams] = useState<TeamList["items"] | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [refreshToken, setRefreshToken] = useState<string | null>(null);
  const [teamId, setTeamId] = useState<number | null>(() => {
    const raw = readStorage(TEAM_ID_KEY);
    const n = raw ? Number(raw) : NaN;
    return Number.isFinite(n) ? n : null;
  });

  // Hydrate token from storage once on mount.
  const [hydrated, setHydrated] = useState(false);
  useEffect(() => {
    const t = readStorage(ACCESS_TOKEN_KEY);
    const r = readStorage(REFRESH_TOKEN_KEY);
    if (t) setToken(t);
    if (r) setRefreshToken(r);
    setHydrated(true);
  }, []);

  const api = useMemo(() => {
    const c = new ApiClient({ token, teamId });
    return c;
  }, [token, teamId]);

  // Keep client token/team in sync.
  useEffect(() => {
    api.token = token;
  }, [api, token]);
  useEffect(() => {
    api.teamId = teamId;
  }, [api, teamId]);

  const login = useCallback((pair: TokenPair) => {
    setToken(pair.accessToken);
    setRefreshToken(pair.refreshToken);
    try {
      window.localStorage.setItem(ACCESS_TOKEN_KEY, pair.accessToken);
      window.localStorage.setItem(REFRESH_TOKEN_KEY, pair.refreshToken);
    } catch {
      /* private mode etc. */
    }
  }, []);

  const logout = useCallback(() => {
    setToken(null);
    setRefreshToken(null);
    setUser(null);
    setTeams(null);
    setTeamId(null);
    try {
      window.localStorage.removeItem(ACCESS_TOKEN_KEY);
      window.localStorage.removeItem(REFRESH_TOKEN_KEY);
      window.localStorage.removeItem(TEAM_ID_KEY);
    } catch {
      /* ignore */
    }
  }, []);

  const selectTeam = useCallback((id: number) => {
    setTeamId(id);
    try {
      window.localStorage.setItem(TEAM_ID_KEY, String(id));
    } catch {
      /* ignore */
    }
  }, []);

  const refreshUser = useCallback(async () => {
    if (!token) return;
    const me = await api.me();
    setUser(me);
  }, [api, token]);

  // On hydration, if we have a token, refresh the current user.
  useEffect(() => {
    if (hydrated && token && !user) {
      refreshUser().catch(() => {
        /* token may be expired; login page will handle */
      });
    }
  }, [hydrated, token, user, refreshUser]);

  const value = useMemo<Session>(
    () => ({
      user,
      token,
      refreshToken,
      teamId,
      teams,
      api,
      login,
      logout,
      selectTeam,
      refreshUser,
      setTeams,
    }),
    [user, token, refreshToken, teamId, teams, api, login, logout, selectTeam, refreshUser]
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const ctx = useContext(SessionContext);
  if (!ctx) throw new Error("useSession must be used within <SessionProvider>");
  return ctx;
}

export { ACCESS_TOKEN_KEY, REFRESH_TOKEN_KEY, TEAM_ID_KEY };
