# Nginx / HTTPS 模板

dev 与 staging 默认共用一台非生产 VPS，因此 Nginx 运行在宿主机并独占 80/443；
两套 Compose 只把各自 Web/MinIO 暴露到不同 loopback 端口。这样既能按域名分流，
又不会把两个环境的 Docker network 连在一起。

首次证书流程（以后由所有者在 VPS 亲手执行）：

1. 先为应用域名和媒体域名配置 DNS；
2. `render-nginx-config.sh --mode bootstrap ... --apply`，经 `nginx -t` 后 reload；
3. `request-certificate.sh ... --apply --confirm ISSUE_CERTIFICATE_<ENV>`；
4. 用 `--mode https` 重新渲染并 `nginx -t`；
5. 安装本目录 systemd timer，续期脚本每次先 `certbot renew`，成功后才 reload。

502 排查顺序：域名是否进本机 → `nginx -t` → loopback upstream 是否监听 →
`docker compose ps`/容器 health → Web 的 `API_BASE` → app health 与依赖 health → 日志。
媒体上传若是 403 `SignatureDoesNotMatch`，优先检查 `FRAMEFLOW_MEDIA_DOMAIN`、
`FRAMEFLOW_STORAGE_PUBLIC_ENDPOINT`、代理 `Host` 和 URI 是否完全一致，而不是按 502 查。
