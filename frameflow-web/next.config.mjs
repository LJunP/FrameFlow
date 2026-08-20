import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Keep the workspace self-contained: trace file output within frameflow-web
  // so Next does not infer a parent workspace root from unrelated lockfiles.
  outputFileTracingRoot: path.join(__dirname),
};

export default nextConfig;
