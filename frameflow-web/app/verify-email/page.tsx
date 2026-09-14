'use client';

import { Suspense, useMemo, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { BrandLockup } from '@/components/marketing/brand-lockup';

function VerifyEmailExperience() {
  const params = useSearchParams();
  const token = useMemo(() => params.get('token') ?? '', [params]);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function confirm(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const response = await fetch('/api/auth/verify-email-confirm', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token }),
      });
      if (response.status !== 204 && !response.ok) {
        const detail = await response.json().catch(() => ({}));
        throw new Error(detail.message || `HTTP ${response.status}`);
      }
      setMessage('邮箱已验证，可以继续使用工作台。');
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
          <p>EMAIL VERIFICATION</p>
          <h1>验证邮箱</h1>
          <span>链接 24 小时内有效，验证后该邮箱才能被确认为你所有。</span>
        </div>
      </section>
      <main className="auth-panel">
        <div>
          <p className="auth-panel-kicker">VERIFY</p>
          <h2>确认这是你的邮箱</h2>
          {token ? (
            <form onSubmit={(event) => void confirm(event)}>
              {error && <p className="auth-error" role="alert">{error}</p>}
              {message && <p className="auth-legal" role="status">{message}</p>}
              <button className="auth-submit" disabled={busy} type="submit">
                {busy ? '正在验证…' : '验证邮箱'}
              </button>
            </form>
          ) : (
            <p className="auth-panel-lede">链接缺少令牌，请使用邮件里的完整地址。</p>
          )}
          <small className="auth-legal"><Link href="/login">返回登录</Link></small>
        </div>
      </main>
    </div>
  );
}

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={<p className="loading auth-loading">正在准备验证界面…</p>}>
      <VerifyEmailExperience />
    </Suspense>
  );
}
