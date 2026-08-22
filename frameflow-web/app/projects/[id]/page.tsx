'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useApi } from '@/lib/api';
import type { Brief, Profile, Project } from '@/lib/types';

export default function ProjectPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const api = useApi();
  const [project, setProject] = useState<Project | null>(null);
  const [briefs, setBriefs] = useState<Brief[]>([]);
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [briefText, setBriefText] = useState('');
  const [profileName, setProfileName] = useState('');
  const [profileSpec, setProfileSpec] = useState('{"dimensions":{"duration":{"min":5,"max":30}},"weights":{"duration":10}}');
  const [batchProfileId, setBatchProfileId] = useState<number | ''>('');
  const [capacity, setCapacity] = useState(50);
  const [message, setMessage] = useState('');

  const load = useCallback(async () => {
    try {
      setProject(await api.get<Project>(`/projects/${id}`));
      setBriefs(await api.get<Brief[]>(`/projects/${id}/briefs`));
      setProfiles(await api.get<Profile[]>('/quality-profiles'));
    } catch (err) {
      setMessage(String(err instanceof Error ? err.message : err));
    }
  }, [api, id]);

  useEffect(() => {
    load();
  }, [load]);

  if (!project) return <p className="muted">加载中… {message}</p>;

  return (
    <>
      <div className="card">
        <h2>
          {project.name} <span className={`badge ${project.status === 'ACTIVE' ? 'ok' : ''}`}>{project.status}</span>
        </h2>
        <p className="muted">{project.description ?? '无说明'}</p>
      </div>

      <div className="card">
        <h2>Brief 快照（不可变，修正 = 新版本）</h2>
        <form
          className="row"
          onSubmit={async (e) => {
            e.preventDefault();
            setMessage('');
            try {
              await api.post(`/projects/${id}/briefs`, { content: briefText });
              setBriefText('');
              await load();
            } catch (err) {
              setMessage(String(err instanceof Error ? err.message : err));
            }
          }}
        >
          <input
            style={{ flex: 1, minWidth: 260 }}
            placeholder="创作要求：主体、风格、禁项（如：不得出现水印）"
            value={briefText}
            required
            onChange={(e) => setBriefText(e.target.value)}
          />
          <button className="btn">发布新快照</button>
        </form>
        {briefs.length === 0 ? (
          <p className="muted">还没有 Brief——批次必须绑定一条 Brief 快照。</p>
        ) : (
          <table style={{ marginTop: 10 }}>
            <thead>
              <tr>
                <th>#</th>
                <th>内容</th>
                <th>发布时间</th>
                <th>当前</th>
              </tr>
            </thead>
            <tbody>
              {briefs.map((b) => (
                <tr key={b.id}>
                  <td className="mono">{b.id}</td>
                  <td>{b.content}</td>
                  <td className="muted">{b.createdAt.slice(0, 19).replace('T', ' ')}</td>
                  <td>{project.currentBriefId === b.id ? <span className="badge ok">当前</span> : ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="card">
        <h2>创建质检标准（Profile，创建即发布 v1，之后只可追加版本）</h2>
        <form
          className="row"
          onSubmit={async (e) => {
            e.preventDefault();
            setMessage('');
            try {
              await api.post('/quality-profiles', { name: profileName, spec: profileSpec });
              setProfileName('');
              await load();
            } catch (err) {
              setMessage(String(err instanceof Error ? err.message : err));
            }
          }}
        >
          <input placeholder="标准名（如：电商竖版）" value={profileName} required onChange={(e) => setProfileName(e.target.value)} />
          <input
            style={{ flex: 1, minWidth: 240 }}
            className="mono"
            placeholder='spec JSON，如 {"dimensions":{"duration":{"min":5,"max":30}}}'
            value={profileSpec}
            onChange={(e) => setProfileSpec(e.target.value)}
          />
          <button className="btn">创建</button>
        </form>
        <p className="muted" style={{ marginTop: 6 }}>
          已有 {profiles.length} 个标准：
          {profiles.map((p) => ` ${p.name}(v${p.latestVersion ?? '—'})`)}
        </p>
      </div>

      <div className="card">
        <h2>创建批次（绑定当前 Brief + Profile 版本）</h2>
        <form
          className="row"
          onSubmit={async (e) => {
            e.preventDefault();
            setMessage('');
            try {
              const created = await api.post<{ id: number }>('/batches', {
                projectId: Number(id),
                profileId: Number(batchProfileId),
                capacity,
              });
              router.push(`/batches/${created.id}`);
            } catch (err) {
              setMessage(String(err instanceof Error ? err.message : err));
            }
          }}
        >
          <select required value={batchProfileId} onChange={(e) => setBatchProfileId(Number(e.target.value))}>
            <option value="" disabled>
              选择质检标准（Profile）
            </option>
            {profiles.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}（最新 v{p.latestVersion ?? '—'}）
              </option>
            ))}
          </select>
          <input
            type="number"
            min={1}
            max={300}
            style={{ width: 110 }}
            value={capacity}
            onChange={(e) => setCapacity(Number(e.target.value))}
            title="容量"
          />
          <button className="btn" disabled={!project.currentBriefId || !batchProfileId}>
            创建批次
          </button>
          {!project.currentBriefId && <span className="muted">先发布 Brief</span>}
        </form>
        {message && <div className="error">{message}</div>}
      </div>
    </>
  );
}
