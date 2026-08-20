# FrameFlow Select 打包政策

正式源码包必须排除：

```text
.git/
**/target/
**/node_modules/
**/.next/
**/__pycache__/
.venv/
.zcode/
.DS_Store
__MACOSX/
data/jwt/*.pem
.env
真实视频与派生媒体
模型缓存
```

必须包含：源码、迁移、OpenAPI/Schema、Lockfile、示例环境、运行脚本、测试说明、Manifest、SHA-256 和已知限制。
