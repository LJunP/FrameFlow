'use client';

import { useEffect, useRef } from 'react';

/**
 * ★ 核心：全局指针场。把鼠标位置阻尼插值后写进 documentElement 的 CSS 变量
 * （--px/--py 平滑像素坐标给跟随光晕，--px2/--py2 更慢一档形成液态拖尾，
 * --mx/--my 归一化 -1..1 给各装饰层做视差）。
 * 为什么这样写：连续指针值走 React state 会每帧重渲染整棵树；这里只用
 * rAF 写 CSS 变量，样式层用 transform/translate 消费，全程留在合成器。
 * 静止约 30 帧后停表节能；reduced-motion 与触屏设备直接不启动。
 */
export function PointerField() {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    if (window.matchMedia('(pointer: coarse)').matches) return;
    const root = document.documentElement;

    let targetX = -600;
    let targetY = -600;
    let x = targetX;
    let y = targetY;
    let x2 = targetX;
    let y2 = targetY;
    let raf = 0;
    let settled = 0;

    const tick = () => {
      x += (targetX - x) * 0.09;
      y += (targetY - y) * 0.09;
      x2 += (targetX - x2) * 0.045;
      y2 += (targetY - y2) * 0.045;
      root.style.setProperty('--px', x.toFixed(1));
      root.style.setProperty('--py', y.toFixed(1));
      root.style.setProperty('--px2', x2.toFixed(1));
      root.style.setProperty('--py2', y2.toFixed(1));
      root.style.setProperty('--mx', ((x / window.innerWidth) * 2 - 1).toFixed(4));
      root.style.setProperty('--my', ((y / window.innerHeight) * 2 - 1).toFixed(4));
      if (Math.abs(targetX - x) < 0.05 && Math.abs(targetY - y) < 0.05) {
        settled += 1;
      } else {
        settled = 0;
      }
      if (settled > 30) {
        raf = 0;
        return;
      }
      raf = requestAnimationFrame(tick);
    };

    const onMove = (event: PointerEvent) => {
      targetX = event.clientX;
      targetY = event.clientY;
      if (raf === 0) {
        settled = 0;
        raf = requestAnimationFrame(tick);
      }
    };

    window.addEventListener('pointermove', onMove, { passive: true });
    return () => {
      window.removeEventListener('pointermove', onMove);
      if (raf !== 0) cancelAnimationFrame(raf);
      for (const key of ['--px', '--py', '--px2', '--py2', '--mx', '--my']) {
        root.style.removeProperty(key);
      }
    };
  }, []);

  return (
    <div ref={ref} className="pointer-field" aria-hidden="true">
      <i className="pf-a" />
      <i className="pf-b" />
    </div>
  );
}
