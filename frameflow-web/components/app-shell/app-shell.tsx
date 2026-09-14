'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { useAuth } from '@/lib/auth-context';
import { BrandLockup } from '@/components/marketing/brand-lockup';
import { GlobalSearch } from '@/components/app-shell/global-search';

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
  // ★ 核心：手势监听器只绑定一次（依赖 mobile/workspace），用 ref 读取最新开关状态，避免 open 变化导致 effect 重建并打断收尾动画。
  const openRef = useRef(open);
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

  useEffect(() => {
    openRef.current = open;
  }, [open]);

  // ★ 核心：移动端抽屉滑动手势（纯原生 Touch，不引依赖）。关闭态从左缘右滑打开、打开态在抽屉内左滑关闭，
  // 拖动期间用内联 translateX 跟手，松手按 35% 宽度或快速滑动方向吸附；桌面端与非触屏设备完全不介入。
  useEffect(() => {
    if (!mobile || !workspace) return;
    const el = drawer.current;
    if (!el) return;
    // ★ 核心：仅在触屏设备启用（粗指针或存在触摸事件），保证桌面端鼠标行为与原先完全一致。
    const coarse = window.matchMedia('(pointer: coarse)').matches || 'ontouchstart' in window;
    if (!coarse) return;

    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)');
    const EDGE_ZONE = 24; // 左缘触发带宽度（px），超出不从边缘起手则不判定为“打开”
    const OPEN_RATIO = 0.35; // 拖过抽屉宽度 35% 即视为要切换，配合速度可越过
    const FLICK = 0.5; // 快速滑动判定（px/ms），用于“轻扫即切换”
    const DIR_RATIO = 1.2; // |dx| 必须明显大于 |dy| 才认定水平拖拽，避免干扰抽屉内纵向滚动
    const MIN_MOVE = 8; // 方向死区，避免手指微抖时误判

    let startX = 0, startY = 0, lastX = 0, lastTime = 0, velocity = 0, lastOffset = -9999, width = 0;
    let mode: 'open' | 'close' | null = null; // 手势意图：关闭态右滑打开 / 打开态左滑关闭
    let active = false; // 是否已确认水平拖拽并接管
    let fallback = 0;

    const resetInline = () => {
      el.style.transform = '';
      el.style.transition = '';
      el.style.visibility = '';
    };

    const snap = (shouldOpen: boolean) => {
      const target = shouldOpen ? 0 : -width;
      // ★ 核心：先把内联值动画到目标位置（内联优先于 class），避免 setOpen 后 class 切换造成闪跳；减弱动效时直接跳变。
      el.style.transition = reduced.matches ? 'none' : '';
      el.style.transform = `translateX(${target}px)`;
      setOpen(shouldOpen);
      // ★ 核心：过渡结束后交回 class 控制；transitionend 可能因减弱动效/中断不触发，用超时兜底清理内联样式。
      fallback = window.setTimeout(resetInline, 320);
      el.addEventListener('transitionend', resetInline, { once: true });
    };

    const onStart = (event: TouchEvent) => {
      if (event.touches.length !== 1) return;
      const touch = event.touches[0];
      width = el.getBoundingClientRect().width;
      startX = touch.clientX;
      startY = touch.clientY;
      lastX = startX;
      lastTime = event.timeStamp;
      velocity = 0;
      active = false;
      mode = null;
      if (openRef.current) {
        // 打开态：只有从抽屉内部按下才可能左滑关闭，避免误吞主内容区的滑动
        mode = event.target instanceof Node && el.contains(event.target) ? 'close' : null;
      } else {
        // 关闭态：只有从屏幕左缘按下才可能右滑打开
        mode = touch.clientX <= EDGE_ZONE ? 'open' : null;
      }
    };

    const onMove = (event: TouchEvent) => {
      if (!mode) return;
      const touch = event.touches[0];
      if (!touch) return;
      const dx = touch.clientX - startX;
      const dy = touch.clientY - startY;
      const base = mode === 'close' ? 0 : -width; // 手势起点位移：打开态 0，关闭态 -width
      if (!active) {
        if (Math.abs(dx) < MIN_MOVE && Math.abs(dy) < MIN_MOVE) return; // 死区内方向未定
        // ★ 核心：|dx| 未明显大于 |dy| 时放弃接管，把纵向滚动让给抽屉内容，避免“滑不动列表”。
        if (Math.abs(dx) < Math.abs(dy) * DIR_RATIO) { mode = null; return; }
        // 方向需与意图一致，反向抖动时继续等待
        if (mode === 'open' && dx <= 0) return;
        if (mode === 'close' && dx >= 0) return;
        active = true;
        // ★ 核心：确认水平拖拽后才接管——关闭过渡让抽屉实时跟手，并强制可见（关闭态 CSS 为 visibility:hidden）。
        el.style.transition = 'none';
        el.style.visibility = 'visible';
      }
      if (event.cancelable) event.preventDefault(); // 仅在接管后阻止页面滚动
      lastOffset = Math.max(-width, Math.min(0, base + dx));
      el.style.transform = `translateX(${lastOffset}px)`;
      const dt = event.timeStamp - lastTime;
      if (dt > 0) velocity = (touch.clientX - lastX) / dt;
      lastX = touch.clientX;
      lastTime = event.timeStamp;
    };

    const finish = () => {
      if (active && mode && lastOffset > -9999) {
        const progress = (lastOffset + width) / width; // 0=全关，1=全开
        // ★ 核心：先看快速滑动方向（轻扫），否则看拖拽距离是否越过 35% 阈值，避免轻微拖动就切换。
        const shouldOpen = Math.abs(velocity) > FLICK
          ? velocity > 0
          : mode === 'open' ? progress >= OPEN_RATIO : progress > 1 - OPEN_RATIO;
        snap(shouldOpen);
      }
      active = false;
      mode = null;
      velocity = 0;
      lastOffset = -9999;
    };

    document.addEventListener('touchstart', onStart, { passive: true });
    // ★ 核心：move 需要按需 preventDefault 阻止滚动，故显式 non-passive；start/end 保持 passive 不阻塞滚动。
    document.addEventListener('touchmove', onMove, { passive: false });
    document.addEventListener('touchend', finish, { passive: true });
    document.addEventListener('touchcancel', finish, { passive: true });
    return () => {
      document.removeEventListener('touchstart', onStart);
      document.removeEventListener('touchmove', onMove);
      document.removeEventListener('touchend', finish);
      document.removeEventListener('touchcancel', finish);
      window.clearTimeout(fallback);
      if (active) resetInline(); // 拖拽中途卸载时清理内联样式，避免残留覆盖 class
    };
  }, [mobile, workspace]);

  // ★ 核心：营销、认证与工作台是不同的内容域。只根据登录态包裹侧栏会把公开首页或登录表单错误地渲染为后台界面。
  if (protectedRoute && sessionError && !user) {
    return <div className="auth-shell"><div className="session-error"><p>{sessionError}</p><button className="btn" type="button" onClick={() => void retrySession()}>重新验证会话</button></div></div>;
  }
  if (protectedRoute && (!ready || !user)) {
    return <div className="auth-shell"><p className="loading auth-loading">正在验证会话…</p></div>;
  }
  const authShell = pathname === '/login' || pathname === '/reset-password' || pathname === '/verify-email' || pathname.startsWith('/invite');
  if (!workspace) return <div className={authShell ? 'auth-shell' : 'public-shell'}>{children}</div>;

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
        <GlobalSearch />
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
