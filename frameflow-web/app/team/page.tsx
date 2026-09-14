'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useApi } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import { EmptyState } from '@/components/empty-state';
import { useToast } from '@/components/toast/use-toast';
import type { TeamInvitation, TeamMember } from '@/lib/types';

const roleCopy: Record<string, string> = { OWNER: '可查看成员；负责团队协作规则与成员管理。', OPERATOR: '可在授权范围内推进项目、批次和操作流程。', REVIEWER: '可参与证据审阅与人工优选判断。', VIEWER: '可查看已授权的项目资料与结果。' };
const assignableRoles: TeamMember['role'][] = ['OPERATOR', 'REVIEWER', 'VIEWER'];

/** 邀请链接由当前站点 origin 拼装——后端只存令牌，不知道前端部署域名。 */
function inviteLinkOf(token: string) {
  return `${window.location.origin}/invite/accept?token=${token}`;
}

export default function TeamPage() {
  const { user, team, ready, accessToken, setSession } = useAuth(); const router = useRouter(); const api = useApi(); const toast = useToast();
  const [members, setMembers] = useState<TeamMember[]>([]); const [error, setError] = useState(''); const [loading, setLoading] = useState(false);
  const [busyUserId, setBusyUserId] = useState<number | null>(null);
  const [invitations, setInvitations] = useState<TeamInvitation[]>([]);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [inviteEmail, setInviteEmail] = useState(''); const [inviteRole, setInviteRole] = useState<'OPERATOR' | 'REVIEWER' | 'VIEWER'>('OPERATOR');
  const [inviteBusy, setInviteBusy] = useState(false);
  const [createdLink, setCreatedLink] = useState('');

  useEffect(() => { if (ready && !user) router.replace('/login?next=/team'); }, [ready, router, user]);
  // ★ 核心：成员列表与邀请列表 API 都只允许 Owner 调用。非 Owner 不发请求而是展示真实权限边界，避免把预期的 403 伪装成页面故障。
  const load = useCallback(async () => {
    if (!team || team.role !== 'OWNER') return;
    setLoading(true);
    try {
      const [memberRows, inviteRows] = await Promise.all([
        api.get<TeamMember[]>(`/teams/${team.id}/members`),
        api.get<TeamInvitation[]>(`/teams/${team.id}/invitations`),
      ]);
      setMembers(memberRows); setInvitations(inviteRows); setError('');
    } catch (reason) { setError(reason instanceof Error ? reason.message : String(reason)); } finally { setLoading(false); }
  }, [api, team]);
  useEffect(() => { void load(); }, [load]);
  if (!ready || !user) return <p className="loading">正在读取团队…</p>;
  const owner = team?.role === 'OWNER';

  const changeRole = async (member: TeamMember, role: TeamMember['role']) => {
    if (!team || role === member.role) return;
    setBusyUserId(member.userId);
    try {
      const updated = await api.put<TeamMember>(`/teams/${team.id}/members/${member.userId}/role`, { role });
      setMembers((current) => current.map((m) => (m.userId === member.userId ? updated : m)));
      toast.success(`已将 ${member.displayName} 的角色调整为 ${role}。`);
    } catch (reason) {
      toast.error(reason instanceof Error ? reason.message : String(reason));
    } finally { setBusyUserId(null); }
  };

  const removeMember = async (member: TeamMember) => {
    if (!team || !window.confirm(`确认将 ${member.displayName}（${member.email}）移出团队？该操作会立即切断其访问权限。`)) return;
    setBusyUserId(member.userId);
    try {
      await api.del(`/teams/${team.id}/members/${member.userId}`);
      setMembers((current) => current.filter((m) => m.userId !== member.userId));
      toast.success(`已将 ${member.displayName} 移出团队。`);
    } catch (reason) {
      toast.error(reason instanceof Error ? reason.message : String(reason));
    } finally { setBusyUserId(null); }
  };

  const transferOwner = async (member: TeamMember) => {
    if (!team || !accessToken || !window.confirm(`把 Owner 转给 ${member.displayName}？你将变成 OPERATOR。`)) return;
    setBusyUserId(member.userId);
    try {
      await api.post(`/teams/${team.id}/owner`, { userId: member.userId });
      const response = await fetch('/api/auth/switch-team', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` },
        body: JSON.stringify({ teamId: team.id }),
      });
      if (response.ok) setSession(await response.json());
      toast.success(`所有权已交给 ${member.displayName}。`);
      router.push('/account');
    } catch (reason) {
      toast.error(reason instanceof Error ? reason.message : String(reason));
    } finally { setBusyUserId(null); }
  };

  const openInvite = () => { setInviteEmail(''); setInviteRole('OPERATOR'); setCreatedLink(''); setInviteOpen(true); };

  const submitInvite = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!team) return;
    setInviteBusy(true);
    try {
      const created = await api.post<TeamInvitation>(`/teams/${team.id}/invitations`, { email: inviteEmail, role: inviteRole });
      // 明文令牌只在这一次响应里出现；列表接口不会再返回，所以必须当场生成链接。
      if (!created.token) throw new Error('服务器未返回邀请令牌');
      setCreatedLink(inviteLinkOf(created.token));
      setInvitations((current) => [created, ...current]);
      toast.success('邀请已创建，请把链接转发给受邀人。');
    } catch (reason) {
      toast.error(reason instanceof Error ? reason.message : String(reason));
    } finally { setInviteBusy(false); }
  };

  const copyLink = async (link: string) => {
    try {
      await navigator.clipboard.writeText(link);
      toast.success('邀请链接已复制，请手工转发给受邀人。');
    } catch {
      window.prompt('复制以下邀请链接', link);
    }
  };

  const revokeInvite = async (invite: TeamInvitation) => {
    if (!team || !window.confirm(`撤销发给 ${invite.email} 的邀请？对方将无法再用该链接入团。`)) return;
    try {
      await api.del(`/teams/${team.id}/invitations/${invite.id}`);
      setInvitations((current) => current.map((row) => (
        row.id === invite.id ? { ...row, revokedAt: new Date().toISOString() } : row
      )));
      toast.success(`已撤销发给 ${invite.email} 的邀请。`);
    } catch (reason) {
      toast.error(reason instanceof Error ? reason.message : String(reason));
    }
  };

  return <div className="settings-page"><header className="settings-head"><p>TEAM SPACE</p><h1>{team?.name ?? '当前团队'}</h1><span>当前团队上下文来自已签发的会话凭据；本页只展示已经由后端支持的数据和权限。</span></header><div className="settings-grid"><section className="card team-summary"><p className="card-kicker">YOUR ROLE</p><h2>{team?.role ?? '未分配角色'}</h2><p>{roleCopy[team?.role ?? ''] ?? '当前角色尚未配置说明。'}</p><div className="team-limits"><span>多团队切换将在后续协作功能中以完整权限与审计机制交付。</span></div></section><section className="card member-card"><div className="card-head"><div><p className="card-kicker">MEMBERS</p><h2>{owner ? '团队成员' : '成员名单权限'}</h2></div>{owner && <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}><b>{members.length} 位</b><button className="btn small" type="button" onClick={openInvite}>邀请成员</button></div>}</div>{!owner ? <div className="permission-note"><strong>成员名单由团队 Owner 管理</strong><p>你当前拥有 {team?.role} 角色。为保护团队成员信息，本角色不会请求 Owner 专用成员列表接口。</p></div> : loading ? <p className="loading">正在读取成员列表…</p> : error ? <div className="error"><p>{error}</p><button className="mini-link" type="button" onClick={() => void load()}>重新加载</button></div> : <>{members.length === 0 ? <EmptyState title="团队还没有成员" description="邀请同事加入，一起评审候选素材、分工推进批次。" action={{ label: '邀请成员', onClick: openInvite }} /> : <div className="member-list">{members.map((member) => {
    const manageable = member.role !== 'OWNER'; const busy = busyUserId === member.userId;
    return <div key={member.userId} className="member-row"><span>{member.displayName.slice(0, 1).toUpperCase()}</span><div><strong>{member.displayName}</strong><small>{member.email}</small></div>{manageable ? <div className="member-actions"><select aria-label={`调整 ${member.displayName} 的角色`} value={member.role} disabled={busy} onChange={(e) => void changeRole(member, e.target.value as TeamMember['role'])}>{assignableRoles.map((role) => <option key={role} value={role}>{role}</option>)}</select><button className="btn small secondary" type="button" disabled={busy} onClick={() => void transferOwner(member)}>转让 Owner</button><button className="danger-action" type="button" disabled={busy} onClick={() => void removeMember(member)}>移除</button></div> : <b>{member.role}</b>}</div>;
  })}</div>}</>}</section>
    {owner && <section className="card member-card"><div className="card-head"><div><p className="card-kicker">INVITATIONS</p><h2>邀请记录</h2></div>{invitations.length > 0 && <b>{invitations.filter((i) => !i.acceptedAt && !i.revokedAt).length} 待接受</b>}</div>
      {loading ? <p className="loading">正在读取邀请记录…</p> : invitations.length === 0 ? <EmptyState title="还没有发出过邀请" description="生成一条 7 天有效的一次性邀请链接，手工转发给对方即可加入团队。" action={{ label: '邀请成员', onClick: openInvite }} /> : <div className="invite-list">{invitations.map((invite) => {
        const revoked = Boolean(invite.revokedAt);
        const pending = !invite.acceptedAt && !revoked;
        const expired = pending && new Date(invite.expiresAt).getTime() < Date.now();
        const status = invite.acceptedAt ? '已接受' : revoked ? '已撤销' : expired ? '已过期' : '待接受';
        return <div key={invite.id} className="invite-row"><strong>{invite.email}</strong><b className={pending && !expired ? 'pending' : 'done'}>{status}</b><small>{invite.role} · {expired ? '过期于' : '有效期至'} {new Date(invite.expiresAt).toLocaleDateString()}</small>{pending && !expired && invite.token ? <small><button className="mini-link" type="button" onClick={() => void copyLink(inviteLinkOf(invite.token!))}>复制邀请链接</button></small> : pending && !expired ? <small className="muted">链接仅在创建时显示一次</small> : null}{pending && !expired && <small><button className="mini-link" type="button" onClick={() => void revokeInvite(invite)}>撤销</button></small>}</div>;
      })}</div>}
    </section>}
  </div>
    {inviteOpen && <div className="invite-overlay" role="dialog" aria-modal="true" aria-label="邀请成员" onClick={(e) => { if (e.target === e.currentTarget) setInviteOpen(false); }}><div className="invite-modal"><h2>邀请成员</h2><p>生成一条 7 天有效的一次性邀请链接，手工转发给对方。邮件发送将在后续版本提供。</p>
      {createdLink ? <div className="invite-link-box"><p>邀请已创建。把这条链接发给 {inviteEmail}：</p><code>{createdLink}</code><div className="modal-actions"><button className="btn secondary small" type="button" onClick={() => setInviteOpen(false)}>完成</button><button className="btn small" type="button" onClick={() => void copyLink(createdLink)}>复制链接</button></div></div>
        : <form onSubmit={(e) => void submitInvite(e)}><label>邮箱<input type="email" required autoComplete="off" value={inviteEmail} placeholder="name@example.com" onChange={(e) => setInviteEmail(e.target.value)} /></label><label>角色<select value={inviteRole} onChange={(e) => setInviteRole(e.target.value as typeof inviteRole)}>{assignableRoles.map((role) => <option key={role} value={role}>{role}</option>)}</select></label><div className="modal-actions"><button className="btn secondary small" type="button" onClick={() => setInviteOpen(false)}>取消</button><button className="btn small" type="submit" disabled={inviteBusy}>{inviteBusy ? '正在创建…' : '生成邀请链接'}</button></div></form>}
    </div></div>}
  </div>;
}
