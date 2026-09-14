import Link from 'next/link';

export interface EmptyStateAction {
  label: string;
  /** 目标路由：优先使用 onClick，二者都有时以 onClick 为准（本地动作如打开弹层）。 */
  href?: string;
  onClick?: () => void;
}

export interface EmptyStateProps {
  title: string;
  description: string;
  action?: EmptyStateAction;
  secondary?: EmptyStateAction;
}

/** 渲染一个动作位：有 onClick 用按钮，只有 href 用链接，都没有则退化为纯文本，避免出现"点了没反应"的假按钮。 */
function EmptyStateActionView({ action, variant }: { action: EmptyStateAction; variant: 'primary' | 'secondary' }) {
  const className = variant === 'primary' ? 'btn small' : 'btn small secondary';
  if (action.onClick) {
    return <button type="button" className={className} onClick={action.onClick}>{action.label}</button>;
  }
  if (action.href) {
    return <Link className={className} href={action.href}>{action.label}</Link>;
  }
  return <span className="empty-state-hint">{action.label}</span>;
}

/**
 * ★ 核心：列表为空时的统一空状态。三个作用缺一不可：
 * 1) 说明"这里本来该有什么"（title），避免用户误以为页面加载失败；
 * 2) 给出下一步怎么做（description + action），把空白页变成引导页；
 * 3) 装饰图形为纯内联 SVG（品牌帧条），不依赖外部图片，离线/弱网也不裂图。
 * 无障碍：装饰层 aria-hidden，标题用 heading 建立语义层级，动作区用 role="group" 标注。
 */
export function EmptyState({ title, description, action, secondary }: EmptyStateProps) {
  return (
    <div className="empty-state">
      <span className="empty-state-motif" aria-hidden="true">
        <svg viewBox="0 0 48 48" width="46" height="46" focusable="false" aria-hidden="true">
          <rect className="esm-frame" x="5" y="5" width="38" height="38" rx="11" />
          <rect className="esm-bar" x="13" y="26" width="5" height="9" rx="1.5" />
          <rect className="esm-bar" x="21.5" y="20" width="5" height="15" rx="1.5" />
          <rect className="esm-bar" x="30" y="14" width="5" height="21" rx="1.5" />
        </svg>
      </span>
      <h3 className="empty-state-title">{title}</h3>
      <p className="empty-state-desc">{description}</p>
      {(action || secondary) && (
        <div className="empty-state-actions" role="group" aria-label={`${title}的下一步操作`}>
          {action && <EmptyStateActionView action={action} variant="primary" />}
          {secondary && <EmptyStateActionView action={secondary} variant="secondary" />}
        </div>
      )}
    </div>
  );
}
