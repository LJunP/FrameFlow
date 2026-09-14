'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';
import { useApi } from '@/lib/api';
import { EmptyState } from '@/components/empty-state';
import { useToast } from '@/components/toast/use-toast';
import type { Candidate, PageOf, RankingEntry, SelectionDetail } from '@/lib/types';

/**
 * ★ 核心：从 Content-Disposition 解析后端给的文件名。只认 filename="..." 形式；
 * 解析不出（头缺失/跨域被隐藏）返回 null，由调用方回退到本地命名，绝不抛错。
 */
function filenameFromDisposition(header: string | null): string | null {
  if (!header) return null;
  const match = /filename\*?=(?:UTF-8''|")?([^";]+)/i.exec(header);
  if (!match) return null;
  const raw = match[1].trim().replace(/"$/, '');
  try {
    return decodeURIComponent(raw);
  } catch {
    return raw;
  }
}

export default function SelectionPage() {
  const { id } = useParams<{ id: string }>();
  const api = useApi();
  const { accessToken } = useAuth();
  const toast = useToast();
  const [selection, setSelection] = useState<SelectionDetail | null>(null);
  const [ranking, setRanking] = useState<Record<number, RankingEntry>>({});
  const [candidates, setCandidates] = useState<Record<number, Candidate>>({});
  const [message, setMessage] = useState('');
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [batchRunning, setBatchRunning] = useState(false);

  const load = useCallback(async () => {
    try {
      const detail = await api.get<SelectionDetail>(`/selections/${id}`);
      setSelection(detail);
      if (detail) {
        const latest = await api.get<{ entries: RankingEntry[] }>(`/batches/${detail.batchId}/ranking/latest`);
        const map: Record<number, RankingEntry> = {};
        latest.entries.forEach((e) => (map[e.candidateId] = e));
        setRanking(map);
        const page = await api.get<PageOf<Candidate>>(`/batches/${detail.batchId}/candidates?size=300`);
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

  if (!selection) {
    return message
      ? <div className="card"><div className="notice bad">{message}</div><p style={{ marginTop: 12 }}><Link href="/workspace">← 返回工作台</Link></p></div>
      : <p className="loading">加载中…</p>;
  }
  const locked = selection.status === 'LOCKED';
  const [extraCandidateId, setExtraCandidateId] = useState('');
  const [extraNote, setExtraNote] = useState('');

  async function adjust(candidateId: number, action: 'INCLUDE' | 'EXCLUDE', note?: string) {
    setMessage('');
    try {
      await api.post(`/selections/${id}/items`, { candidateId, action, note: note || undefined });
      await load();
    } catch (err) {
      setMessage(String(err instanceof Error ? err.message : err));
    }
  }

  function toggleSelect(candidateId: number) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(candidateId)) next.delete(candidateId);
      else next.add(candidateId);
      return next;
    });
  }

  const selectAll = () => {
    setSelected(new Set(selection.items.map((i) => i.candidateId)));
  };

  const invertSelection = () => {
    setSelected((prev) => {
      const next = new Set<number>();
      selection.items.forEach((i) => {
        if (!prev.has(i.candidateId)) next.add(i.candidateId);
      });
      return next;
    });
  };

  // ★ 核心：批量收录/剔除——后端只有单条 items 接口，这里逐条顺序提交，
  // 避免并发打爆连接；单条失败不中断，最后汇总失败数并刷新、清空勾选。
  // 若中断失败不重试，已成功的条目状态以刷新后的服务端数据为准。
  async function batchAdjust(action: 'INCLUDE' | 'EXCLUDE') {
    if (selected.size === 0 || batchRunning) return;
    setBatchRunning(true);
    setMessage('');
    let failed = 0;
    for (const candidateId of selected) {
      try {
        await api.post(`/selections/${id}/items`, { candidateId, action });
      } catch {
        failed += 1;
      }
    }
    setSelected(new Set());
    setBatchRunning(false);
    if (failed > 0) {
      // ★ 核心：批量操作是可逆的，但用户看不到"哪几条成功"，必须给明确反馈；
      // 旧实现只在失败时 setMessage，成功时界面毫无变化，容易被误认为没生效。
      toast.error(`批量${action === 'INCLUDE' ? '收录' : '剔除'}：${failed} 条失败，其余已生效`);
    } else {
      toast.success(`批量${action === 'INCLUDE' ? '收录' : '剔除'}完成`);
    }
    await load();
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
    // ★ 核心：优先沿用后端 Content-Disposition 里的文件名（含优选集 id 与日期），
    // 前端硬编码 selection-<id>.<ext> 会让同一天多次导出互相覆盖成 "(1)(2)" 副本。
    a.download = filenameFromDisposition(resp.headers.get('content-disposition'))
      ?? `selection-${id}.${format}`;
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
        {!locked && (
          <form className="row" style={{ marginTop: 12 }} onSubmit={(e) => { e.preventDefault(); const cid = Number(extraCandidateId); if (!cid) return; void adjust(cid, 'INCLUDE', extraNote).then(() => { setExtraCandidateId(''); setExtraNote(''); }); }}>
            <input type="number" min={1} placeholder="候选 ID" value={extraCandidateId} onChange={(e) => setExtraCandidateId(e.target.value)} />
            <input placeholder="备注（可选）" value={extraNote} onChange={(e) => setExtraNote(e.target.value)} />
            <button className="btn secondary" type="submit">从排名外纳入</button>
          </form>
        )}
        {message && <div className="notice bad">{message}</div>}
        <p className="muted" style={{ marginTop: 8 }}>
          机器标记与人工调整并存——最终交付 = 机器 Top-K 剔除人工 EXCLUDE 后 + 人工 INCLUDE。
        </p>
      </div>

      <div className="card">
        <h2>条目（{selection.items.length}）</h2>
        {selection.items.length === 0 ? (
          <EmptyState
            title="优选集还没有条目"
            description="回到批次页确认候选已上传并完成分析，然后重新生成排名并创建优选集。"
            action={{ label: '返回批次', href: `/batches/${selection.batchId}` }}
          />
        ) : (
          <>
        {!locked && (
          <div className="row batch-bar">
            <button className="btn small secondary" onClick={selectAll}>
              全选
            </button>
            <button className="btn small secondary" onClick={invertSelection}>
              反选
            </button>
            <span className="muted">已选 {selected.size} 项</span>
            <button
              className="btn small"
              disabled={selected.size === 0 || batchRunning}
              onClick={() => batchAdjust('INCLUDE')}
            >
              批量收录
            </button>
            <button
              className="btn small danger"
              disabled={selected.size === 0 || batchRunning}
              onClick={() => batchAdjust('EXCLUDE')}
            >
              批量剔除
            </button>
          </div>
        )}
        <table>
          <thead>
            <tr>
              {!locked && (
                <th className="sel-col">
                  <input
                    type="checkbox"
                    aria-label="全选"
                    checked={selection.items.length > 0 && selected.size === selection.items.length}
                    ref={(el) => {
                      if (el) el.indeterminate = selected.size > 0 && selected.size < selection.items.length;
                    }}
                    onChange={() => (selected.size === selection.items.length ? setSelected(new Set()) : selectAll())}
                  />
                </th>
              )}
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
                const rowClass = [
                  item.humanAction === 'EXCLUDE' ? 'is-excluded' : '',
                  selected.has(item.candidateId) ? 'is-selected' : '',
                ]
                  .filter(Boolean)
                  .join(' ');
                return (
                  <tr key={item.candidateId} className={rowClass || undefined}>
                    {!locked && (
                      <td className="sel-col">
                        <input
                          type="checkbox"
                          aria-label={`选择候选 #${item.candidateId}`}
                          checked={selected.has(item.candidateId)}
                          onChange={() => toggleSelect(item.candidateId)}
                        />
                      </td>
                    )}
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
          </>
        )}
      </div>
    </>
  );
}
