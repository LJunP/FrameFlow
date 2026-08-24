'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useApi } from '@/lib/api';
import type { Finding } from '@/lib/types';

function fmtTime(ms: number | null): string {
  if (ms == null) return '—';
  const s = ms / 1000;
  const m = Math.floor(s / 60);
  return `${m}:${(s - m * 60).toFixed(1).padStart(4, '0')}`;
}

export default function CandidatePage() {
  const { id } = useParams<{ id: string }>();
  const api = useApi();
  const [findings, setFindings] = useState<Finding[]>([]);
  const [videoUrl, setVideoUrl] = useState<string | null>(null);
  const [candidateStatus, setCandidateStatus] = useState('');
  const [probeError, setProbeError] = useState<string | null>(null);
  const [error, setError] = useState('');
  const videoRef = useRef<HTMLVideoElement>(null);

  const load = useCallback(async () => {
    try {
      // 候选详情经批次列表页跳转携带 batchId；此处从 findings 接口无法取，
      // 直接用列表页上下文不可得——改用 content-url 接口即可（404 会带原因）
      const content = await api.get<{ url: string; status: string; probeError: string | null }>(`/candidates/${id}/content-url`);
      setVideoUrl(content.url);
      setCandidateStatus(content.status);
      setProbeError(content.probeError);
      setFindings(await api.get<Finding[]>(`/candidates/${id}/findings`));
    } catch (err) {
      setError(String(err instanceof Error ? err.message : err));
    }
  }, [api, id]);

  useEffect(() => {
    load();
  }, [load]);

  function seek(ms: number | null) {
    if (ms == null || !videoRef.current) return;
    videoRef.current.currentTime = ms / 1000;
    videoRef.current.play().catch(() => undefined);
  }

  if (error) return <div className="card"><div className="notice bad">{error}</div><p style={{ marginTop: 12 }}><Link href="/workspace">← 返回工作台</Link></p></div>;

  const deterministic = findings.filter((f) => !f.verdict);
  const semantic = findings.filter((f) => f.verdict);

  return (
    <>
      <div className="card">
        <h2>候选 #{id} 审阅 {candidateStatus && <span className={`badge ${candidateStatus === 'ANALYSIS_ERROR' ? 'system' : ''}`}>{candidateStatus === 'ANALYSIS_ERROR' ? '系统异常 · ANALYSIS_ERROR' : candidateStatus}</span>}</h2>
        {candidateStatus === 'ANALYSIS_ERROR' && <div className="notice system"><strong>分析系统异常，未生成质检结论</strong><p>这不是“视频不合格”，也不是“全部通过”。请修复 Worker 环境后重新触发分析。</p></div>}
        {candidateStatus === 'INVALID' && probeError && <div className="notice bad"><strong>文件入口校验失败</strong><p>{probeError}</p></div>}
        {videoUrl ? (
          <video ref={videoRef} src={videoUrl} controls style={{ width: '100%', maxHeight: 420, background: '#000', borderRadius: 8 }} />
        ) : (
          <p className="loading">视频地址加载中…（无法播放的候选通常是签名未通过或文件损坏）</p>
        )}
      </div>

      <div className="card">
        <h2>确定性 Finding（{deterministic.length}）——点击时间码跳转画面</h2>
        <ul className="timeline">
          {deterministic.map((f) => (
            <li key={f.id} onClick={() => seek(f.timecodeMs)}>
              <span className={`badge ${f.passed ? 'ok' : f.severity === 'BLOCKER' ? 'bad' : 'warn'}`}>
                {f.passed ? 'PASS' : f.severity}
              </span>{' '}
              <b>{f.dimension}</b> <span className="t">{fmtTime(f.timecodeMs)} · {f.detector}</span>
              <div className="muted">{f.message}</div>
              {f.evidence && <details><summary className="t">证据</summary><pre className="mono">{f.evidence}</pre></details>}
            </li>
          ))}
          {deterministic.length === 0 && <li className="muted">{candidateStatus === 'ANALYSIS_ERROR' ? '系统异常，未生成确定性质检结论' : '暂无确定性结论（未分析，或全部通过且无记录）'}</li>}
        </ul>
      </div>

      <div className="card">
        <h2>语义 Finding（{semantic.length}）——模型意见，仅供人参考</h2>
        <ul className="timeline">
          {semantic.map((f) => (
            <li key={f.id} onClick={() => seek(f.timecodeMs)}>
              <span className={`badge ${f.verdict === 'PASS' ? 'ok' : f.verdict === 'VIOLATE' ? 'bad' : 'warn'}`}>
                {f.verdict}
              </span>{' '}
              <b>{f.dimension}</b> <span className="t">{f.detector}</span>
              <div className="muted">{f.message}</div>
              {f.evidence && <details><summary className="t">证据束</summary><pre className="mono">{f.evidence}</pre></details>}
            </li>
          ))}
          {semantic.length === 0 && <li className="muted">{candidateStatus === 'ANALYSIS_ERROR' ? '系统异常，未生成语义质检结论' : '暂无语义结论（质检标准未启用语义检测，或 Provider 禁用）'}</li>}
        </ul>
      </div>
    </>
  );
}
