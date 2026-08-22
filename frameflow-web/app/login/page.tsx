'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';
import type { AuthPayload } from '@/lib/types';

export default function LoginPage() {
  const { setSession } = useAuth();
  const router = useRouter();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [teamName, setTeamName] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      const body =
        mode === 'login'
          ? { email, password }
          : { email, password, displayName: displayName || email.split('@')[0], teamName: teamName || undefined };
      const resp = await fetch(`/api/auth/${mode}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      if (!resp.ok) {
        const detail = await resp.json().catch(() => ({}));
        throw new Error(detail.message ?? `HTTP ${resp.status}`);
      }
      setSession((await resp.json()) as AuthPayload);
      router.push('/');
    } catch (err) {
      setError(String(err instanceof Error ? err.message : err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="card" style={{ maxWidth: 420, margin: '60px auto' }}>
      <h2>{mode === 'login' ? '登录' : '注册（自动创建团队）'}</h2>
      <form onSubmit={submit} style={{ display: 'grid', gap: 10 }}>
        <input placeholder="邮箱" type="email" value={email} required onChange={(e) => setEmail(e.target.value)} />
        <input placeholder="密码（≥8 位）" type="password" value={password} required minLength={8} onChange={(e) => setPassword(e.target.value)} />
        {mode === 'register' && (
          <>
            <input placeholder="昵称（可选）" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
            <input placeholder="团队名（可选）" value={teamName} onChange={(e) => setTeamName(e.target.value)} />
          </>
        )}
        <button className="btn" disabled={busy}>
          {busy ? '请稍候…' : mode === 'login' ? '登录' : '注册并登录'}
        </button>
        <button
          type="button"
          className="btn secondary"
          onClick={() => setMode(mode === 'login' ? 'register' : 'login')}
        >
          {mode === 'login' ? '没有账号？注册' : '已有账号？登录'}
        </button>
        {error && <div className="notice bad">{error}</div>}
      </form>
    </div>
  );
}
