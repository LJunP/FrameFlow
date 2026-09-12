/** 浏览器当前支持单文件直传；后端另以 simpleOnly 按实际配置检查。 */
export const MAX_BROWSER_UPLOAD_BYTES = 32 * 1024 * 1024;

export function browserUploadError(file: { size: number; type: string }): string | null {
  if (file.size <= 0) return '不能上传空文件';
  if (file.size > MAX_BROWSER_UPLOAD_BYTES) return '浏览器暂支持不超过 32 MiB 的单文件；大文件请使用分片上传 API';
  if (file.type && !file.type.startsWith('video/')) return '请选择视频文件';
  return null;
}
