'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { useAuth } from '@/lib/auth-context';
import { BrandLockup } from '@/components/marketing/brand-lockup';

const nav = [
  {
    href: '/workspace',
    label: '项目工作台',
    sub: '项目、批次与交付',
    match: (path: string) => path === '/workspace' || /^\/(projects|batches|candidates|selections)(\/|$)/.test(path),
  },
  { href: '/team', label: '当前团队', sub: '成员与协作边界', match: (path: string) => path === '/team' },
  { href: '/account', label: '个人中心', sub: '账户与会话安全', match: (path: string) => path === '/account' },
];

function workspacePath(path: string) {
  return path === '/workspace' || /^\/(projects|batches|candidates|selections|team|account)(\/|$)/.test(path);
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const { user, team, ready, sessionError, retrySession, logout } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [mobile, setMobile] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);
  const drawer = useRef<HTMLElement>(null);
  const protectedRoute = workspacePath(pathname);
  const workspace = ready && Boolean(user) && protectedRoute;

  useEffect(() => {
    const media = window.matchMedia('(max-width: 700px)');
    const sync = () => {
      setMobile(media.matches);
      if (!media.matches) setOpen(false);
    };
    sync();
    media.addEventListener('change', sync);
    return () => media.removeEventListener('change', sync);
  }, []);

  // ★ 核心：先在壳层挡住所有受保护深链，等会话恢复后再挂载业务页；否则子页面会在 refresh 间隙发出未授权请求并停在错误加载态。
  useEffect(() => {
    // 临时上游故障时 sessionError 代表“未知”，不是“未登录”；此时必须留在重试页，不能清空用户路径并跳转登录。
    if (ready && protectedRoute && !user && !sessionError) {
      router.replace(`/login?next=${encodeURIComponent(pathname)}`);
    }
  }, [pathname, protectedRoute, ready, router, sessionError, user]);

  useEffect(() => {
    if (!mobile || !open || !workspace) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    drawer.current?.querySelector<HTMLElement>('a, button')?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
        trigger.current?.focus();
        return;
      }
      if (event.key !== 'Tab') return;
      const focusable = drawer.current?.querySelectorAll<HTMLElement>('a, button');
      if (!focusable?.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [mobile, open, workspace]);

  // ★ 核心：营销、认证与工作台是不同的内容域。只根据登录态包裹侧栏会把公开首页或登录表单错误地渲染为后台界面。
  if (protectedRoute && sessionError && !user) {
    return <div className="auth-shell"><div className="session-error"><p>{sessionError}</p><button className="btn" type="button" onClick={() => void retrySession()}>重新验证会话</button></div></div>;
  }
  if (protectedRoute && (!ready || !user)) {
    return <div className="auth-shell"><p className="loading auth-loading">正在验证会话…</p></div>;
  }
  if (!workspace) return <div className={pathname === '/login' ? 'auth-shell' : 'public-shell'}>{children}</div>;

  const initial = user?.displayName.slice(0, 1).toUpperCase() || '?';
  const closeMenu = (restoreFocus = false) => {
    setOpen(false);
    if (restoreFocus) requestAnimationFrame(() => trigger.current?.focus());
  };
  const signOut = async () => {
    closeMenu();
    await logout();
    router.push('/login');
  };

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">跳到主要内容</a>
      <header className="app-header">
        <BrandLockup href="/" />
        <nav className="workspace-topnav" aria-label="工作台主导航">
          {nav.map((item) => <Link key={item.href} href={item.href} className={item.match(pathname) ? 'active' : ''} aria-current={item.match(pathname) ? 'page' : undefined}>{item.label}</Link>)}
        </nav>
        <div className="header-spacer" />
        <Link className="team-chip" href="/team"><span>{team?.name.slice(0, 1).toUpperCase() || 'T'}</span><b>{team?.name || '未选择团队'}</b></Link>
        <Link className="account-avatar" href="/account" aria-label="个人中心">{initial}</Link>
        <button ref={trigger} type="button" className="workspace-menu-button" aria-expanded={open} aria-controls="workspace-mobile-menu" aria-label={open ? '关闭工作台菜单' : '打开工作台菜单'} onClick={() => setOpen((value) => !value)}><span /><span /></button>
      </header>
      <aside
        ref={drawer}
        id="workspace-mobile-menu"
        className={`side-nav ${open ? 'mobile-open' : ''}`}
        aria-label="工作台导航"
        aria-hidden={mobile && !open ? true : undefined}
        aria-modal={mobile && open ? true : undefined}
        inert={mobile && !open ? true : undefined}
        role={mobile ? 'dialog' : undefined}
      >
        <p>WORKSPACE</p>
        {nav.map((item) => <Link key={item.href} className={item.match(pathname) ? 'active' : ''} aria-current={item.match(pathname) ? 'page' : undefined} href={item.href} onClick={() => closeMenu(mobile)}><strong>{item.label}</strong><small>{item.sub}</small></Link>)}
        <div className="side-user"><span>{user?.displayName} · {team?.role}</span><button type="button" onClick={signOut}>安全登出</button></div>
      </aside>
      <main id="main-content" className="app-main">{children}</main>
    </div>
  );
}
