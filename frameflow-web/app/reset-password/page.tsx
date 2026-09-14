'use client';

import { Suspense, useMemo, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { BrandLockup } from '@/components/marketing/brand-lockup';

function ResetPasswordExperience() {
  const params = useSearchParams();
  const tokenFromLink = useMemo(() => params.get('token') ?? '', [params]);
  const [email, setEmail] = useState('');
  const [token, setToken] = useState(tokenFromLink);
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function requestReset(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const response = await fetch('/api/auth/password-reset-request', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email }),
      });
      if (response.status !== 204 && !response.ok) {
        const detail = await response.json().catch(() => ({}));
        throw new Error(detail.message || `HTTP ${response.status}`);
      }
      setMessage('如果该邮箱已注册，重置凭证已生成。当前版本不发送邮件：本地开发请查看后端日志中的 token。');
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setBusy(false);
    }
  }

  async function confirmReset(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const response = await fetch('/api/auth/password-reset-confirm', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token, newPassword: password }),
      });
      if (response.status !== 204 && !response.ok) {
        const detail = await response.json().catch(() => ({}));
        throw new Error(detail.message || `HTTP ${response.status}`);
      }
      setMessage('密码已更新，全部会话已吊销。请使用新密码登录。');
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-experience">
      <section className="auth-story">
        <BrandLockup inverse />
        <div>
          <p>ACCOUNT RECOVERY</p>
          <h1>
            重置
            <br />
            <em>登录密码</em>
          </h1>
          <span>申请始终返回相同结果，避免用接口探测哪些邮箱已经注册。</span>
        </div>
        <small>成功后全部刷新令牌立即作废，需要重新登录。</small>
      </section>
      <main className="auth-panel">
        <div>
          <p className="auth-panel-kicker">RESET PASSWORD</p>
          <h2>自助找回密码</h2>
          <form onSubmit={(event) => void requestReset(event)}>
            <label>
              注册邮箱
              <input
                type="email"
                required
                autoComplete="email"
                value={email}
                placeholder="name@example.com"
                onChange={(event) => setEmail(event.target.value)}
              />
            </label>
            <button className="auth-submit" disabled={busy} type="submit">
              {busy ? '正在申请…' : '申请重置凭证'}
            </button>
          </form>
          <form onSubmit={(event) => void confirmReset(event)} style={{ marginTop: 24 }}>
            <label>
              重置令牌
              <input
                required
                minLength={16}
                value={token}
                placeholder="从本地日志或重置链接中粘贴"
                onChange={(event) => setToken(event.target.value)}
              />
            </label>
            <label>
              新密码 <small>至少 8 位</small>
              <input
                type="password"
                required
                minLength={8}
                autoComplete="new-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </label>
            {error && <p className="auth-error" role="alert">{error}</p>}
            {message && <p className="auth-legal" role="status">{message}</p>}
            <button className="auth-submit" disabled={busy} type="submit">
              {busy ? '正在更新…' : '确认新密码'}
            </button>
          </form>
          <small className="auth-legal">
            <Link href="/login">返回登录</Link>
          </small>
        </div>
      </main>
    </div>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={<p className="loading auth-loading">正在准备重置界面…</p>}>
      <ResetPasswordExperience />
    </Suspense>
  );
}
