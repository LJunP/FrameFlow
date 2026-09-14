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

function pad(n: number, len: number): string {
  return String(n).padStart(len, '0');
}

// ★ 核心：审阅需要“跳到某一毫秒”可核验，故用 HH:MM:SS.mmm 而非取整到秒；
// 保留毫秒位才能判断 seek 是否真的命中分数时间码（如 3.45s）。
function fmtClock(ms: number | null): string {
  if (ms == null || !Number.isFinite(ms) || ms < 0) return '--:--:--.---';
  const t = Math.floor(ms);
  const h = Math.floor(t / 3_600_000);
  const m = Math.floor((t % 3_600_000) / 60_000);
  const s = Math.floor((t % 60_000) / 1000);
  return `${pad(h, 2)}:${pad(m, 2)}:${pad(s, 2)}.${pad(t % 1000, 3)}`;
}

// ★ 核心：截图文件名要求形如 candidate-12-00-03-450.png（H-M-S-ms），
// 冒号/点在文件名中不合法或易被工具曲解，故用短横线分隔。
function snapshotFileName(candidateId: string, ms: number): string {
  const t = Math.max(0, Math.floor(Number.isFinite(ms) ? ms : 0));
  const h = Math.floor(t / 3_600_000);
  const m = Math.floor((t % 3_600_000) / 60_000);
  const s = Math.floor((t % 60_000) / 1000);
  return `candidate-${candidateId}-${pad(h, 2)}-${pad(m, 2)}-${pad(s, 2)}-${pad(t % 1000, 3)}.png`;
}

// ~1/30s：约合 30fps 的单帧步长（仅用于“目视核对相邻帧”，不追求精确帧率换算）
const FRAME_STEP_SEC = 1 / 30;
const PLAYBACK_RATES = [0.25, 0.5, 1, 1.5, 2];

export default function CandidatePage() {
  const { id } = useParams<{ id: string }>();
  const api = useApi();
  const [findings, setFindings] = useState<Finding[]>([]);
  const [videoUrl, setVideoUrl] = useState<string | null>(null);
  const [candidateStatus, setCandidateStatus] = useState('');
  const [probeError, setProbeError] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentMs, setCurrentMs] = useState(0);
  const [durationMs, setDurationMs] = useState<number | null>(null);
  const [rate, setRate] = useState(1);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [snapshotError, setSnapshotError] = useState<string | null>(null);
  const [fsError, setFsError] = useState<string | null>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const shellRef = useRef<HTMLDivElement>(null);

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
    const v = videoRef.current;
    if (ms == null || !v) return;
    // ★ 核心：毫秒直接除以 1000 得到秒并保留完整浮点精度（绝不做取整/对齐），
    // 否则分数时间码被四舍五入后，画面会落在错误帧上且无法通过毫秒显示核验。
    v.currentTime = ms / 1000;
    v.play().catch(() => undefined);
  }

  // ★ 核心：Space 键与按钮共用同一入口；play() 可能被浏览器自动播放策略拒绝，
  // 静默忽略失败以免打断审阅流程。
  const togglePlay = useCallback(() => {
    const v = videoRef.current;
    if (!v) return;
    if (v.paused) v.play().catch(() => undefined);
    else v.pause();
  }, []);

  // ★ 核心：相对跳转必须夹紧到 [0, duration]；越界的 currentTime 会被浏览器静默忽略，
  // 导致“按了方向键却没反应”的假象，且末尾处会出现 NaN。
  const seekBy = useCallback((deltaSec: number) => {
    const v = videoRef.current;
    if (!v) return;
    const upper = Number.isFinite(v.duration) ? v.duration : Infinity;
    v.currentTime = Math.min(Math.max(0, v.currentTime + deltaSec), upper);
  }, []);

  const stepFrame = useCallback((dir: 1 | -1) => {
    const v = videoRef.current;
    if (!v) return;
    const upper = Number.isFinite(v.duration) ? v.duration : Infinity;
    v.currentTime = Math.min(Math.max(0, v.currentTime + dir * FRAME_STEP_SEC), upper);
  }, []);

  // ★ 核心：对 .video-shell 容器（而非 <video>）请求全屏，
  // 这样自研控制条在全屏下仍然可见可用；退出全屏交给 document 统一处理。
  // 失败必须显式反馈：浏览器策略或内嵌宿主会静默拒绝 requestFullscreen。
  // 更坑的是某些宿主既不 resolve 也不 reject（Promise 永久挂起），
  // 仅靠 try/catch 永远不会触发，所以必须加超时兜底把"静默"变成"可见报错"。
  const toggleFullscreen = useCallback(async () => {
    const shell = shellRef.current;
    if (!shell) return;
    setFsError(null);
    let timer: ReturnType<typeof setTimeout> | undefined;
    const timeout = new Promise<never>((_, reject) => {
      timer = setTimeout(() => reject(new Error('宿主在 1.5 秒内未响应全屏请求')), 1500);
    });
    try {
      if (document.fullscreenElement) {
        await document.exitFullscreen();
      } else {
        await Promise.race([shell.requestFullscreen(), timeout]);
      }
    } catch (err) {
      // 超时后宿主仍可能已悄悄进入全屏，先复核真实状态再决定是否报错
      if (document.fullscreenElement) return;
      setFsError(
        `全屏切换失败：${err instanceof Error ? err.message : String(err)}（可能被浏览器或内嵌宿主策略拒绝）`,
      );
    } finally {
      if (timer) clearTimeout(timer);
    }
  }, []);

  // 全屏状态可能由 Esc / 系统手势改变，必须监听 document 事件回写按钮文案
  useEffect(() => {
    const onFsChange = () => setIsFullscreen(Boolean(document.fullscreenElement));
    document.addEventListener('fullscreenchange', onFsChange);
    return () => document.removeEventListener('fullscreenchange', onFsChange);
  }, []);

  // ★ 核心：把当前帧画到离屏 canvas 再导出 PNG。跨域视频会“污染”画布，
  // 此后 toBlob/toDataURL 抛 SecurityError（回调也可能拿到 null）；
  // 必须捕获并以行内提示告知用户，而不是让异常冒泡把整页打断。
  const takeSnapshot = useCallback(() => {
    setSnapshotError(null);
    const v = videoRef.current;
    if (!v) return;
    const w = v.videoWidth;
    const h = v.videoHeight;
    if (!w || !h) {
      setSnapshotError('视频尚未加载出画面，无法截图（请等待画面出现后重试）。');
      return;
    }
    try {
      const canvas = document.createElement('canvas');
      canvas.width = w;
      canvas.height = h;
      const ctx = canvas.getContext('2d');
      if (!ctx) {
        setSnapshotError('当前浏览器不支持 2D 画布，无法截图。');
        return;
      }
      ctx.drawImage(v, 0, 0, w, h);
      canvas.toBlob((blob) => {
        if (!blob) {
          setSnapshotError('截图导出失败：画面可能受跨域保护（tainted canvas），请改用源站代理的视频地址。');
          return;
        }
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = snapshotFileName(String(id), v.currentTime * 1000);
        a.click();
        URL.revokeObjectURL(url);
      }, 'image/png');
    } catch (err) {
      setSnapshotError(
        `截图失败：${err instanceof Error ? err.message : String(err)}（画面可能受跨域限制，非视频本身问题）`,
      );
    }
  }, [id]);

  // ★ 核心：快捷键挂在 window（页面级），但光标在输入框/文本域/下拉框内一律放行，
  // 否则会劫持用户正常录入与选择；空格必须 preventDefault，否则会滚动页面。
  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      const target = e.target as HTMLElement | null;
      const tag = target?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target?.isContentEditable) return;
      switch (e.key) {
        case ' ':
        case 'Spacebar':
          e.preventDefault();
          togglePlay();
          break;
        case 'ArrowLeft':
          e.preventDefault();
          seekBy(-5);
          break;
        case 'ArrowRight':
          e.preventDefault();
          seekBy(5);
          break;
        case ',':
          e.preventDefault();
          stepFrame(-1);
          break;
        case '.':
          e.preventDefault();
          stepFrame(1);
          break;
        case 'f':
        case 'F':
          e.preventDefault();
          toggleFullscreen();
          break;
        default:
          break;
      }
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [togglePlay, seekBy, stepFrame, toggleFullscreen]);

  if (error) return <div className="card"><div className="notice bad">{error}</div><p style={{ marginTop: 12 }}><Link href="/workspace">← 返回工作台</Link></p></div>;

  const deterministic = findings.filter((f) => !f.verdict);
  const semantic = findings.filter((f) => f.verdict);

  function onRateChange(next: number) {
    setRate(next);
    if (videoRef.current) videoRef.current.playbackRate = next;
  }

  return (
    <>
      <div className="card">
        <h2>候选 #{id} 审阅 {candidateStatus && <span className={`badge ${candidateStatus === 'ANALYSIS_ERROR' ? 'system' : ''}`}>{candidateStatus === 'ANALYSIS_ERROR' ? '系统异常 · ANALYSIS_ERROR' : candidateStatus}</span>}</h2>
        {candidateStatus === 'ANALYSIS_ERROR' && <div className="notice system"><strong>分析系统异常，未生成质检结论</strong><p>这不是“视频不合格”，也不是“全部通过”。请修复 Worker 环境后重新触发分析。</p></div>}
        {candidateStatus === 'INVALID' && probeError && <div className="notice bad"><strong>文件入口校验失败</strong><p>{probeError}</p></div>}
        {videoUrl ? (
          <div className="video-shell" ref={shellRef}>
            <video
              ref={videoRef}
              src={videoUrl}
              // ★ 核心：必须声明匿名跨域，否则从对象存储加载的视频会把 canvas 置为
              // “污染”状态，截图 toBlob 直接抛 SecurityError。声明后浏览器会带
              // Origin 请求，并要求存储端返回 Access-Control-Allow-Origin 才放行。
              crossOrigin="anonymous"
              // ★ 核心：不启用原生 controls——本页已有自研控制条，两套控件会重叠成两排。
              onLoadedMetadata={(e) => setDurationMs(e.currentTarget.duration * 1000)}
              onDurationChange={(e) => setDurationMs(e.currentTarget.duration * 1000)}
              onTimeUpdate={(e) => setCurrentMs(e.currentTarget.currentTime * 1000)}
              onPlay={() => setIsPlaying(true)}
              onPause={() => setIsPlaying(false)}
              onRateChange={(e) => setRate(e.currentTarget.playbackRate)}
            />
            <div className="video-controls">
              <button type="button" className="btn secondary small" onClick={togglePlay}>
                {isPlaying ? '暂停' : '播放'}
              </button>
              <button type="button" className="btn secondary small" onClick={() => stepFrame(-1)} title="上一帧（快捷键 ,）">上一帧</button>
              <button type="button" className="btn secondary small" onClick={() => stepFrame(1)} title="下一帧（快捷键 .）">下一帧</button>
              <span className="video-time mono">
                <b>{fmtClock(currentMs)}</b> / {durationMs == null ? '--:--:--.---' : fmtClock(durationMs)}
              </span>
              <label className="video-rate">
                倍速
                <select value={rate} onChange={(e) => onRateChange(Number(e.target.value))}>
                  {PLAYBACK_RATES.map((r) => (
                    <option key={r} value={r}>{r}x</option>
                  ))}
                </select>
              </label>
              <button type="button" className="btn secondary small" onClick={takeSnapshot} title="把当前帧保存为 PNG">截图</button>
              <button type="button" className="btn secondary small" onClick={toggleFullscreen}>
                {isFullscreen ? '退出全屏' : '全屏'}
              </button>
            </div>
            {snapshotError && <div className="notice bad video-snapshot-error">{snapshotError}</div>}
            {fsError && <div className="notice bad video-snapshot-error">{fsError}</div>}
            <p className="video-hint">快捷键：空格 播放/暂停 · ←/→ 后退/前进 5 秒 · , . 逐帧 · F 全屏</p>
          </div>
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
