'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';
import { useApi } from '@/lib/api';
import { EmptyState } from '@/components/empty-state';
import type { PageOf, Project } from '@/lib/types';

export default function WorkspaceDashboard() {
  const { user, ready, team } = useAuth(); const router = useRouter(); const api = useApi();
  const nameInputRef = useRef<HTMLInputElement>(null);
  const [projects, setProjects] = useState<Project[]>([]); const [name, setName] = useState(''); const [description, setDescription] = useState(''); const [error, setError] = useState('');
  const [page, setPage] = useState(0); const [total, setTotal] = useState(0); const pageSize = 20;
  // ★ 核心：完成 refresh 前不跳登录，避免有有效 HttpOnly refresh cookie 的用户在会话恢复间隙被误判为未登录。
  useEffect(() => { if (ready && !user) router.replace('/login?next=/workspace'); }, [ready, user, router]);
  const load = useCallback(async () => { if (!user) return; try { const result = await api.get<PageOf<Project>>(`/projects?page=${page}&size=${pageSize}`); setProjects(result.items); setTotal(result.total); } catch (err) { setError(err instanceof Error ? err.message : String(err)); } }, [api, user, page]);
  useEffect(() => { void load(); }, [load]);
  if (!ready || !user) return <p className="loading">正在打开工作台…</p>;
  return <><header className="workspace-page-head"><p>当前团队 / {team?.name}</p><h1>视频质检工作台</h1><span>建立项目、保存 Brief 和质量标准，再将每一次优选留成证据。</span></header><div className="workspace-grid"><section className="card"><div className="card-head"><div><p>PROJECTS</p><h2>当前项目</h2></div><b>{total} 个</b></div>{error && <div className="error">{error}</div>}{total === 0 ? <EmptyState title="还没有项目" description="建立第一个项目，保存 Brief 与质量标准，再把每一次优选留成证据。" action={{ label: '创建第一个项目', onClick: () => nameInputRef.current?.focus() }} /> : <><table><thead><tr><th>项目</th><th>状态</th><th>Brief</th><th /></tr></thead><tbody>{projects.map((project) => <tr key={project.id}><td><strong>{project.name}</strong><small>{project.description || '尚未添加说明'}</small></td><td><span className="badge ok">{project.status}</span></td><td>{project.currentBriefId ? `#${project.currentBriefId}` : '未发布'}</td><td><Link className="mini-link" href={`/projects/${project.id}`}>打开 ↗</Link></td></tr>)}</tbody></table>{total > pageSize && <nav className="row" style={{ marginTop: 12 }}><button className="btn secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>上一页</button><span>第 {page + 1} / {Math.ceil(total / pageSize)} 页</span><button className="btn secondary" disabled={(page + 1) * pageSize >= total} onClick={() => setPage((p) => p + 1)}>下一页</button></nav>}</>}</section><aside className="card project-create"><p>NEW PROJECT</p><h2>开始一条新的选择流程</h2><form onSubmit={async (event) => { event.preventDefault(); setError(''); try { await api.post('/projects', { name, description: description || null }); setName(''); setDescription(''); await load(); } catch (err) { setError(err instanceof Error ? err.message : String(err)); } }}><label>项目名称<input ref={nameInputRef} value={name} required placeholder="例如：秋季产品广告素材" onChange={(event) => setName(event.target.value)} /></label><label>项目说明 <em>可选</em><textarea value={description} placeholder="说明目标、投放场景或团队约定" onChange={(event) => setDescription(event.target.value)} /></label><button className="btn">创建项目 ↗</button></form></aside></div></>;
}
