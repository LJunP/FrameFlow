# syntax=docker/dockerfile:1.7

ARG NODE_IMAGE=node:22.17.0-bookworm-slim

FROM ${NODE_IMAGE} AS dependencies
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

FROM dependencies AS build
COPY . .
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

FROM ${NODE_IMAGE} AS production-dependencies
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --omit=dev && npm cache clean --force

FROM ${NODE_IMAGE} AS runtime
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    HOSTNAME=0.0.0.0 \
    PORT=3000
WORKDIR /app
COPY --from=production-dependencies --chown=node:node /app/node_modules ./node_modules
COPY --from=build --chown=node:node /app/.next ./.next
COPY --from=build --chown=node:node /app/package.json /app/package-lock.json /app/next.config.mjs ./

# 版本标签放在稳定依赖/产物复制之后，重标记镜像不再重跑 npm 层。
ARG VERSION=0.1.0
ARG VCS_REF=unknown
ARG SOURCE_URL=https://example.invalid/frameflow-select
LABEL org.opencontainers.image.title="FrameFlow Select web" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.source="${SOURCE_URL}"

# ★ 核心：Next 只在服务端读取 API_BASE，浏览器始终请求同源 BFF；同时以 node 用户运行。
# 若把内部 API 地址改成 NEXT_PUBLIC_*，会泄露拓扑并绕开 HttpOnly refresh-cookie 边界。
USER node
EXPOSE 3000
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
  CMD node -e "fetch('http://127.0.0.1:3000/').then(r=>{if(!r.ok)process.exit(1)}).catch(()=>process.exit(1))"
CMD ["npm", "run", "start"]
