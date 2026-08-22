import type { Metadata } from 'next';
import './globals.css';
import { AuthProvider } from '@/lib/auth-context';
import TopBar from '@/components/top-bar';

export const metadata: Metadata = {
  title: 'FrameFlow Select',
  description: 'AI 生成短视频的批量质检与优选平台',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body>
        <AuthProvider>
          <TopBar />
          <div className="container">{children}</div>
        </AuthProvider>
      </body>
    </html>
  );
}
