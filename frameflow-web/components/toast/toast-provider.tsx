'use client';

import { createContext, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

/** 通知语义级别：成功 / 失败 / 提示。 */
type ToastKind = 'success' | 'error' | 'info';

interface ToastItem {
  id: number;
  kind: ToastKind;
  message: string;
}

/** 页面可调用的通知 API，由 useToast() 暴露。 */
export interface ToastApi {
  success: (message: string) => void;
  error: (message: string) => void;
  info: (message: string) => void;
}

export const ToastContext = createContext<ToastApi | null>(null);

/** 最多同时显示 4 条：超出时挤掉最旧的一条，避免连续失败刷屏遮挡页面。 */
const MAX_VISIBLE = 4;
/** 自动消失时长：普通通知 4s；错误保留更久，便于读清失败原因。 */
const TTL_MS: Record<ToastKind, number> = { success: 4000, info: 4000, error: 7000 };

/**
 * ★ 核心：全局通知宿主。状态与定时器都收敛在这一个 Provider 里，页面只通过
 * toast.success/error/info 抛事件，不再各自维护临时提示 state。
 * 为什么用 Portal：通知必须悬浮在 AppShell 与弹窗（z-index 60）之上，脱离各页面
 * 的层叠上下文，否则会被 overflow/transform 的祖先裁切或压住。
 * SSR 安全：初始 mounted=false，首帧服务端与客户端一致（都不渲染 Portal），
 * 挂载后再渲染，避免水合不一致。
 */
export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const [mounted, setMounted] = useState(false);
  const seq = useRef(0);
  const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>());

  useEffect(() => setMounted(true), []);

  const dismiss = useCallback((id: number) => {
    const timer = timers.current.get(id);
    if (timer) { clearTimeout(timer); timers.current.delete(id); }
    setToasts((current) => current.filter((item) => item.id !== id));
  }, []);

  const push = useCallback((kind: ToastKind, message: string) => {
    const id = (seq.current += 1);
    // ★ 核心：只保留最近 MAX_VISIBLE 条。被挤掉的那条无需手动清定时器——
    // 其 dismiss 到期执行时该 id 已不在列表，过滤是幂等的。
    setToasts((current) => [...current, { id, kind, message }].slice(-MAX_VISIBLE));
    timers.current.set(id, setTimeout(() => dismiss(id), TTL_MS[kind]));
  }, [dismiss]);

  // ★ 核心：卸载时统一清表，避免定时器在组件树销毁后仍触发 setState。
  useEffect(() => {
    const pending = timers.current;
    return () => { for (const timer of pending.values()) clearTimeout(timer); pending.clear(); };
  }, []);

  const api = useMemo<ToastApi>(() => ({
    success: (message) => push('success', message),
    error: (message) => push('error', message),
    info: (message) => push('info', message),
  }), [push]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      {mounted && createPortal(
        <div className="toast-stack">
          {toasts.map((item) => (
            <div
              key={item.id}
              className={`toast toast-${item.kind}`}
              role={item.kind === 'error' ? 'alert' : 'status'}
              aria-live={item.kind === 'error' ? 'assertive' : 'polite'}
              aria-atomic="true"
            >
              <span className="toast-dot" aria-hidden="true" />
              <p className="toast-text">{item.message}</p>
              <button className="toast-close" type="button" aria-label="关闭通知" onClick={() => dismiss(item.id)}>×</button>
            </div>
          ))}
        </div>,
        document.body,
      )}
    </ToastContext.Provider>
  );
}
