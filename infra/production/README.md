# production：独立 VPS 的人工发布模板

本目录只提供可审计模板。购买 VPS、SSH、DNS、证书签发、真实发布、生产恢复都由
所有者以后亲手执行。production 必须使用独立宿主、GitHub `production`
Environment 人工审批、staging 已验证的相同 Digest，以及 F10 的监控/备份门禁。

脚本默认 dry-run；即使传 `--apply`，也必须同时提供精确环境和确认短语。当前本地
工程完成不代表 production 已发布或已恢复演练。

真实 `.env` 使用 bootstrap 建立的 `/etc/frameflow/production/frameflow.env`，安装为
`root:frameflow 0640`，不进仓库；F10 systemd 备份任务只接受这套路径与权限模型。
