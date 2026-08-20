"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { useSession } from "@/lib/store";
import { useToast } from "./ToastProvider";

const NAV = [
  { href: "/dashboard", label: "Dashboard" },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const { user, teams, teamId, selectTeam, logout, api, setTeams } = useSession();
  const router = useRouter();
  const pathname = usePathname();
  const { toast } = useToast();

  // Load teams on mount / when authed.
  useEffect(() => {
    if (!api.token) return;
    api
      .listMyTeams()
      .then((t) => {
        setTeams(t.items);
        if (!teamId && t.items.length > 0) {
          selectTeam(t.items[0].teamId);
        }
      })
      .catch(() => {
        /* backend offline — non-fatal */
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [api.token]);

  const handleLogout = () => {
    logout();
    toast("Signed out", "info");
    router.push("/login");
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-7xl items-center justify-between gap-4 px-4">
          <div className="flex items-center gap-6">
            <Link href="/dashboard" className="flex items-center gap-2 text-sm font-bold text-slate-900">
              <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-brand-600 text-xs font-black text-white">
                FS
              </span>
              FrameFlow Select
            </Link>
            <nav className="hidden items-center gap-1 md:flex">
              {NAV.map((item) => {
                const active = pathname.startsWith(item.href);
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    className={
                      "rounded-lg px-3 py-1.5 text-sm font-medium " +
                      (active
                        ? "bg-brand-50 text-brand-700"
                        : "text-slate-600 hover:bg-slate-100 hover:text-slate-900")
                    }
                  >
                    {item.label}
                  </Link>
                );
              })}
            </nav>
          </div>

          <div className="flex items-center gap-3">
            {teams && teams.length > 0 && (
              <label className="hidden items-center gap-2 text-sm sm:flex">
                <span className="text-slate-500">Team</span>
                <select
                  value={teamId ?? ""}
                  onChange={(e) => selectTeam(Number(e.target.value))}
                  className="h-8 rounded-lg border border-slate-300 bg-white px-2 text-sm text-slate-700"
                >
                  {teams.map((t) => (
                    <option key={t.teamId} value={t.teamId}>
                      {t.name} ({t.role})
                    </option>
                  ))}
                </select>
              </label>
            )}
            <div className="hidden text-right sm:block">
              <p className="text-xs font-medium text-slate-800">{user?.displayName || "…"}</p>
              <p className="text-[10px] text-slate-400">{user?.email || ""}</p>
            </div>
            <button
              onClick={handleLogout}
              className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-100"
            >
              Sign out
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-4 py-6">{children}</main>
    </div>
  );
}
