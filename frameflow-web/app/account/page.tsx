'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';

export default function AccountPage() {
  const { user, team, ready, logout } = useAuth(); const router = useRouter();
  useEffect(() => { if (ready && !user) router.replace('/login?next=/account'); }, [ready, router, user]);
  if (!ready || !user) return <p className="loading">正在读取账户…</p>;
  return <div className="settings-page"><header className="settings-head"><p>ACCOUNT</p><h1>个人中心</h1><span>这里展示当前会话中的真实账户与团队信息。</span></header><div className="settings-grid"><section className="card identity-card"><div className="identity-avatar">{user.displayName.slice(0, 1).toUpperCase()}</div><div><p>当前登录账户</p><h2>{user.displayName}</h2><span>{user.email}</span></div><dl><div><dt>当前团队</dt><dd>{team?.name ?? '未选择团队'}</dd></div><div><dt>协作角色</dt><dd>{team?.role ?? '未分配'}</dd></div></dl></section><section className="card"><p className="card-kicker">ACCOUNT SAFETY</p><h2>会话与安全</h2><p className="card-copy">Access Token 只保存在浏览器内存；刷新凭据只保存在 HttpOnly Cookie。退出后会请求服务端吊销刷新会话。</p><button className="danger-action" type="button" onClick={async () => { await logout(); router.push('/login'); }}>安全登出当前会话</button></section><section className="card future-card"><p className="card-kicker">NEXT CAPABILITY</p><h2>资料与密码编辑</h2><p>昵称、密码和账户安全编辑需要服务端会话吊销与审计能力。本版本不展示不可用的保存按钮，后续将在团队协作功能切片中真实交付。</p></section></div></div>;
}
