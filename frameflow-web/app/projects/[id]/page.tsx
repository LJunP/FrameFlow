'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useApi } from '@/lib/api';
import { EmptyState } from '@/components/empty-state';
import type { BatchSummary, Brief, PageOf, Profile, Project, SemanticModelCatalog } from '@/lib/types';
import {
  defaultQualityProfileDraft,
  serializeQualityProfileDraft,
  validateQualityProfileDraft,
  type QualityProfileDraft,
} from '@/lib/quality-profile';
import {
  listEnabledSemanticModels,
  parseSemanticModelCatalog,
  resolveSemanticModelId,
} from '@/lib/semantic-models';

type SemanticModelLoadState = 'loading' | 'ready' | 'error';

/** 把批次的候选状态计数渲染成一行摘要，如 "ANALYZED 3 · AUTO_REJECT 1"。 */
function formatBatchCounts(counts: Record<string, number> | null): string {
  const entries = Object.entries(counts ?? {});
  if (entries.length === 0) return '暂无候选';
  return entries.map(([status, n]) => `${status} ${n}`).join(' · ');
}

export default function ProjectPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const api = useApi();
  const [project, setProject] = useState<Project | null>(null);
  const [briefs, setBriefs] = useState<Brief[]>([]);
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [batches, setBatches] = useState<BatchSummary[]>([]);
  const [batchPage, setBatchPage] = useState(0);
  const [batchTotal, setBatchTotal] = useState(0);
  const batchPageSize = 20;
  const [briefText, setBriefText] = useState('');
  const [profileName, setProfileName] = useState('');
  const [profileDraft, setProfileDraft] = useState<QualityProfileDraft>(defaultQualityProfileDraft);
  const [profileErrors, setProfileErrors] = useState<string[]>([]);
  const [semanticModelCatalog, setSemanticModelCatalog] = useState<SemanticModelCatalog | null>(null);
  const [semanticModelLoadState, setSemanticModelLoadState] = useState<SemanticModelLoadState>('loading');
  const [semanticModelLoadError, setSemanticModelLoadError] = useState('');
  const semanticModelRequestId = useRef(0);
  const briefInputRef = useRef<HTMLInputElement>(null);
  const batchFormRef = useRef<HTMLFormElement>(null);
  const [batchProfileId, setBatchProfileId] = useState<number | ''>('');
  const [capacity, setCapacity] = useState(50);
  const [message, setMessage] = useState('');

  const enabledSemanticModels = useMemo(
    () => listEnabledSemanticModels(semanticModelCatalog),
    [semanticModelCatalog],
  );
  const selectedSemanticModel = enabledSemanticModels.find(
    (entry) => entry.id === profileDraft.semanticModelId,
  ) ?? null;
  const semanticModelSelectionBlocked = profileDraft.semanticEnabled && (
    semanticModelLoadState !== 'ready'
    || enabledSemanticModels.length === 0
    || selectedSemanticModel === null
  );

  const load = useCallback(async () => {
    try {
      setProject(await api.get<Project>(`/projects/${id}`));
      setBriefs(await api.get<Brief[]>(`/projects/${id}/briefs`));
      setProfiles(await api.get<Profile[]>('/quality-profiles'));
      const batchPageResult = await api.get<PageOf<BatchSummary>>(
        `/projects/${id}/batches?page=${batchPage}&size=${batchPageSize}`,
      );
      setBatches(batchPageResult.items);
      setBatchTotal(batchPageResult.total);
    } catch (err) {
      setMessage(String(err instanceof Error ? err.message : err));
    }
  }, [api, id, batchPage]);

  // ★ 核心：模型目录只能来自平台 API；加载失败时清空可用目录并锁住 AI Profile 提交，绝不在浏览器补一个假选项。
  const loadSemanticModels = useCallback(async () => {
    const requestId = ++semanticModelRequestId.current;
    setSemanticModelLoadState('loading');
    setSemanticModelLoadError('');
    setSemanticModelCatalog(null);
    try {
      const response = await api.get<unknown>('/semantic-models');
      const catalog = parseSemanticModelCatalog(response);
      if (!catalog) throw new Error('平台返回的模型目录格式不正确');
      if (requestId !== semanticModelRequestId.current) return;
      setSemanticModelCatalog(catalog);
      setProfileDraft((current) => ({
        ...current,
        semanticModelId: resolveSemanticModelId(catalog, current.semanticModelId),
      }));
      setSemanticModelLoadState('ready');
    } catch (err) {
      if (requestId !== semanticModelRequestId.current) return;
      setSemanticModelCatalog(null);
      setSemanticModelLoadError(
        err instanceof Error && err.message === '平台返回的模型目录格式不正确'
          ? err.message
          : '平台模型目录暂时不可用，请稍后重试',
      );
      setSemanticModelLoadState('error');
    }
  }, [api]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    void loadSemanticModels();
    return () => {
      semanticModelRequestId.current += 1;
    };
  }, [loadSemanticModels]);

  if (!project) {
    return message
      ? <div className="card"><div className="notice bad">{message}</div><p style={{ marginTop: 12 }}><Link href="/workspace">← 返回工作台</Link></p></div>
      : <p className="loading">加载中…</p>;
  }

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
            ref={briefInputRef}
            style={{ flex: 1, minWidth: 260 }}
            placeholder="创作要求：主体、风格、禁项（如：不得出现水印）"
            value={briefText}
            required
            onChange={(e) => setBriefText(e.target.value)}
          />
          <button className="btn">发布新快照</button>
        </form>
        {briefs.length === 0 ? (
          <EmptyState
            title="还没有 Brief 快照"
            description="每个批次都必须绑定一条 Brief 快照。先在上方填写创作要求，发布第一条快照。"
            action={{ label: '填写创作要求', onClick: () => briefInputRef.current?.focus() }}
          />
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

      <div className="card quality-editor-card">
        <div className="card-head">
          <div>
            <p className="card-kicker">QUALITY PROFILE</p>
            <h2>创建质检标准</h2>
          </div>
          <span className="badge">创建即发布 v1</span>
        </div>
        <p className="muted quality-editor-intro">用业务语言设置视频边界。系统会在提交时生成内部规则，普通用户无需编写 JSON。</p>
        <form
          className="quality-editor"
          onSubmit={async (e) => {
            e.preventDefault();
            const errors = validateQualityProfileDraft(
              profileName,
              profileDraft,
              enabledSemanticModels.map((entry) => entry.id),
            );
            setProfileErrors(errors);
            if (errors.length) return;
            setMessage('');
            try {
              await api.post('/quality-profiles', {
                name: profileName.trim(),
                description: null,
                spec: serializeQualityProfileDraft(profileDraft),
              });
              setProfileName('');
              setProfileDraft({
                ...defaultQualityProfileDraft,
                semanticDimensions: { ...defaultQualityProfileDraft.semanticDimensions },
                weights: { ...defaultQualityProfileDraft.weights },
                semanticModelId: resolveSemanticModelId(semanticModelCatalog, ''),
              });
              await load();
            } catch (err) {
              setMessage(String(err instanceof Error ? err.message : err));
            }
          }}
        >
          <label className="quality-name-field">标准名称<input value={profileName} required placeholder="例如：电商竖版" onChange={(e) => setProfileName(e.target.value)} /></label>
          <fieldset>
            <legend>视频时长</legend>
            <div className="quality-fields">
              <label>最短秒数<input type="number" min="0" value={profileDraft.minDurationSeconds} onChange={(e) => setProfileDraft((d) => ({ ...d, minDurationSeconds: e.target.value === '' ? '' : Number(e.target.value) }))} /></label>
              <label>最长秒数<input type="number" min="0" value={profileDraft.maxDurationSeconds} onChange={(e) => setProfileDraft((d) => ({ ...d, maxDurationSeconds: e.target.value === '' ? '' : Number(e.target.value) }))} /></label>
            </div>
          </fieldset>
          <fieldset>
            <legend>画面规格</legend>
            <div className="quality-fields">
              <label>最小宽度 px<input type="number" min="1" value={profileDraft.minWidth} placeholder="可选" onChange={(e) => setProfileDraft((d) => ({ ...d, minWidth: e.target.value === '' ? '' : Number(e.target.value) }))} /></label>
              <label>最小高度 px<input type="number" min="1" value={profileDraft.minHeight} placeholder="可选" onChange={(e) => setProfileDraft((d) => ({ ...d, minHeight: e.target.value === '' ? '' : Number(e.target.value) }))} /></label>
            </div>
          </fieldset>
          <fieldset>
            <legend>帧率检查</legend>
            <label className="quality-toggle"><input type="checkbox" checked={profileDraft.fpsEnabled} onChange={(e) => setProfileDraft((d) => ({ ...d, fpsEnabled: e.target.checked }))} />启用最低帧率检查</label>
            {profileDraft.fpsEnabled && <label>最低帧率 fps<input type="number" min="1" max="240" value={profileDraft.minFps} onChange={(e) => setProfileDraft((d) => ({ ...d, minFps: e.target.value === '' ? '' : Number(e.target.value) }))} /></label>}
          </fieldset>
          <fieldset>
            <legend>AI 辅助人工复核</legend>
            <label className="quality-toggle"><input type="checkbox" checked={profileDraft.semanticEnabled} onChange={(e) => {
              const semanticEnabled = e.target.checked;
              setProfileDraft((d) => ({
                ...d,
                semanticEnabled,
                semanticModelId: semanticEnabled
                  ? resolveSemanticModelId(semanticModelCatalog, d.semanticModelId)
                  : d.semanticModelId,
              }));
            }} />启用 AI 提示（不自动淘汰）</label>
            {profileDraft.semanticEnabled && (
              <div className="semantic-model-picker">
                <label htmlFor="semantic-model-id">
                  平台多模态模型
                  <select
                    id="semantic-model-id"
                    value={profileDraft.semanticModelId}
                    disabled={semanticModelLoadState !== 'ready' || enabledSemanticModels.length === 0}
                    required
                    onChange={(e) => setProfileDraft((d) => ({ ...d, semanticModelId: e.target.value }))}
                  >
                    <option value="" disabled>
                      {semanticModelLoadState === 'loading' ? '正在加载平台模型…' : '请选择可用模型'}
                    </option>
                    {enabledSemanticModels.map((entry) => (
                      <option key={entry.id} value={entry.id}>{entry.label} · {entry.model}</option>
                    ))}
                  </select>
                </label>
                <p className="field-help">模型由平台统一接入和托管；这里只选择用途，不需要配置 API Key 或接口地址。</p>
                {semanticModelLoadState === 'loading' && (
                  <div className="notice system" role="status">正在从平台读取可用模型，加载完成前不能创建启用 AI 的标准。</div>
                )}
                {semanticModelLoadState === 'error' && (
                  <div className="notice bad" role="alert">
                    可用模型加载失败：{semanticModelLoadError || '未知错误'}。为避免保存无法执行的配置，已暂停创建。
                    <button className="btn secondary small semantic-model-retry" type="button" onClick={() => void loadSemanticModels()}>重新加载模型</button>
                  </div>
                )}
                {semanticModelLoadState === 'ready' && enabledSemanticModels.length === 0 && (
                  <div className="notice bad" role="alert">平台当前没有启用的多模态模型，暂时不能创建启用 AI 的标准。</div>
                )}
                {semanticModelLoadState === 'ready' && enabledSemanticModels.length > 0 && !selectedSemanticModel && (
                  <div className="notice system" role="status">平台未提供可用的默认模型，请明确选择一个模型后再创建。</div>
                )}
                {selectedSemanticModel && (
                  <div className="semantic-model-detail" aria-live="polite">
                    <strong>{selectedSemanticModel.label}</strong>
                    <p>{selectedSemanticModel.description}</p>
                    <dl>
                      <div><dt>接入协议</dt><dd>{selectedSemanticModel.provider}</dd></div>
                      <div><dt>实际模型</dt><dd className="mono">{selectedSemanticModel.model}</dd></div>
                    </dl>
                  </div>
                )}
                <div className="quality-checks"><label><input type="checkbox" checked={profileDraft.semanticDimensions.promptAlignment} onChange={(e) => setProfileDraft((d) => ({ ...d, semanticDimensions: { ...d.semanticDimensions, promptAlignment: e.target.checked } }))} />Brief 对齐</label><label><input type="checkbox" checked={profileDraft.semanticDimensions.qualityImpression} onChange={(e) => setProfileDraft((d) => ({ ...d, semanticDimensions: { ...d.semanticDimensions, qualityImpression: e.target.checked } }))} />画面观感</label><label><input type="checkbox" checked={profileDraft.semanticDimensions.policyViolation} onChange={(e) => setProfileDraft((d) => ({ ...d, semanticDimensions: { ...d.semanticDimensions, policyViolation: e.target.checked } }))} />内容缺陷</label></div>
              </div>
            )}
          </fieldset>
          <fieldset>
            <legend>评分与近重复</legend>
            <div className="quality-fields"><label>近重复灵敏度<input type="number" min="0" max="64" value={profileDraft.duplicateHammingThreshold} onChange={(e) => setProfileDraft((d) => ({ ...d, duplicateHammingThreshold: Number(e.target.value) }))} /></label><span className="field-help">数值越小越严格；用于识别相似画面。</span></div>
          </fieldset>
          {profileErrors.length > 0 && <div className="notice bad" role="alert"><ul>{profileErrors.map((error) => <li key={error}>{error}</li>)}</ul></div>}
          <div className="quality-editor-footer"><span className="muted">已有 {profiles.length} 个标准</span><button className="btn" disabled={semanticModelSelectionBlocked}>创建并发布标准 ↗</button></div>
        </form>
      </div>

      <div className="card">
        <h2>创建批次（绑定当前 Brief + Profile 版本）</h2>
        <form
          ref={batchFormRef}
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
        {message && <div className="notice bad">{message}</div>}
      </div>

      <div className="card">
        <h2>历史批次{batchTotal > 0 ? `（${batchTotal}）` : ''}</h2>
        {batchTotal === 0 ? (
          <EmptyState
            title="还没有批次"
            description="批次用来承接一批候选素材，并绑定当前的 Brief 与质检标准版本。"
            action={
              project.currentBriefId
                ? { label: '创建第一个批次', onClick: () => batchFormRef.current?.scrollIntoView({ block: 'center' }) }
                : { label: '先发布 Brief 快照', onClick: () => briefInputRef.current?.focus() }
            }
          />
        ) : (
          <>
            <nav className="row" aria-label="批次分页" style={{ marginBottom: 12 }}>
              <button className="btn secondary" disabled={batchPage === 0} onClick={() => setBatchPage((page) => page - 1)}>上一页</button>
              <span>第 {batchPage + 1} / {Math.max(1, Math.ceil(batchTotal / batchPageSize))} 页</span>
              <button className="btn secondary" disabled={(batchPage + 1) * batchPageSize >= batchTotal} onClick={() => setBatchPage((page) => page + 1)}>下一页</button>
            </nav>
            <table style={{ marginTop: 10 }}>
              <thead>
                <tr>
                  <th>批次</th>
                  <th>状态</th>
                  <th>容量</th>
                  <th>候选进度</th>
                  <th>标准版本</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {batches.map((b) => (
                  <tr key={b.id}>
                    <td className="mono">#{b.id}</td>
                    <td><span className={`badge ${b.status === 'OPEN' ? '' : 'ok'}`}>{b.status}</span></td>
                    <td>{b.capacity}</td>
                    <td>{formatBatchCounts(b.candidateCounts)}</td>
                    <td className="muted">{b.profileVersionNo === null ? '—' : `v${b.profileVersionNo}`}</td>
                    <td><Link href={`/batches/${b.id}`}>打开 ↗</Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </div>
    </>
  );
}
