'use client';

import { useContext } from 'react';
import { ToastContext } from '@/components/toast/toast-provider';

/** ★ 核心：读取全局通知 API。Provider 缺失时显式报错，避免提示被静默吞掉而难以排查。 */
export function useToast() {
  const api = useContext(ToastContext);
  if (!api) throw new Error('useToast 必须在 <ToastProvider> 内使用');
  return api;
}
