'use client';

import { useEffect, useState, type FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import { useApi } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import { useToast } from '@/components/toast/use-toast';

interface Feedback { ok: boolean; text: string }

export default function AccountPage() {
  const { user, team, ready, logout, updateUser } = useAuth();
  const router = useRouter();
  const api = useApi();
  const toast = useToast();

  const [editingProfile, setEditingProfile] = useState(false);
  const [displayName, setDisplayName] = useState('');
  const [profileSaving, setProfileSaving] = useState(false);

  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [pwSaving, setPwSaving] = useState(false);
  const [pwMsg, setPwMsg] = useState<Feedback | null>(null);
  const [pwDone, setPwDone] = useState(false);

  useEffect(() => { if (ready && !user) router.replace('/login?next=/account'); }, [ready, router, user]);

  const saveProfile = async (e: FormEvent) => {
    e.preventDefault();
    const name = displayName.trim();
    if (!name || name.length > 64) { toast.error('昵称长度须为 1-64 个字符'); return; }
    setProfileSaving(true);
    try {
      const updated = await api.put<{ id: number; email: string; displayName: string }>('/users/me/profile', { displayName: name });
      // 服务端返回最新用户对象，同步进会话——头像字母、侧栏昵称立即跟随
      updateUser(updated);
      setEditingProfile(false);
      toast.success('资料已保存');
    } catch (reason) {
      toast.error(reason instanceof Error ? reason.message : String(reason));
    } finally { setProfileSaving(false); }
  };

  const savePassword = async (e: FormEvent) => {
    e.preventDefault();
    if (newPassword.length < 8) { toast.error('新密码至少 8 个字符'); return; }
    if (newPassword !== confirmPassword) { toast.error('两次输入的新密码不一致'); return; }
    if (newPassword === oldPassword) { toast.error('新密码不能与原密码相同'); return; }
    setPwSaving(true);
    try {
      await api.put('/users/me/password', { oldPassword, newPassword });
      // ★ 核心：改密成功后服务端已吊销全部刷新令牌，当前会话必然失效。
      // 必须先提示"需要重新登录"再登出跳转——直接弹回登录页会让用户以为系统出错。
      setPwDone(true);
      setPwMsg({ ok: true, text: '密码已修改，需要重新登录' });
      setTimeout(() => { void logout().then(() => router.push('/login')); }, 1800);
    } catch (reason) {
      toast.error(reason instanceof Error ? reason.message : String(reason));
    } finally { setPwSaving(false); }
  };

  if (!ready || !user) return <p className="loading">正在读取账户…</p>;

  return <div className="settings-page"><header className="settings-head"><p>ACCOUNT</p><h1>个人中心</h1><span>这里展示当前会话中的真实账户与团队信息。</span></header><div className="settings-grid"><section className="card identity-card"><div className="identity-avatar">{user.displayName.slice(0, 1).toUpperCase()}</div><div><p>当前登录账户</p><h2>{user.displayName}</h2><span>{user.email}</span></div><dl><div><dt>当前团队</dt><dd>{team?.name ?? '未选择团队'}</dd></div><div><dt>协作角色</dt><dd>{team?.role ?? '未分配'}</dd></div></dl></section>

    <section className="card"><p className="card-kicker">PROFILE</p><h2>资料编辑</h2><p className="card-copy">昵称会展示在团队列表与协作记录中，邮箱暂不支持自助修改。</p>
      {editingProfile
        ? <form className="account-form" onSubmit={(e) => void saveProfile(e)}><label>昵称<input value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={64} autoFocus required /></label><div className="row"><button className="btn small" type="submit" disabled={profileSaving}>{profileSaving ? '保存中…' : '保存'}</button><button className="btn small secondary" type="button" disabled={profileSaving} onClick={() => setEditingProfile(false)}>取消</button></div></form>
        : <div className="row"><button className="btn small secondary" type="button" onClick={() => { setDisplayName(user.displayName); setEditingProfile(true); }}>编辑资料</button></div>}
    </section>

    <section className="card"><p className="card-kicker">PASSWORD</p><h2>修改密码</h2><p className="card-copy">修改成功后所有已登录会话都会被吊销，需要使用新密码重新登录。</p>
      {pwDone
        ? <div><p className="form-note ok">{pwMsg?.text}</p><button className="btn small" type="button" onClick={() => void logout().then(() => router.push('/login'))}>立即重新登录</button></div>
        : <form className="account-form" onSubmit={(e) => void savePassword(e)}>
          <label>原密码<input type="password" value={oldPassword} onChange={(e) => setOldPassword(e.target.value)} autoComplete="current-password" required /></label>
          <label>新密码（至少 8 位）<input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} autoComplete="new-password" minLength={8} maxLength={72} required /></label>
          <label>确认新密码<input type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} autoComplete="new-password" required /></label>
          <div className="row"><button className="btn small" type="submit" disabled={pwSaving}>{pwSaving ? '提交中…' : '修改密码'}</button></div>
        </form>}
    </section>

    <section className="card"><p className="card-kicker">ACCOUNT SAFETY</p><h2>会话与安全</h2><p className="card-copy">Access Token 只保存在浏览器内存；刷新凭据只保存在 HttpOnly Cookie。退出后会请求服务端吊销刷新会话。</p><button className="danger-action" type="button" onClick={async () => { await logout(); router.push('/login'); }}>安全登出当前会话</button></section>
  </div></div>;
}
