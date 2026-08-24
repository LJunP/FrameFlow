'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useApi, putWithProgress } from '@/lib/api';
import type { Batch, Candidate, PageOf, RegisterCandidateResponse, SelectionSummary } from '@/lib/types';

const STATUS_CLASS: Record<string, string> = {
  UPLOADED: 'ok', ANALYZED: 'ok', PENDING_UPLOAD: '', ANALYZING: 'warn',
  REVIEW_REQUIRED: 'warn', AUTO_REJECT: 'bad', ANALYSIS_ERROR: 'system', INVALID: 'bad',
};

function statusLabel(status: string) {
  return status === 'ANALYSIS_ERROR' ? '系统异常 · ANALYSIS_ERROR' : status;
}

export default function BatchPage() {
  const { id } = useParams<{ id: string }>();
  const api = useApi();
  const [batch, setBatch] = useState<Batch | null>(null);
  const [candidates, setCandidates] = useState<Candidate[]>([]);
  const [selections, setSelections] = useState<SelectionSummary[]>([]);
  const [message, setMessage] = useState('');
  const [uploadPct, setUploadPct] = useState<number | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const [topK, setTopK] = useState(5);

  const load = useCallback(async () => {
    try {
      setBatch(await api.get<Batch>(`/batches/${id}`));
      const page = await api.get<PageOf<Candidate>>(`/batches/${id}/candidates?size=200`);
      setCandidates(page.items);
      setSelections(await api.get<SelectionSummary[]>(`/batches/${id}/selections`));
    } catch (err) {
      setMessage(String(err instanceof Error ? err.message : err));
    }
  }, [api, id]);

  useEffect(() => {
    load();
  }, [load]);

  // 有进行中任务时轮询（批次进度是缓存优先端点，轮询很便宜）
  useEffect(() => {
    if (!batch) return;
    const busy = Object.entries(batch.candidateCounts).some(
      ([k, v]) => (k === 'PENDING_UPLOAD' || k === 'ANALYZING') && v > 0,
    );
    if (!busy) return;
    const timer = setInterval(load, 3000);
    return () => clearInterval(timer);
  }, [batch, load]);

  if (!batch) {
    return message
      ? <div className="card"><div className="notice bad">{message}</div><p style={{ marginTop: 12 }}><Link href="/workspace">← 返回工作台</Link></p></div>
      : <p className="loading">加载中…</p>;
  }

  async function upload() {
    const file = fileRef.current?.files?.[0];
    if (!file) return;
    setMessage('');
    try {
      const reg = await api.post<RegisterCandidateResponse>(`/batches/${id}/candidates`, {
        fileName: file.name,
        contentType: file.type || 'video/mp4',
        sizeBytes: file.size,
      });
      if (!reg.uploadUrl) throw new Error('该文件需要分片上传，请在桌面端使用小文件体验');
      setUploadPct(0);
      await putWithProgress(reg.uploadUrl, file, setUploadPct);
      const done = await api.post<{ candidateId: number; status: string; probeError: string | null }>(
        `/candidates/${reg.candidateId}/complete`,
        {},
      );
      setMessage(
        done.status === 'UPLOADED'
          ? '✓ 上传成功；关闭批次后可触发分析'
          : `✗ 文件被判无效：${done.probeError}`,
      );
      if (fileRef.current) fileRef.current.value = '';
      await load();
    } catch (err) {
      setMessage(String(err instanceof Error ? err.message : err));
    } finally {
      setUploadPct(null);
    }
  }

  async function act(label: string, fn: () => Promise<unknown>) {
    setMessage(`${label}…`);
    try {
      await fn();
      setMessage(`${label} ✓`);
      await load();
    } catch (err) {
      setMessage(`${label} ✗ ${String(err instanceof Error ? err.message : err)}`);
    }
  }

  return (
    <>
      <div className="card">
        <h2>
          批次 #{batch.id}{' '}
          <span className={`badge ${batch.status === 'OPEN' ? 'warn' : 'ok'}`}>{batch.status}</span>{' '}
          <span className="muted">容量 {batch.capacity} · Profile v{batch.profileVersionNo}</span>
        </h2>
        <p className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
          {Object.entries(batch.candidateCounts).map(([k, v]) => (
            <span key={k} className={`badge ${STATUS_CLASS[k] ?? ''}`}>
              {statusLabel(k)} {v}
            </span>
          ))}
          {Object.keys(batch.candidateCounts).length === 0 && <span className="muted">还没有候选</span>}
        </p>
        {message && (
          <div className={`notice ${message.includes('✗') ? 'bad' : message.includes('✓') ? 'ok' : ''}`}>
            {message}
          </div>
        )}
        <div className="row" style={{ marginTop: 10 }}>
          {batch.status === 'OPEN' ? (
            <button className="btn secondary" onClick={() => act('关闭批次', () => api.post(`/batches/${id}/close`))}>
              关闭批次
            </button>
          ) : (
            <>
              <button className="btn secondary" onClick={() => act('触发分析', () => api.post(`/batches/${id}/analyze`))}>
                触发分析
              </button>
              <button className="btn secondary" onClick={() => act('生成排名', () => api.post(`/batches/${id}/rank`))}>
                生成排名
              </button>
              <span className="row">
                <input type="number" min={1} max={100} value={topK} style={{ width: 80 }} onChange={(e) => setTopK(Number(e.target.value))} title="Top-K" />
                <button className="btn" onClick={() => act('创建优选集', () => api.post(`/batches/${id}/selections`, { topK }))}>
                  创建优选集
                </button>
              </span>
            </>
          )}
        </div>
        {selections.length > 0 && (
          <p className="row" style={{ gap: 10 }}>
            {selections.map((s) => (
              <Link key={s.id} href={`/selections/${s.id}`} className="badge">
                优选集 #{s.id} · {s.status} · Top-{s.topK}
              </Link>
            ))}
          </p>
        )}
      </div>

      {batch.status === 'OPEN' && (
        <div className="card">
          <h2>上传候选视频（浏览器直传对象存储，≤32MB 单文件）</h2>
          <div className="row">
            <input ref={fileRef} type="file" accept="video/*" />
            <button className="btn" onClick={upload}>
              上传
            </button>
          </div>
          {uploadPct !== null && <progress value={uploadPct} max={100} style={{ marginTop: 8 }} />}
        </div>
      )}

      <div className="card">
        <h2>候选（{candidates.length}）</h2>
        {candidates.length === 0 ? (
          <div className="empty">
            批次还没有候选
            <div className="hint">在上方选择视频文件上传，或等待批量导入</div>
          </div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th>文件</th>
                <th>状态</th>
                <th>大小</th>
                <th>说明</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {candidates.map((c) => (
                <tr key={c.id}>
                  <td className="mono">{c.id}</td>
                  <td>{c.fileName}</td>
                  <td>
                    <span className={`badge ${STATUS_CLASS[c.status] ?? ''}`}>{statusLabel(c.status)}</span>
                  </td>
                  <td className="muted">{(c.sizeBytes / 1024 / 1024).toFixed(1)}MB</td>
                  <td className="muted" style={{ maxWidth: 260 }}>
                    {c.probeError ?? ''}
                  </td>
                  <td>
                    <Link href={`/candidates/${c.id}`}>审阅</Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
