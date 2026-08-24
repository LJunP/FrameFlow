'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useApi } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import type { TeamMember } from '@/lib/types';

const roleCopy: Record<string, string> = { OWNER: '可查看成员；负责团队协作规则与成员管理。', OPERATOR: '可在授权范围内推进项目、批次和操作流程。', REVIEWER: '可参与证据审阅与人工优选判断。', VIEWER: '可查看已授权的项目资料与结果。' };

export default function TeamPage() {
  const { user, team, ready } = useAuth(); const router = useRouter(); const api = useApi(); const [members, setMembers] = useState<TeamMember[]>([]); const [error, setError] = useState(''); const [loading, setLoading] = useState(false);
  useEffect(() => { if (ready && !user) router.replace('/login?next=/team'); }, [ready, router, user]);
  // ★ 核心：成员列表 API 只允许 Owner 调用。非 Owner 不发请求而是展示真实权限边界，避免把预期的 403 伪装成页面故障。
  const load = useCallback(async () => { if (!team || team.role !== 'OWNER') return; setLoading(true); try { setMembers(await api.get<TeamMember[]>(`/teams/${team.id}/members`)); setError(''); } catch (reason) { setError(reason instanceof Error ? reason.message : String(reason)); } finally { setLoading(false); } }, [api, team]);
  useEffect(() => { void load(); }, [load]);
  if (!ready || !user) return <p className="loading">正在读取团队…</p>;
  const owner = team?.role === 'OWNER';
  return <div className="settings-page"><header className="settings-head"><p>TEAM SPACE</p><h1>{team?.name ?? '当前团队'}</h1><span>当前团队上下文来自已签发的会话凭据；本页只展示已经由后端支持的数据和权限。</span></header><div className="settings-grid"><section className="card team-summary"><p className="card-kicker">YOUR ROLE</p><h2>{team?.role ?? '未分配角色'}</h2><p>{roleCopy[team?.role ?? ''] ?? '当前角色尚未配置说明。'}</p><div className="team-limits"><span>成员邀请、角色调整、移除成员和多团队切换将在后续协作功能中以完整权限与审计机制交付。</span></div></section><section className="card member-card"><div className="card-head"><div><p className="card-kicker">MEMBERS</p><h2>{owner ? '团队成员' : '成员名单权限'}</h2></div>{owner && <b>{members.length} 位</b>}</div>{!owner ? <div className="permission-note"><strong>成员名单由团队 Owner 管理</strong><p>你当前拥有 {team?.role} 角色。为保护团队成员信息，本角色不会请求 Owner 专用成员列表接口。</p></div> : loading ? <p className="loading">正在读取成员列表…</p> : error ? <div className="error"><p>{error}</p><button className="mini-link" type="button" onClick={() => void load()}>重新加载</button></div> : <div className="member-list">{members.map((member) => <div key={member.userId} className="member-row"><span>{member.displayName.slice(0, 1).toUpperCase()}</span><div><strong>{member.displayName}</strong><small>{member.email}</small></div><b>{member.role}</b></div>)}</div>}</section></div></div>;
}
