# 前端开发规范

> 状态：DECISION（ADR-012 配套文档）。前端技术选型、阶段计划和约束的工程规范。

## 1. 技术选型（ADR-012）

```text
语言：TypeScript
框架：React
元框架：Next.js（App Router + BFF）
样式：Tailwind CSS
组件：shadcn/ui
HTTP：TanStack Query（缓存/重试/loading）
状态管理：Zustand（轻量）
类型生成：openapi-typescript（唯一选择，从后端 OpenAPI YAML 生成 TS 类型）
```

版本策略：路线只锁定技术方向。任务派发当日核验官方支持窗口，在 Task Capsule 中记录 Node.js/包管理器与核心依赖的精确版本，并由 `package.json` + lockfile 固定；不使用 `latest`、`next` 或无 lockfile 派发。

## 2. 项目结构

前端位于本源码单仓库 `frameflow-web/`，不加入 Maven reactor，但共享同一 Git、CI、Task Capsule 与 Evidence 边界。推荐结构：

```text
frameflow-web/
  ├─ app/                      # Next.js App Router
  │   ├─ (auth)/               # 认证相关页面（登录/注册）
  │   ├─ (dashboard)/          # 主面板布局
  │   │   ├─ teams/            # 团队管理
  │   │   ├─ projects/         # 项目与 Brief
  │   │   ├─ tasks/            # 任务与评论
  │   │   ├─ assets/           # 素材版本
  │   │   ├─ reviews/          # 审核流
  │   │   └─ deliveries/       # 交付包
  │   └─ layout.tsx            # 根布局
  ├─ components/               # 复用组件（shadcn/ui 源码在此）
  ├─ lib/
  │   ├─ api/                  # API 客户端（fetch 封装 + TanStack Query）
  │   ├─ types/                # openapi-typescript 生成的类型
  │   └─ stores/              # Zustand stores
  ├─ public/
  ├─ package.json              # 核心依赖精确版本
  ├─ pnpm-lock.yaml 或选定包管理器的 lockfile
  ├─ tsconfig.json
  ├─ next.config.js
  └─ tailwind.config.ts
```

## 3. 与后端对接

### 3.1 BFF 与 Token 边界

- Browser 只请求同源 Next BFF；BFF 再请求后端 `http://127.0.0.1:8080/api/v1`。
- Refresh Token 只能由 BFF 写入 `Secure`/`HttpOnly`/`SameSite` Cookie，禁止写入 localStorage、sessionStorage、IndexedDB、页面状态、日志或前端错误上报。
- BFF 在服务端执行 Access Token 附加与 Refresh Token 轮换；前端 JavaScript 不持有 Refresh Token。状态变更路由必须有 CSRF 防护。
- 每次 HTTP attempt 的 `X-Request-Id` 由后端生成/覆盖；前端不生成或重用该值，只把响应值用于当次报错。
- 多次请求需归入同一业务意图时，使用独立 `X-Correlation-Id`；其长度、字符集、是否由 BFF 生成及 OpenAPI 声明尚待 M02 Contract Gate 批准。
- 高风险写操作携带 `Idempotency-Key`（UUID）
- 并发更新携带 `version` 或 `If-Match`

### 3.2 类型生成

从后端权威契约 `docs/04-api/openapi/frameflow-v1.yaml` 生成 TypeScript 类型：

`openapi-typescript` 必须作为精确锁版本的本地 devDependency，并由脚本统一执行：

```json
{
  "scripts": {
    "generate:api": "openapi-typescript ../docs/04-api/openapi/frameflow-v1.yaml -o lib/types/api.d.ts"
  }
}
```

运行 `npm run generate:api`（或 Task 派发时选定包管理器的等价命令）；禁止用未锁版本的临时 `npx` 下载改变生成结果。

不手写重复类型定义。后端契约变更后重新生成。

### 3.3 CORS

浏览器与 Next BFF 保持同源，不直接依赖 Spring Boot CORS 来保护 Token。本地 BFF 到后端的服务端请求不使用浏览器 CORS；若未来允许浏览器直连 API，必须另立安全 ADR/任务。

## 4. 阶段计划

| 阶段 | 前置 | 目标 | 关键页面 |
|---|---|---|---|
| M01-F / FF-M01F-001 | `FF-M01H-001=DONE` | 前端基座与认证界面 | BFF、登录/注册、团队列表/创建/成员管理 |
| M04-F / FF-M04F-001 | M01-F + M02/M03/M04-A DONE | 核心业务界面 | 项目/Brief 版本、任务/评论、素材版本上传与浏览 |
| M08-F / FF-M08F-001 | M04-F + M07/M08 DONE | 协作与交付界面，product MVP gate | 审核流、时间码批注、基础通知、交付包、客户确认、全栈 E2E |

## 5. 不做

```text
浏览器内视频剪辑器
实时协同编辑
移动端 App
复杂炫酷动画/3D 渲染
```

## 6. 前端不做 RBAC 信任

- 前端只做 UI 层展示控制（隐藏/禁用按钮），不做安全决策。
- 所有权限以后端返回为准；前端不缓存角色判断结果。
- 资源不存在或无权访问统一显示 404 语义（与后端一致，防枚举）。

## 7. 证据要求

前端阶段同样遵循证据规则：

- 构建命令与原始输出
- 页面截图或交互录屏
- API 对接验证记录
- 证据编号与任务包 JSON 的 `evidenceId` 一致

## 8. 任务包

前端阶段任务包遵循同一 Task Capsule v2 规范。任务文件路径：

```text
docs/05-engineering/tasks/M01F/FF-M01F-001.json
docs/05-engineering/tasks/M04F/FF-M04F-001.json
docs/05-engineering/tasks/M08F/FF-M08F-001.json
```

创建任务包时需定义 `writeSet`（前端项目路径）、`allowedCommands`（npm/pnpm 命令白名单）和 `techBoundary`。
