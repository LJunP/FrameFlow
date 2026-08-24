import type { Metadata } from 'next';
import './globals.css';
import { AuthProvider } from '@/lib/auth-context';
import { AppShell } from '@/components/app-shell/app-shell';

export const metadata: Metadata = {
  title: { default: 'FrameFlow Select | AI 视频质检与优选', template: '%s | FrameFlow Select' },
  description: '为 AI 生成短视频团队建立批量质检、证据审阅与可追溯优选交付流程。',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return <html lang="zh-CN"><body><AuthProvider><AppShell>{children}</AppShell></AuthProvider></body></html>;
}
