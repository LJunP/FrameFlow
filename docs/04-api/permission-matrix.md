# 权限矩阵（M01 Ready Gate 输入）

> 状态：DECISION。角色模型定稿：团队角色与项目角色分离，CLIENT 是项目级角色。

## 1. 角色模型（定稿）

```text
团队角色（team_members.role）：OWNER / OPERATOR / REVIEWER / VIEWER
项目角色（project_members.role）：PROJECT_MANAGER / CONTRIBUTOR / VIEWER / CLIENT
```

- CLIENT 是项目级外部角色，只访问被明确授权的项目内容；
- 团队 OWNER 自动获得团队内项目的 PROJECT_MANAGER 语义（按需明确）；
- 非项目成员不可读取或修改项目资源。
- M01 Access Token 只标识用户，不携带团队角色；登录后由 `GET /teams` 返回当前用户的 ACTIVE 团队成员关系，客户端选定团队后使用路径中的 `teamId`，服务端以数据库成员关系做实时授权。

## 2. 团队级权限矩阵（M01）

创建团队不依赖调用者已经拥有某个团队角色：任一 `users.status=ACTIVE` 的已认证用户都可调用 `POST /teams`，服务端在同一事务中创建团队并把创建者写为首名 OWNER。

| 操作 | OWNER | OPERATOR | REVIEWER | VIEWER |
|---|---|---|---|---|
| 发现自己加入的有效团队 | ✅ | ✅ | ✅ | ✅ |
| 查看团队与成员 | ✅ | ✅ | ✅ | ✅ |
| 直接添加已注册成员 | ✅ | ❌ | ❌ | ❌ |
| 修改成员角色 | ✅ | ❌ | ❌ | ❌ |
| 移除成员 | ✅ | ❌ | ❌ | ❌ |

M01 不提供删除团队 API，也不实现邀请接受流程。添加成员立即产生 ACTIVE 关系；INVITED 状态待未来完整邀请流程另行定义。团队必须始终至少有一名 ACTIVE OWNER：不得降级或移除最后一名 OWNER；OWNER 不得通过成员删除接口移除自己。

## 3. 项目级权限矩阵（M02 起生效，M01 预定义）

| 操作 | PROJECT_MANAGER | CONTRIBUTOR | VIEWER | CLIENT |
|---|---|---|---|---|
| 查看项目内容 | ✅ | ✅ | ✅ | 仅授权内容 |
| 创建/修改 Brief | ✅ | ✅ | ❌ | ❌ |
| 创建/分配任务 | ✅ | ✅ | ❌ | ❌ |
| 更新自己被分配的任务 | ✅ | ✅ | ❌ | ❌ |
| 上传/创建素材版本 | ✅ | ✅ | ❌ | ❌ |
| 提交审核/返工 | ✅ | ✅ | ❌ | ❌ |
| 批注/时间码评论 | ✅ | ✅ | ✅ | ✅（仅授权内容） |
| 要求修改 | ✅ | ✅ | ❌ | ✅ |
| 确认交付 | ✅ | ✅ | ❌ | ✅ |
| 锁定交付版本 | ✅ | ❌ | ❌ | ❌ |

## 4. 未认证/无权语义（定稿，禁止实现者二选一）

| 场景 | HTTP | 错误码 |
|---|---|---|
| 未认证或 Token 无效/过期 | 401 | AUTH_REQUIRED / TOKEN_EXPIRED |
| 已认证但角色不足（明确的操作权限） | 403 | FORBIDDEN |
| 资源不存在或无权访问（防枚举） | 404 | RESOURCE_NOT_FOUND / PROJECT_NOT_FOUND |

规则：**资源级访问（读、改、删某个资源）无权一律 404 隐藏存在性；操作级权限（添加成员、改角色、锁定等动作）不足返回 403。**

## 5. 校验位置

- MVP：应用 Spring Security Filter 校验 Token；微服务阶段可由 Gateway 增加入口校验，但不能替代业务服务校验；
- Identity 服务：`GET /teams` 只按 Token 的 `sub` 查询该用户的 ACTIVE 成员关系；不接受任意 `userId` 查询，也不返回 REMOVED 关系；
- 各服务：按路径 `teamId` 在本地重新校验资源归属和数据库角色（不信任 Token claims 或 Gateway 传递的权限结论）；
- 项目成员判定由 project-service 的授权 API/投影提供，不是 identity-service。
