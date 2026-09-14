'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useApi, putWithProgress, uploadMultipartParts, batchEventsUrl } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import { browserUploadError, collectVideoFiles, SIMPLE_UPLOAD_THRESHOLD_BYTES } from '@/lib/batch-upload';
import { EmptyState } from '@/components/empty-state';
import type { Batch, Candidate, PageOf, RegisterCandidateResponse, SelectionSummary, UploadedPart, UploadPartsResponse } from '@/lib/types';

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
  const { accessToken } = useAuth();
  const [batch, setBatch] = useState<Batch | null>(null);
  const [candidates, setCandidates] = useState<Candidate[]>([]);
  const [selections, setSelections] = useState<SelectionSummary[]>([]);
  const [message, setMessage] = useState('');
  const [uploadPct, setUploadPct] = useState<number | null>(null);
  const [uploadQueue, setUploadQueue] = useState<{ current: number; total: number; name: string } | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);
  const [topK, setTopK] = useState(5);
  const [candidatePage, setCandidatePage] = useState(0);
  const [candidateTotal, setCandidateTotal] = useState(0);
  const [loadingCandidates, setLoadingCandidates] = useState(false);
  // SSE 实时通道是否已判定不可用（不支持 EventSource / 连续失败）→ 回退轮询
  const [sseFailed, setSseFailed] = useState(false);
  const loadSequence = useRef(0);
  const uploading = useRef(false);
  const pageSize = 50;

  // 供副作用判断的派生布尔值：只用布尔做依赖，避免每次计数变化都重建连接。
  const analyzingNow = (batch?.candidateCounts.ANALYZING ?? 0) > 0;
  const busyNow = batch
    ? Object.entries(batch.candidateCounts).some(
      ([k, v]) => (k === 'PENDING_UPLOAD' || k === 'ANALYZING') && v > 0,
    )
    : false;

  const load = useCallback(async () => {
    const sequence = ++loadSequence.current;
    setLoadingCandidates(true);
    try {
      const [detail, page, selectionList] = await Promise.all([
        api.get<Batch>(`/batches/${id}`),
        api.get<PageOf<Candidate>>(`/batches/${id}/candidates?page=${candidatePage}&size=${pageSize}`),
        api.get<SelectionSummary[]>(`/batches/${id}/selections`),
      ]);
      // ★ 核心：翻页后的新请求拥有结果；旧轮询晚到不能覆盖当前页。
      if (sequence !== loadSequence.current) return;
      setBatch(detail);
      setCandidates(page.items);
      setCandidateTotal(page.total);
      setSelections(selectionList);
    } catch (err) {
      if (sequence === loadSequence.current) setMessage(String(err instanceof Error ? err.message : err));
    } finally {
      if (sequence === loadSequence.current) setLoadingCandidates(false);
    }
  }, [api, id, candidatePage]);

  useEffect(() => {
    load();
    return () => { ++loadSequence.current; };
  }, [load]);

  // ★ 核心：分析进行中用 SSE 实时推送替代固定 3 秒轮询——服务端仅在计数变化时
  // 推 progress 事件、进入终态推 done。EventSource 无法带自定义头，token 由
  // batchEventsUrl 走查询参数交给同源路由换成 Bearer 头；事件到达即更新徽标/进度条
  // 并顺带刷新候选列表。
  useEffect(() => {
    if (!analyzingNow) {
      // 分析结束（或还没开始）：重置失败标记，清掉可能存在的降级态。
      setSseFailed(false);
      return;
    }
    if (sseFailed || typeof EventSource === 'undefined') {
      // 环境不支持 EventSource → 确定不可用，回退轮询兜底。
      setSseFailed(true);
      return;
    }
    if (!accessToken) {
      // 静默续期尚未拿到 token：不标记失败，待 token 到位后本 effect 会重跑并建连。
      return;
    }
    let failures = 0;
    const source = new EventSource(batchEventsUrl(id, accessToken));
    const onProgress = (event: MessageEvent) => {
      failures = 0;
      try {
        const next = JSON.parse(event.data) as Record<string, number>;
        setBatch((prev) => (prev ? { ...prev, candidateCounts: next } : prev));
      } catch { /* 忽略无法解析的事件帧 */ }
      void load();
    };
    const onDone = () => {
      source.close();
      void load();
    };
    source.addEventListener('progress', onProgress);
    source.addEventListener('done', onDone);
    // EventSource 断开会自动重连；连续失败达阈值即关闭并切轮询，避免无限重连打后端。
    source.onerror = () => {
      failures += 1;
      if (failures >= 3) {
        source.close();
        setSseFailed(true);
      }
    };
    return () => source.close();
  }, [analyzingNow, sseFailed, accessToken, id, load]);

  // 轮询兜底：SSE 不可用（不支持/连续失败）时，或只有 PENDING_UPLOAD（无分析）时使用。
  // 批次进度是缓存优先端点，轮询很便宜。
  useEffect(() => {
    if (!busyNow) return;
    if (analyzingNow && !sseFailed) return;   // 分析中且 SSE 正常 → 不需要轮询
    const timer = setInterval(load, 3000);
    return () => clearInterval(timer);
  }, [busyNow, analyzingNow, sseFailed, load]);

  if (!batch) {
    return message
      ? <div className="card"><div className="notice bad">{message}</div><p style={{ marginTop: 12 }}><Link href="/workspace">← 返回工作台</Link></p></div>
      : <p className="loading">加载中…</p>;
  }

  // ★ 核心：批次自身没有 ANALYZING 状态——"分析中"由候选计数推导（ANALYZING > 0）。
  // 进度分母只算进入分析流水线的候选：终态四种 + 在析 + 待派发（UPLOADED）；
  // PENDING_UPLOAD/INVALID 从不进入分析，不计入，否则进度永远到不了 100%。
  const counts = batch.candidateCounts;
  const analyzingCount = counts.ANALYZING ?? 0;
  const isAnalyzing = analyzingCount > 0;
  const analysisDone =
    (counts.ANALYZED ?? 0) + (counts.AUTO_REJECT ?? 0) +
    (counts.ANALYSIS_ERROR ?? 0) + (counts.REVIEW_REQUIRED ?? 0);
  const analysisTotal = analysisDone + analyzingCount + (counts.UPLOADED ?? 0);
  const analysisPct = analysisTotal > 0 ? Math.round((analysisDone / analysisTotal) * 100) : 0;

  async function uploadOne(file: File) {
    const error = browserUploadError(file);
    if (error) throw new Error(error);
    const reg = await api.post<RegisterCandidateResponse>(`/batches/${id}/candidates`, {
      fileName: file.name,
      contentType: file.type || 'video/mp4',
      sizeBytes: file.size,
      simpleOnly: file.size <= SIMPLE_UPLOAD_THRESHOLD_BYTES,
    });
    setUploadPct(0);
    let parts: UploadedPart[] | undefined;
    if (reg.mode === 'MULTIPART') {
      parts = await uploadMultipartParts({
        file,
        partSizeBytes: reg.partSizeBytes,
        fetchPartUrls: async (partNumbers) => {
          const resp = await api.post<UploadPartsResponse>(
            `/candidates/${reg.candidateId}/upload-parts`,
            { partNumbers },
          );
          return resp.partUrls;
        },
        onProgress: setUploadPct,
      });
    } else {
      if (!reg.uploadUrl) throw new Error('后端未返回上传地址');
      await putWithProgress(reg.uploadUrl, file, setUploadPct);
    }
    return api.post<{ candidateId: number; status: string; probeError: string | null }>(
      `/candidates/${reg.candidateId}/complete`,
      parts ? { parts } : {},
    );
  }

  async function uploadQueueOf(files: File[]) {
    const { accepted, rejected } = collectVideoFiles(files);
    if (accepted.length === 0) {
      setMessage(`✗ ${rejected[0]?.reason ?? '没有可上传的视频'}`);
      return;
    }
    if (uploading.current) return;
    uploading.current = true;
    setMessage('');
    const notes: string[] = rejected.map((item) => `${item.name}：${item.reason}`);
    try {
      for (let i = 0; i < accepted.length; i += 1) {
        const file = accepted[i];
        setUploadQueue({ current: i + 1, total: accepted.length, name: file.name });
        setUploadPct(0);
        const done = await uploadOne(file);
        notes.push(done.status === 'UPLOADED'
          ? `${file.name} 已上传`
          : `${file.name} 被判无效：${done.probeError}`);
        await load();
      }
      const failed = notes.filter((line) => line.includes('无效') || line.includes('：')).length;
      setMessage(failed === 0
        ? `✓ 已上传 ${accepted.length} 个文件；关闭批次后可触发分析`
        : `✓ 队列结束：${notes.join('；')}`);
    } catch (err) {
      setMessage(`✗ ${String(err instanceof Error ? err.message : err)}`);
    } finally {
      uploading.current = false;
      setUploadPct(null);
      setUploadQueue(null);
      if (fileRef.current) fileRef.current.value = '';
    }
  }

  async function upload() {
    const selected = fileRef.current?.files;
    if (!selected || selected.length === 0) return;
    await uploadQueueOf(Array.from(selected));
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
          {isAnalyzing && <span className="badge warn analyzing">分析中 {analysisPct}%</span>}{' '}
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
        {isAnalyzing && (
          <div className="notice system" role="status" aria-live="polite">
            分析进行中：已完成 {analysisDone} / {analysisTotal} 个候选（{analysisPct}%），进度实时推送{sseFailed ? '（实时通道不可用，已回退轮询）' : ''}
            <progress value={analysisDone} max={analysisTotal} style={{ marginTop: 8 }} aria-label="分析进度" />
          </div>
        )}
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
              <button className="btn secondary" disabled={isAnalyzing}
                title={isAnalyzing ? '分析进行中，完成后可再次触发' : undefined}
                onClick={() => act('触发分析', () => api.post(`/batches/${id}/analyze`))}>
                {isAnalyzing ? '分析中…' : '触发分析'}
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
          <h2>上传候选视频（可多选或拖放，≤200 MiB 单文件，大文件自动分片）</h2>
          <div
            className={`upload-zone${dragOver ? ' dragover' : ''}`}
            onDragOver={(event) => { event.preventDefault(); setDragOver(true); }}
            onDragLeave={() => setDragOver(false)}
            onDrop={(event) => {
              event.preventDefault();
              setDragOver(false);
              void uploadQueueOf(Array.from(event.dataTransfer.files));
            }}
          >
            <input ref={fileRef} type="file" accept="video/*" multiple aria-label="选择候选视频" disabled={uploadPct !== null} />
            <button className="btn" onClick={upload} disabled={uploadPct !== null}>
              {uploadQueue ? `上传中 ${uploadQueue.current}/${uploadQueue.total}` : '上传'}
            </button>
            <span className="muted">或把多个视频拖进此区域，将按顺序排队直传。</span>
          </div>
          {uploadQueue && <p className="muted" style={{ marginTop: 8 }}>正在上传 {uploadQueue.name}（{uploadQueue.current}/{uploadQueue.total}）</p>}
          {uploadPct !== null && <progress value={uploadPct} max={100} style={{ marginTop: 8 }} />}
        </div>
      )}

      <div className="card">
        <h2>候选（{candidateTotal}）</h2>
        <nav className="row" aria-label="候选分页" style={{ marginBottom: 12 }}>
          <button className="btn secondary" disabled={candidatePage === 0 || loadingCandidates}
            onClick={() => setCandidatePage((page) => page - 1)}>上一页</button>
          <span aria-live="polite">第 {candidatePage + 1} / {Math.max(1, Math.ceil(candidateTotal / pageSize))} 页</span>
          <button className="btn secondary" disabled={(candidatePage + 1) * pageSize >= candidateTotal || loadingCandidates}
            onClick={() => setCandidatePage((page) => page + 1)}>下一页</button>
        </nav>
        {candidates.length === 0 ? (
          <EmptyState
            title="批次还没有候选视频"
            description={batch.status === 'OPEN'
              ? '选择视频文件上传即可开始质检；大文件会自动分片，无需额外操作。'
              : '批次已关闭，无法再上传新候选；请新建批次继续上传。'}
            action={batch.status === 'OPEN'
              ? { label: '选择视频上传', onClick: () => fileRef.current?.click() }
              : undefined}
          />
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
