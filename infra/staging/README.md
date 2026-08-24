# staging：人工晋级与回滚验证环境

只接收 dev 已 Smoke 通过的同一组 Digest，不重新 build。与 dev 共宿主时使用独立
Compose project 和 `23000/29000` loopback 端口。发布前必须执行环境矩阵校验、
Smoke、一次回滚再前滚；不能把合成验证当生产证明。

真实 `.env` 使用 bootstrap 建立的 `/etc/frameflow/staging/frameflow.env`，安装为
`root:frameflow 0640`，不进仓库；F10 定时备份复用同一路径。
