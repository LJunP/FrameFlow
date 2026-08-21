export const metadata = {
  title: "FrameFlow Select - Learning",
  description: "Learning-first rebuild of FrameFlow Select"
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
