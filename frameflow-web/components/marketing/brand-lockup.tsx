import Link from 'next/link';

export function BrandLockup({ inverse = false, href = '/' }: { inverse?: boolean; href?: string }) {
  return <Link className={`ff-brand ${inverse ? 'inverse' : ''}`} href={href} aria-label="FrameFlow Select 首页"><span className="ff-brand-mark" aria-hidden="true"><i /><i /><i /></span><span>FrameFlow <b>Select</b></span></Link>;
}
