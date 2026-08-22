'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-context';

export default function TopBar() {
  const { user, team, ready, logout } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  return (
    <nav className="topbar">
      <Link href="/" style={{ color: '#fff', textDecoration: 'none' }} className="brand">
        FrameFlow Select
      </Link>
      {user && (
        <Link
          href="/"
          style={{ color: pathname === '/' ? '#fff' : '#9ca3af', textDecoration: 'none', fontSize: 13 }}
        >
          项目
        </Link>
      )}
      <span className="spacer" />
      {!ready ? (
        <span className="who">…</span>
      ) : user ? (
        <span className="row" style={{ gap: 10 }}>
          <span className="who">
            {user.displayName}（{team?.role}）
          </span>
          <button
            className="btn small secondary"
            style={{ color: '#9ca3af', borderColor: '#374151' }}
            onClick={async () => {
              await logout();
              router.push('/login');
            }}
          >
            登出
          </button>
        </span>
      ) : (
        <Link href="/login" className="btn small">
          登录
        </Link>
      )}
    </nav>
  );
}
