/** @type {import("next").NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // ★ 隔离 dev 与 build 的缓存目录：dev 用 .next-dev、build 用 .next。
  // 二者共用 .next 时，dev 运行期间执行 build 会覆盖正在服务的编译产物，
  // 造成 "Cannot find module './xxx.js'" 运行时错误（本次事故根因，
  // 已两次触发）。隔离后互不干扰。
  distDir: process.env.NEXT_DIST_DIR ?? '.next',
};

export default nextConfig;
