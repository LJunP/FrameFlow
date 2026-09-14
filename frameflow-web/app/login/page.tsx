'use client';

import { Suspense, useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';
import type { AuthPayload } from '@/lib/types';
import { safeNextPath } from '@/lib/security-contracts';
import Link from 'next/link';
import { BrandLockup } from '@/components/marketing/brand-lockup';

function LoginExperience() {
  const { user, ready, setSession } = useAuth();
  const router = useRouter();
  const params = useSearchParams();
  const next = useMemo(() => safeNextPath(params.get('next')), [params]);
  const [mode, setMode] = useState<'login' | 'register'>(() =>
    params.get('mode') === 'register' ? 'register' : 'login',
  );
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [teamName, setTeamName] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  // ★ 核心：只允许站内 next，认证后的导航不能相信外部 URL；否则登录页会变成开放重定向入口。
  useEffect(() => {
    if (ready && user) router.replace(next);
  }, [next, ready, router, user]);

  useEffect(() => {
    setMode(params.get('mode') === 'register' ? 'register' : 'login');
  }, [params]);

  function switchMode(value: 'login' | 'register') {
    const query = new URLSearchParams();
    if (value === 'register') query.set('mode', 'register');
    if (next !== '/workspace') query.set('next', next);
    router.replace(`/login${query.size ? `?${query}` : ''}`);
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      const body =
        mode === 'login'
          ? { email, password }
          : {
              email,
              password,
              displayName: displayName || email.split('@')[0],
              teamName: teamName || undefined,
            };
      const response = await fetch(`/api/auth/${mode}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      if (!response.ok) {
        const detail = await response.json().catch(() => ({}));
        throw new Error(detail.message || `HTTP ${response.status}`);
      }
      setSession((await response.json()) as AuthPayload);
      router.push(next);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setBusy(false);
    }
  }

  if (!ready || user) return null;
  const register = mode === 'register';

  return (
    <div className="auth-experience">
      <a className="skip-link" href="#auth-form">
        跳到认证表单
      </a>
      <section className="auth-story">
        <BrandLockup inverse />
        <div>
          <p>FRAME-BY-FRAME CONFIDENCE</p>
          <h1>
            把每一次
            <br />
            <em>视频选择</em>变成证据。
          </h1>
          <span>
            从批量上传、确定性质检到人工复核与锁定交付，所有关键判断都在同一条工作流中。
          </span>
        </div>
        <ol>
          <li>
            <b>01</b>建立项目与不可变 Brief
          </li>
          <li>
            <b>02</b>批量分析与证据定位
          </li>
          <li>
            <b>03</b>人工优选并导出交付
          </li>
        </ol>
        <small>Access Token 仅在浏览器内存保存；刷新凭据由 HttpOnly Cookie 保护。</small>
      </section>
      <main id="auth-form" className="auth-panel">
        <div>
          <p className="auth-panel-kicker">{register ? 'CREATE WORKSPACE' : 'WELCOME BACK'}</p>
          <h2>{register ? '创建团队工作区' : '登录到你的工作台'}</h2>
          <p className="auth-panel-lede">
            {register
              ? '注册后你会成为新团队的 Owner，可以开始建立第一个视频质检项目。'
              : '继续处理项目、候选审阅和优选交付。'}
          </p>
          <div className="auth-mode-tabs" role="tablist" aria-label="认证方式">
            <button
              type="button"
              role="tab"
              aria-selected={!register}
              className={!register ? 'active' : ''}
              onClick={() => switchMode('login')}
            >
              登录
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={register}
              className={register ? 'active' : ''}
              onClick={() => switchMode('register')}
            >
              注册
            </button>
          </div>
          <form onSubmit={submit}>
            <label>
              邮箱
              <input
                type="email"
                required
                autoComplete="email"
                value={email}
                placeholder="name@example.com"
                onChange={(event) => setEmail(event.target.value)}
              />
            </label>
            <label>
              密码 <small>至少 8 位</small>
              <input
                type="password"
                required
                minLength={8}
                autoComplete={register ? 'new-password' : 'current-password'}
                value={password}
                placeholder="请输入密码"
                onChange={(event) => setPassword(event.target.value)}
              />
            </label>
            {register && (
              <>
                <label>
                  昵称 <em>可选</em>
                  <input
                    value={displayName}
                    placeholder="用于团队协作展示"
                    onChange={(event) => setDisplayName(event.target.value)}
                  />
                </label>
                <label>
                  团队名称 <em>可选</em>
                  <input
                    value={teamName}
                    placeholder="未填写时创建默认名称"
                    onChange={(event) => setTeamName(event.target.value)}
                  />
                </label>
              </>
            )}
            {error && (
              <p className="auth-error" role="alert">
                {error}
              </p>
            )}
            <button className="auth-submit" disabled={busy} type="submit">
              {busy ? '正在安全验证…' : register ? '注册并进入工作台 ↗' : '登录并继续 ↗'}
            </button>
          </form>
          {!register && (
            <p className="auth-legal">
              <Link href="/reset-password">忘记密码？</Link>
            </p>
          )}
          <small className="auth-legal">
            继续即表示你理解：视频质量结论需按团队规则与人工复核共同确认。
          </small>
        </div>
      </main>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={<p className="loading auth-loading">正在准备认证界面…</p>}>
      <LoginExperience />
    </Suspense>
  );
}
