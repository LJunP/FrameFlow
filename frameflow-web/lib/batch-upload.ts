/** 浏览器上传上限：单文件不超过 200 MiB（后端对象上限 5GB，这是浏览器侧的保守体验上限）。 */
export const MAX_BROWSER_UPLOAD_BYTES = 200 * 1024 * 1024;

// ★ 核心：与后端 multipart-threshold（默认 32MB）对齐——超过此值登记 simpleOnly:false，
// 后端才会发放 MULTIPART 会话；阈值不一致会出现"前端说分片、后端给单传"的错配。
export const SIMPLE_UPLOAD_THRESHOLD_BYTES = 32 * 1024 * 1024;

export function browserUploadError(file: { size: number; type: string }): string | null {
  if (file.size <= 0) return '不能上传空文件';
  if (file.size > MAX_BROWSER_UPLOAD_BYTES) return '浏览器暂支持不超过 200 MiB 的单文件';
  if (file.type && !file.type.startsWith('video/')) return '请选择视频文件';
  return null;
}

/** 过滤出浏览器侧允许排队的视频文件；非法文件由调用方单独提示。 */
export function collectVideoFiles<T extends { name: string; size: number; type: string }>(
  files: ArrayLike<T>,
): { accepted: T[]; rejected: { name: string; reason: string }[] } {
  const accepted: T[] = [];
  const rejected: { name: string; reason: string }[] = [];
  for (let i = 0; i < files.length; i += 1) {
    const file = files[i];
    const reason = browserUploadError(file);
    if (reason) rejected.push({ name: file.name, reason });
    else accepted.push(file);
  }
  return { accepted, rejected };
}
