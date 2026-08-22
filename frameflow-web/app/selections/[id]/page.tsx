'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';
import { useApi } from '@/lib/api';
import type { Candidate, PageOf, RankingEntry, SelectionDetail } from '@/lib/types';

export default function SelectionPage() {
  const { id } = useParams<{ id: string }>();
  const api = useApi();
  const { accessToken } = useAuth();
  const [selection, setSelection] = useState<SelectionDetail | null>(null);
  const [ranking, setRanking] = useState<Record<number, RankingEntry>>({});
  const [candidates, setCandidates] = useState<Record<number, Candidate>>({});
  const [message, setMessage] = useState('');

  const load = useCallback(async () => {
    try {
      const detail = await api.get<SelectionDetail>(`/selections/${id}`);
      setSelection(detail);
      if (detail) {
        const latest = await api.get<{ entries: RankingEntry[] }>(`/batches/${detail.batchId}/ranking/latest`);
        const map: Record<number, RankingEntry> = {};
        latest.entries.forEach((e) => (map[e.candidateId] = e));
        setRanking(map);
        const page = await api.get<PageOf<Candidate>>(`/batches/${detail.batchId}/candidates?size=200`);
        const cmap: Record<number, Candidate> = {};
        page.items.forEach((c) => (cmap[c.id] = c));
        setCandidates(cmap);
      }
    } catch (err) {
      setMessage(String(err instanceof Error ? err.message : err));
    }
  }, [api, id]);

  useEffect(() => {
    load();
  }, [load]);

  if (!selection) return <p className="loading">加载中…</p>;
  const locked = selection.status === 'LOCKED';

  async function adjust(candidateId: number, action: 'INCLUDE' | 'EXCLUDE') {
    setMessage('');
    try {
      await api.post(`/selections/${id}/items`, { candidateId, action });
      await load();
    } catch (err) {
      setMessage(String(err instanceof Error ? err.message : err));
    }
  }

  async function downloadExport(format: 'csv' | 'json') {
    if (!accessToken) return;
    const resp = await fetch(`/api/gw/selections/${id}/export?format=${format}`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    if (!resp.ok) {
      setMessage(`导出失败 HTTP ${resp.status}`);
      return;
    }
    const blob = await resp.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `selection-${id}.${format}`;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <>
      <div className="card">
        <h2>
          优选集 #{selection.id}{' '}
          <span className={`badge ${locked ? 'ok' : 'warn'}`}>{selection.status}</span>{' '}
          <span className="muted">Top-{selection.topK} · 批次 #{selection.batchId} · 快照 #{selection.snapshotId}</span>
        </h2>
        <div className="row">
          {!locked && (
            <button
              className="btn danger"
              onClick={async () => {
                if (!confirm('锁定后不可再修改，确认？')) return;
                try {
                  await api.post(`/selections/${id}/lock`);
                  await load();
                } catch (err) {
                  setMessage(String(err instanceof Error ? err.message : err));
                }
              }}
            >
              锁定
            </button>
          )}
          {locked && (
            <>
              {/* <a> 导航带不上 Authorization 头（cookie 只覆盖 /api/auth），
                  必须用 fetch + blob 触发下载 */}
              <button
                className="btn secondary"
                onClick={async () => downloadExport('csv')}
              >
                导出 CSV
              </button>
              <button className="btn secondary" onClick={() => downloadExport('json')}>
                导出 JSON
              </button>
            </>
          )}
          <Link href={`/batches/${selection.batchId}`} className="muted">
            ← 返回批次
          </Link>
        </div>
        {message && <div className="notice bad">{message}</div>}
        <p className="muted" style={{ marginTop: 8 }}>
          机器标记与人工调整并存——最终交付 = 机器 Top-K 剔除人工 EXCLUDE 后 + 人工 INCLUDE。
        </p>
      </div>

      <div className="card">
        <h2>条目（{selection.items.length}）</h2>
        <table>
          <thead>
            <tr>
              <th>排名</th>
              <th>候选</th>
              <th>分数</th>
              <th>簇</th>
              <th>机器</th>
              <th>人工</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {selection.items
              .slice()
              .sort((a, b) => (ranking[a.candidateId]?.rankNo ?? 999) - (ranking[b.candidateId]?.rankNo ?? 999))
              .map((item) => {
                const entry = ranking[item.candidateId];
                const cand = candidates[item.candidateId];
                return (
                  <tr key={item.candidateId} style={{ opacity: item.humanAction === 'EXCLUDE' ? 0.45 : 1 }}>
                    <td className="mono">{entry ? `#${entry.rankNo}` : '—'}</td>
                    <td>
                      <Link href={`/candidates/${item.candidateId}`}>
                        #{item.candidateId} {cand?.fileName ?? ''}
                      </Link>
                    </td>
                    <td className="mono">{entry?.score ?? '—'}</td>
                    <td className="mono">{entry ? `C${entry.clusterId}` : '—'}</td>
                    <td>{item.machinePick ? <span className="badge ok">TOP-K</span> : <span className="badge">—</span>}</td>
                    <td>
                      {item.humanAction ? (
                        <span className={`badge ${item.humanAction === 'INCLUDE' ? 'ok' : 'bad'}`}>{item.humanAction}</span>
                      ) : (
                        ''
                      )}
                    </td>
                    <td>
                      {!locked && (
                        <span className="row" style={{ gap: 4 }}>
                          <button className="btn small" onClick={() => adjust(item.candidateId, 'INCLUDE')}>
                            收录
                          </button>
                          <button className="btn small danger" onClick={() => adjust(item.candidateId, 'EXCLUDE')}>
                            剔除
                          </button>
                        </span>
                      )}
                    </td>
                  </tr>
                );
              })}
          </tbody>
        </table>
      </div>
    </>
  );
}
