# main 主线统一记录

所有者于 2026-09-13 明确授权解除 main 冻结、更新规则，将最新完整代码统一到 main 并清理旧分支。

## 迁移前引用

```text
refs/heads/archive/frameflow-select-agent-mvp-v1 6bbb13423548f93462dd5d0bba7488ece60dd3aa
refs/heads/feature/f1-工程基线 ecf531e157c998ee48663432de03133ce0a2b61e
refs/heads/feature/f2-项目与质检配置 44542d2ccb871f4f2407affebf8207dcf24d1834
refs/heads/feature/f3-批次与视频上传 0a34647d16dac9970abafee5981cdc64abb0d7f4
refs/heads/feature/f4-确定性质检流水线 719749c3fdacbe1a727f58973ec70b589ce49f02
refs/heads/feature/f5-缓存与限流 a691fed5a53f64acb9638ca9daa383610700bf2c
refs/heads/feature/f6-语义质检 c6b71ce2d07773cb02ad4347821ad504b3fd745b
refs/heads/feature/f7-聚类排名与优选 cf9e1317d6d9d6aeffeb7ed6ef2409017c21e6d7
refs/heads/feature/f8-ui-polish 2a97e486b036459a7b67b9003943ecafb4ad3fec
refs/heads/feature/f8-前端产品化 32564ee4e3101ff9d86c51571d0b52edc437c360
refs/heads/frameflow-select/learning-main 420bec27ee5fd92e82b09815b0e667baa1b85bf9
refs/heads/main 2356fe5b2328f136bb70f0d4f8c618748289d9f0
refs/remotes/origin/codex/ff-m01-001 517ed72e5d74cda049c52beeff6ab4e718a834b8
refs/remotes/origin/feat/ff-p0-001 d2a6860d2887a7e5e2efcd5491c7a1f0d78e095a
refs/remotes/origin/frameflow-select/learning-main 420bec27ee5fd92e82b09815b0e667baa1b85bf9
refs/remotes/origin/main 621e82fb2d048959f55b15bbdd726c9af12015f0
```

## 处理策略

- 远端 main 快进到当前完整主线，再纳入分支规则与 CI 配置更新，不强推、不改写历史。
- 旧远端 main、P0、M01、旧开发主线和两个本地冻结说明位置以 archive/2026-09-13/* 标签保留。
- feature 分支均已完整包含在当前主线；验证通过后，本地和远端仅保留 main 分支。
- 既有历史标签及历史 Evidence 保持原样；归档标签不代表新版本发布。
- CI、镜像发布分支门禁、交付来源校验与真实 Provider 门禁脚本统一为 main。Provider 调用仍需独立预算授权。
