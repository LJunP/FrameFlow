'use client';

import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';
import { useAuth } from '@/lib/auth-context';
import { BrandLockup } from './brand-lockup';

const links = [
  { href: '#workflow', label: '工作流' },
  { href: '#capabilities', label: '核心能力' },
  { href: '#evidence', label: '证据优选' },
];

export function MarketingHeader() {
  const { ready, user } = useAuth();
  const [open, setOpen] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);
  const drawer = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;

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
  }, [open]);

  // ★ 核心：公开首页只根据已恢复的会话切换 CTA，不加载任何业务数据；否则访客会收到无意义的权限失败且首屏发生布局跳动。
  const cta = ready && user
    ? { href: '/workspace', label: '进入工作台' }
    : { href: '/login?mode=register', label: '免费开始' };

  return (
    <header className="marketing-header">
      <div className="marketing-nav">
        <BrandLockup inverse />
        <nav className="marketing-links" aria-label="产品导航">
          {links.map((link) => <Link key={link.href} href={link.href}>{link.label}</Link>)}
        </nav>
        <div className="marketing-actions">
          {!ready ? <span className="marketing-session">正在恢复会话</span> : !user && <Link className="marketing-login" href="/login">登录</Link>}
          <Link className="marketing-cta" href={cta.href}>{cta.label} <span aria-hidden="true">↗</span></Link>
        </div>
        <button
          ref={trigger}
          type="button"
          className="marketing-menu-button"
          aria-expanded={open}
          aria-controls="marketing-mobile-menu"
          aria-label={open ? '关闭导航菜单' : '打开导航菜单'}
          onClick={() => setOpen((value) => !value)}
        >
          <span /><span />
        </button>
      </div>
      {open && (
        <div ref={drawer} id="marketing-mobile-menu" className="marketing-mobile-menu" role="dialog" aria-modal="true" aria-label="移动产品导航">
          <nav aria-label="移动产品导航链接">
            {links.map((link) => <Link key={link.href} href={link.href} onClick={() => setOpen(false)}>{link.label}</Link>)}
          </nav>
          {!user && <Link href="/login" onClick={() => setOpen(false)}>登录</Link>}
          <Link className="marketing-cta" href={cta.href} onClick={() => setOpen(false)}>{cta.label} <span aria-hidden="true">↗</span></Link>
        </div>
      )}
    </header>
  );
}
