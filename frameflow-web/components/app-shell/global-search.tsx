'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { globalSearchPath, useApi } from '@/lib/api';
import type { SearchResponse } from '@/lib/types';

const MIN_KEYWORD_LENGTH = 2;
// ★ 核心：250ms 去抖——按键即发请求会让"输入 ab"产生 a、ab 两次请求，
// 旧响应后到就会覆盖新结果（乱序）。去抖之外还要用代次标记丢弃过期响应，
// 两者缺一都会出现"结果与输入框内容对不上"。
const DEBOUNCE_MS = 250;

type HitGroup = '项目' | '批次' | '候选';

interface FlatHit {
  key: string;
  href: string;
  title: string;
  meta: string;
  status: string;
}

/**
 * 顶栏全局搜索：入口按钮 + 覆盖层面板。
 *
 * 交互契约：输入去抖 250ms 后请求 /api/v1/search；Esc 关闭并退回入口焦点，
 * ↑/↓ 在命中项之间移动、Enter 打开当前项。结果按 项目/批次/候选 分组，
 * 分别回跳 /projects/{id}、/batches/{id}、/candidates/{id}。
 */
export function GlobalSearch() {
  const api = useApi();
  const router = useRouter();
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [data, setData] = useState<SearchResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [active, setActive] = useState(0);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const trimmed = keyword.trim();
  const tooShort = trimmed.length < MIN_KEYWORD_LENGTH;

  const close = useCallback((restoreFocus = true) => {
    setOpen(false);
    if (restoreFocus) requestAnimationFrame(() => triggerRef.current?.focus());
  }, []);

  // 打开时聚焦输入并锁住页面滚动；关闭/卸载还原
  useEffect(() => {
    if (!open) return;
    inputRef.current?.focus();
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [open]);

  // 路由变化后收敛面板（点结果或别处跳转都适用，避免面板悬在新页面上）
  useEffect(() => {
    setOpen(false);
  }, [pathname]);

  // 去抖取数 + 竞态防护
  useEffect(() => {
    if (!open) return;
    if (tooShort) {
      setData(null);
      setError('');
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    const timer = window.setTimeout(() => {
      void (async () => {
        try {
          const resp = await api.get<SearchResponse>(globalSearchPath(trimmed));
          if (cancelled) return;
          setData(resp);
          setError('');
        } catch (err) {
          if (cancelled) return;
          setData(null);
          setError(err instanceof Error ? err.message : String(err));
        } finally {
          if (!cancelled) setLoading(false);
        }
      })();
    }, DEBOUNCE_MS);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [api, open, tooShort, trimmed]);

  const groups = useMemo(() => {
    if (!data) return [] as { label: HitGroup; items: FlatHit[] }[];
    return [
      {
        label: '项目' as HitGroup,
        items: data.projects.map((item) => ({
          key: `project-${item.id}`,
          href: `/projects/${item.id}`,
          title: item.name,
          meta: item.matchedField === 'description' ? '说明命中' : '名称命中',
          status: item.status,
        })),
      },
      {
        label: '批次' as HitGroup,
        items: data.batches.map((item) => ({
          key: `batch-${item.id}`,
          href: `/batches/${item.id}`,
          title: `批次 #${item.id}`,
          meta: `所属项目：${item.projectName}`,
          status: item.status,
        })),
      },
      {
        label: '候选' as HitGroup,
        items: data.candidates.map((item) => ({
          key: `candidate-${item.id}`,
          href: `/candidates/${item.id}`,
          title: item.fileName,
          meta: `所属批次 #${item.batchId}`,
          status: item.status,
        })),
      },
    ].filter((group) => group.items.length > 0);
  }, [data]);

  const hits = useMemo(() => groups.flatMap((group) => group.items), [groups]);
  const indexOfKey = useMemo(
    () => new Map(hits.map((hit, index) => [hit.key, index])),
    [hits],
  );

  useEffect(() => {
    setActive(0);
  }, [hits]);

  // 键盘：Esc 关闭、↑/↓ 移动、Enter 打开
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        close();
        return;
      }
      if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') {
        if (event.key === 'Enter' && hits[active]) {
          event.preventDefault();
          close(false);
          router.push(hits[active].href);
        }
        return;
      }
      if (hits.length === 0) return;
      event.preventDefault();
      setActive((current) => (event.key === 'ArrowDown'
        ? (current + 1) % hits.length
        : (current - 1 + hits.length) % hits.length));
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [active, close, hits, open, router]);

  let body: React.ReactNode;
  if (tooShort) {
    body = <p className="search-hint">输入至少 {MIN_KEYWORD_LENGTH} 个字符开始搜索。</p>;
  } else if (loading) {
    body = <p className="loading">搜索中…</p>;
  } else if (error) {
    body = <div className="notice bad">{error}</div>;
  } else if (hits.length === 0) {
    body = <p className="search-hint">无匹配结果</p>;
  } else {
    body = groups.map((group) => (
      <section key={group.label} className="search-group">
        <p>{group.label}<b>{group.items.length}</b></p>
        {group.items.map((hit) => (
          <Link
            key={hit.key}
            href={hit.href}
            className={`search-item${indexOfKey.get(hit.key) === active ? ' active' : ''}`}
            onMouseEnter={() => setActive(indexOfKey.get(hit.key) ?? 0)}
            onClick={() => close(false)}
          >
            <span className="search-item-main">
              <strong>{hit.title}</strong>
              <small>{hit.meta}</small>
            </span>
            <span className="badge system">{hit.status}</span>
          </Link>
        ))}
      </section>
    ));
  }

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        className="search-trigger"
        aria-label="全局搜索"
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => (open ? close(false) : setOpen(true))}
      >
        <svg className="search-glyph" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
          <circle cx="7" cy="7" r="4.5" />
          <path d="M10.6 10.6 14 14" />
        </svg>
        <span className="search-trigger-label">搜索</span>
      </button>
      {/* ★ 核心：面板必须 portal 到 body——顶栏 .app-header 带 backdrop-filter，
          它会成为 position: fixed 子元素的包含块，留在顶栏里定位会被压缩到
          顶栏高度而不是整个视口。portal 只在 open（纯客户端交互）后触发，
          SSR 阶段不会触碰 document。 */}
      {open && createPortal(
        <div
          className="search-overlay"
          role="dialog"
          aria-modal="true"
          aria-label="全局搜索"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) close();
          }}
        >
          <div className="search-panel">
            <div className="search-field">
              <svg className="search-glyph" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
                <circle cx="7" cy="7" r="4.5" />
                <path d="M10.6 10.6 14 14" />
              </svg>
              <input
                ref={inputRef}
                className="search-input"
                type="text"
                value={keyword}
                placeholder="搜索项目、批次或候选文件名…"
                aria-label="搜索关键词"
                autoComplete="off"
                spellCheck={false}
                onChange={(event) => setKeyword(event.target.value)}
              />
              <button type="button" className="search-close" aria-label="关闭搜索" onClick={() => close()}>Esc</button>
            </div>
            <div className="search-body">{body}</div>
          </div>
        </div>,
        document.body,
      )}
    </>
  );
}
