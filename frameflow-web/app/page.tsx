'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';
import { useApi } from '@/lib/api';
import type { PageOf, Project } from '@/lib/types';

export default function Dashboard() {
  const { user, ready } = useAuth();
  const router = useRouter();
  const api = useApi();
  const [projects, setProjects] = useState<Project[]>([]);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (ready && !user) router.replace('/login');
  }, [ready, user, router]);

  const load = useCallback(async () => {
    if (!user) return;
    try {
      const page = await api.get<PageOf<Project>>('/projects?size=50');
      setProjects(page.items);
    } catch (err) {
      setError(String(err instanceof Error ? err.message : err));
    }
  }, [user, api]);

  useEffect(() => {
    load();
  }, [load]);

  if (!ready || !user) return <p className="muted">加载中…</p>;

  return (
    <>
      <div className="card">
        <h2>创建项目</h2>
        <form
          className="row"
          onSubmit={async (e) => {
            e.preventDefault();
            setError('');
            try {
              await api.post('/projects', { name, description: description || null });
              setName('');
              setDescription('');
              await load();
            } catch (err) {
              setError(String(err instanceof Error ? err.message : err));
            }
          }}
        >
          <input placeholder="项目名" value={name} required onChange={(e) => setName(e.target.value)} />
          <input placeholder="说明（可选）" value={description} onChange={(e) => setDescription(e.target.value)} />
          <button className="btn">创建</button>
        </form>
        {error && <div className="error">{error}</div>}
      </div>

      <div className="card">
        <h2>我的项目（{projects.length}）</h2>
        {projects.length === 0 ? (
          <p className="muted">还没有项目——创建第一个，然后发布 Brief、配置质检标准。</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>名称</th>
                <th>状态</th>
                <th>当前 Brief</th>
                <th>版本锁</th>
              </tr>
            </thead>
            <tbody>
              {projects.map((p) => (
                <tr key={p.id}>
                  <td>
                    <Link href={`/projects/${p.id}`}>{p.name}</Link>
                  </td>
                  <td>
                    <span className={`badge ${p.status === 'ACTIVE' ? 'ok' : ''}`}>{p.status}</span>
                  </td>
                  <td className="muted">{p.currentBriefId ? `#${p.currentBriefId}` : '未发布'}</td>
                  <td className="mono">v{p.lockVersion}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
