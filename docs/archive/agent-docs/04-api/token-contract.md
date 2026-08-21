# Token 契约（M01 Ready Gate 输入）

> 状态：DECISION。M01 派发前必须与本文件一致；实现不得随意更改签名、claims 或过期策略。

## 1. 类型

- Access Token：RS256 JWT，短期；使用 Spring Security 6 / Nimbus 的标准 `JwtEncoder`、`JwtDecoder`，不自定义密码学实现；
- Refresh Token：32 字节（256 bit）安全随机数生成的不透明 Base64URL 字符串（无 padding），长期、可撤销，**不得使用 JWT**；
- MVP 支持多端登录（每端独立 Refresh Token），不做单端强制下线。

## 2. Access Token

| 项 | 值 |
|---|---|
| 算法 | 所有环境统一 RS256；RSA 密钥至少 2048 bit；JWT header 必须包含 `alg=RS256` 与非空 `kid` |
| TTL | 15 分钟 |
| Claims | `sub`(userId)、`iat`、`exp`、`iss=frameflow`、`aud=frameflow-api`、`jti` |
| clock skew | 服务端容忍 30 秒 |
| 撤销 | 无状态（依赖 TTL）；安全事件时用 `jti` 黑名单（Redis 后置，MVP 可仅 TTL） |

M01 Access Token 是用户身份令牌，不绑定当前团队，也不携带团队角色。客户端登录后通过 `GET /teams` 发现自己的团队，再将选中的 `teamId` 放入团队资源路径；服务端每次依据数据库中的有效成员关系校验角色，避免成员角色变化后旧 Token 继续携带过期权限。

私钥只供签发端使用，公钥供校验端使用。密钥通过环境变量或 Secret 注入，禁止提交到仓库、写入数据库或在启动时临时生成。轮换时签发端切换到新 `kid`，校验端至少保留旧公钥至旧 Access Token 的 `15 分钟 + 30 秒 clock skew` 全部失效。

## 3. Refresh Token

| 项 | 值 |
|---|---|
| TTL | 每次签发 30 天；成功轮换产生的新 token 重新计算 30 天 |
| 生成 | `SecureRandom` 生成 32 字节随机值，Base64URL 无 padding 编码 |
| 存储 | 仅存 SHA-256 小写十六进制哈希；随机值具有 256 bit 熵，因此可用确定性哈希安全索引查询；明文只在签发响应中出现一次 |
| 轮换 | 每次刷新轮换；旧 token 立即失效（防重放） |
| 撤销 | 登出撤销当前设备的整个 token family；安全事件可撤销用户的全部 family |

每次登录创建一个新的 `family_id`，同一设备后续轮换沿用该 family。刷新必须在数据库事务中锁定当前 ACTIVE 记录：插入新记录后将旧记录标记为 ROTATED 并关联替代记录。已 ROTATED 或 REVOKED 的 token 再次出现视为重放，拒绝请求并撤销该 family 的所有 ACTIVE token。表结构以 `identity-data-dictionary.md` 的 `refresh_token_sessions` 为准。

## 4. 端点契约

| 端点 | 请求 | 响应 | 错误 |
|---|---|---|---|
| POST /auth/register | email、password、displayName | 201 + user | VALIDATION_FAILED、409 重复 |
| POST /auth/login | email、password | 200 + accessToken、refreshToken、expiresIn | 401 AUTH_REQUIRED |
| POST /auth/refresh | refreshToken | 200 + 新 token 对 | 401 AUTH_REQUIRED / TOKEN_EXPIRED |
| POST /auth/logout | refreshToken（唯一凭据，不要求 Access Token） | 204 | 401 AUTH_REQUIRED / TOKEN_EXPIRED |
| GET /auth/me | Authorization | 200 + user | 401 |
| GET /teams | Authorization | 200 + 当前用户的有效团队成员关系 | 401 |

## 5. 安全要求

- 密码不出现在响应、日志、Token claims；
- 响应头不使用敏感字段传 token（JSON body 或 HttpOnly cookie 二选一，MVP 用 JSON body）；
- Token 校验失败区分：格式错误（401 AUTH_REQUIRED）、过期（401 TOKEN_EXPIRED）；
- `/auth/refresh` 与 `/auth/logout` 都只校验请求 body 中的 Refresh Token，不要求仍有效的 Access Token；Access Token 过期不能阻止用户撤销当前设备会话；
- 签名密钥只通过环境变量/Secret 注入，禁止入库和提交；
- Refresh Token 明文禁止写入日志、数据库、Trace、指标标签和错误响应；
- `iss`、`aud`、`alg`、`kid` 必须全部校验，不能只验证签名和过期时间。

## 6. 测试要求

- TTL 过期后请求被拒；
- refresh 轮换后旧 token 不可用；
- 已轮换 refresh token 重放后，该设备 family 的新 token 也被撤销；
- 登出后 refresh 不可用；
- 登录后可通过 `GET /teams` 发现当前用户的有效团队，且不能发现其他用户或已移除的团队关系；
- clock skew 边界测试；
- 错误码与 permission-matrix 一致。
