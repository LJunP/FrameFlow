# BOOK-05:Linux、Docker、服务器部署与运维

## 1. 四套逻辑隔离环境
- local:个人电脑,编码/单元测试/快速调试/本地 Compose/本地集成测试;
- dev:非生产 Linux 服务器,联调/持续部署/功能验证;
- staging:尽量模拟生产,Release Candidate/迁移验证/完整 E2E/上线前验收/回滚验证;
- production:单独生产服务器,真实用户/真实数据/正式域名/HTTPS/监控/备份/告警。

默认拓扑:1 台 Non-production VPS(Dev + Staging,Compose Project/Network/Volume/DB/Secret 完全隔离)+ 1 台 Production VPS。预算不足时可先用一台完成 Dev+Staging,但 Production Gate 不得在与非生产环境共用宿主机时宣布通过。

每套环境必须隔离:数据库、Redis、RabbitMQ VHost、MinIO Bucket、Docker Network、Docker Volume、环境变量、Secret、域名、日志、备份。禁止跨环境共用生产数据与 Secret。

## 2. Linux 手工能力(HUMAN_CORE)
服务器选择、SSH Key、首次登录、非 root 用户、sudo、禁用 root 密码登录、SSH 安全、防火墙、时区、CPU/内存/磁盘检查、ps/top/free/df/du/ss/curl/grep/tail/journalctl、目录与权限处理。Agent 不得要求或读取服务器密码/私钥。

## 3. Docker 能力(HUMAN_CORE)
安装 Docker Engine、理解镜像与容器、第一个 Dockerfile、第一个 Compose、Network、Volume、Healthcheck 与 Restart Policy、资源限制、非 root 容器、日志、停止与恢复、镜像回滚。第一次 Docker 与 Compose 部署不得由 Agent 通过远程 SSH 自动执行。

## 4. 中间件手工部署(HUMAN_CORE,首次)
PostgreSQL、Redis、RabbitMQ、MinIO 的首次部署与验证:网络、端口、用户、权限、持久化、健康检查、容器重启、数据保留。

## 5. Nginx、域名、HTTPS(HUMAN_CORE)
安装 Nginx、反向代理、绑定域名、DNS、HTTPS 证书、自动续期、80/443 检查、502 排查、证书错误处理。

## 6. CI/CD(HUMAN_CORE 亲手建立第一条)
PR 到 测试 到 构建镜像 到 推送 Registry 到 Dev 部署 到 Smoke Test;Production 发布人工审批;后续加入 Staging 晋级与回滚。

## 7. 监控、日志、备份、故障演练(HUMAN_CORE)
结构化日志、Spring Actuator、Micrometer、Prometheus、Grafana、日志聚合与告警;PostgreSQL 与 MinIO 备份/恢复;必须演练:Redis 宕机、RabbitMQ 积压、Worker 崩溃、数据库不可连接、Nginx 502、磁盘不足、错误版本发布、数据库恢复、对象存储恢复、镜像回滚;每次演练形成事故复盘。
