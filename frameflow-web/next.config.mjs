/** @type {import("next").NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // ★ 核心：固定自托管构建的 tracing root，避免 Next.js 因用户目录中
  // 的其他 lockfile 误把整个上层目录当作 workspace，导致追踪结果漂移。
  outputFileTracingRoot: process.cwd(),
  // ★ 隔离 dev 与 build 的缓存目录：dev 用 .next-dev、build 用 .next。
  // 二者共用 .next 时，dev 运行期间执行 build 会覆盖正在服务的编译产物，
  // 造成 "Cannot find module './xxx.js'" 运行时错误（本次事故根因，
  // 已两次触发）。隔离后互不干扰。
  distDir: process.env.NEXT_DIST_DIR ?? '.next',
};

export default nextConfig;
