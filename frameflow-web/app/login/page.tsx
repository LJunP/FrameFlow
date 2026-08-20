"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useSession } from "@/lib/store";
import { useToast } from "@/components/ToastProvider";
import { Button, Card, CardBody, Field, Input } from "@/components/ui";
import { ApiError } from "@/lib/api";

type Mode = "login" | "register";

export default function LoginPage() {
  const router = useRouter();
  const { api, login } = useSession();
  const { toast } = useToast();
  const [mode, setMode] = useState<Mode>("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const switchMode = (m: Mode) => {
    setMode(m);
    setError(null);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      if (mode === "register") {
        await api.register({ email, password, displayName });
        toast("Account created — sign in to continue", "success");
        setMode("login");
        setLoading(false);
        return;
      }
      const pair = await api.login({ email, password });
      login(pair);
      toast("Signed in", "success");
      router.push("/dashboard");
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : "Unable to reach the FrameFlow backend.";
      setError(msg);
      toast(msg, "error");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-md">
        <div className="mb-6 text-center">
          <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-brand-600 text-lg font-black text-white">
            FS
          </div>
          <h1 className="text-xl font-bold text-slate-900">FrameFlow Select</h1>
          <p className="mt-1 text-sm text-slate-500">
            Batch quality review, evidence and Top-K selection for AI short video teams.
          </p>
        </div>

        <Card>
          <CardBody>
            <div className="mb-4 flex gap-1 rounded-lg bg-slate-100 p-1">
              {(["login", "register"] as Mode[]).map((m) => (
                <button
                  key={m}
                  onClick={() => switchMode(m)}
                  className={
                    "flex-1 rounded-md px-3 py-1.5 text-sm font-medium transition " +
                    (mode === m ? "bg-white text-slate-900 shadow-sm" : "text-slate-500 hover:text-slate-700")
                  }
                >
                  {m === "login" ? "Sign in" : "Create account"}
                </button>
              ))}
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">
              {mode === "register" && (
                <Field label="Display name" required>
                  <Input
                    value={displayName}
                    onChange={(e) => setDisplayName(e.target.value)}
                    placeholder="Jane Reviewer"
                    required
                  />
                </Field>
              )}
              <Field label="Email" required>
                <Input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@studio.example"
                  required
                  autoComplete="email"
                />
              </Field>
              <Field
                label="Password"
                required
                hint={mode === "register" ? "Minimum 8 characters" : undefined}
              >
                <Input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="password"
                  required
                  minLength={8}
                  autoComplete={mode === "login" ? "current-password" : "new-password"}
                />
              </Field>

              {error && (
                <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
                  {error}
                </p>
              )}

              <Button type="submit" className="w-full" loading={loading}>
                {mode === "login" ? "Sign in" : "Create account"}
              </Button>
            </form>
          </CardBody>
        </Card>

        <p className="mt-4 text-center text-xs text-slate-400">
          Requires the FrameFlow backend. Set{" "}
          <code className="rounded bg-slate-100 px-1 py-0.5">FRAMEFLOW_API</code> to point at it — see the
          web README for run steps.
        </p>
      </div>
    </div>
  );
}
