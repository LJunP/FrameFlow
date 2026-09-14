'use client';

import { Suspense, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';
import type { AuthPayload } from '@/lib/types';
import { BrandLockup } from '@/components/marketing/brand-lockup';

// 公开页面（不在 AppShell 的受保护路径内）：受邀人凭 64 位随机令牌入团，
// 令牌本身就是凭证；成功后 BFF 写入 HttpOnly refresh cookie，直接进工作台。
function AcceptInviteExperience() {
  const { setSession } = useAuth();
  const router = useRouter();
  const params = useSearchParams();
  const token = useMemo(() => params.get('token') ?? '', [params]);
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      const response = await fetch('/api/auth/accept-invitation', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          token,
          password,
          displayName: displayName || undefined,
        }),
      });
      if (!response.ok) {
        const detail = await response.json().catch(() => ({}));
        throw new Error(detail.message || `HTTP ${response.status}`);
      }
      setSession((await response.json()) as AuthPayload);
      router.push('/team');
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-experience">
      <a className="skip-link" href="#invite-form">
        跳到接受邀请表单
      </a>
      <section className="auth-story">
        <BrandLockup inverse />
        <div>
          <p>TEAM INVITATION</p>
          <h1>
            你受邀加入一个
            <br />
            <em>视频质检</em>团队。
          </h1>
          <span>
            接受邀请后，你将以邀请中指定的角色进入团队工作区，参与批量上传、证据审阅与优选交付。
          </span>
        </div>
        <small>邀请链接为一次性凭证，7 天内有效；链接只应来自你信任的团队 Owner。</small>
      </section>
      <main id="invite-form" className="auth-panel">
        <div>
          <p className="auth-panel-kicker">ACCEPT INVITATION</p>
          <h2>接受团队邀请</h2>
          <p className="auth-panel-lede">
            {token
              ? '设置你的登录密码即可完成入团；若受邀邮箱已有 FrameFlow 账号，请输入该账号的原密码确认是本人。'
              : '链接缺少邀请令牌，请检查是否从完整邀请链接进入。'}
          </p>
          {token && (
            <form onSubmit={submit}>
              <label>
                密码 <small>至少 8 位</small>
                <input
                  type="password"
                  required
                  minLength={8}
                  autoComplete="new-password"
                  value={password}
                  placeholder="新账号将以此密码登录；已有账号请输入原密码"
                  onChange={(event) => setPassword(event.target.value)}
                />
              </label>
              <label>
                昵称 <em>可选，仅新账号生效</em>
                <input
                  value={displayName}
                  placeholder="用于团队协作展示，默认取邮箱前缀"
                  onChange={(event) => setDisplayName(event.target.value)}
                />
              </label>
              {error && (
                <p className="auth-error" role="alert">
                  {error}
                </p>
              )}
              <button className="auth-submit" disabled={busy} type="submit">
                {busy ? '正在验证邀请…' : '接受邀请并进入团队 ↗'}
              </button>
            </form>
          )}
          <small className="auth-legal">
            接受即表示你理解：视频质量结论需按团队规则与人工复核共同确认。
          </small>
        </div>
      </main>
    </div>
  );
}

export default function AcceptInvitePage() {
  return (
    <Suspense fallback={<p className="loading auth-loading">正在准备邀请界面…</p>}>
      <AcceptInviteExperience />
    </Suspense>
  );
}
