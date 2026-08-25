# AuthBridge UUID 协议

本文定义 `cc-web` 到 `ChampionshipsAuthBridge` 的 UUID 字段契约。AuthBridge 是可选的密码、准入和迁移同步组件；它不读取 Core 的 `identity.mode`，也不替网站猜测 UUID。

## 1. 普通账户事件

`GET /api/internal/bridge/changes`、`/snapshot` 和非迁移控制任务中的每名玩家都必须携带：

| 字段 | 规则 |
| --- | --- |
| `username` / `authmeUsername` | `[A-Za-z0-9_]{3,16}`，用于离线算法和 AuthMe 账号名。 |
| `uuidSource` | 只能是 `UUID`、`OFFLINE` 或 `ONLINE`。缺失或未知值必须拒绝整条需要 UUID 的事件。 |
| `minecraftUuid` | 仅 `uuidSource=UUID` 必须提供，支持带连字符或无连字符的合法 UUID。 |
| `passwordHash` | 仅密码下发事件必须提供；格式必须是 BCrypt。 |

三种来源的语义固定如下：

- `UUID`：直接校验并使用 `minecraftUuid`。这是 cc-web 当前有效身份（例如网站统一 UUID）。
- `OFFLINE`：按事件用户名计算 `UUID.nameUUIDFromBytes("OfflinePlayer:" + username)`。
- `ONLINE`：只请求 Mojang `/users/profiles/minecraft/<username>`，校验返回名称和 UUID；404、网络故障、格式错误和名称不一致均失败，绝不回退为离线 UUID。

因此，`OFFLINE`/`ONLINE` 表示网站绑定记录保存的服务器原始身份，`UUID` 表示网站已经确定的当前有效身份。它们不能互换，也不能把 `SERVER_UUID`、`CUSTOM_UUID` 作为 Bridge 的 `uuidSource`。

## 2. 事件应用和确认

Bridge 按 outbox 游标顺序应用事件。`PROVISION`、`USERNAME_UPDATED` 和 `WHITELISTED` 先完成 UUID 解析；`PASSWORD_UPDATED` 只按已有 AuthMe 玩家名更新 BCrypt 密码，不读取或推导 UUID，也不会创建缺失的 AuthMe 账号。`REVOKED`、封禁和解封事件不需要重新解析 UUID。任何字段缺失或解析失败都不推进游标。

`POST /api/internal/bridge/ack` 只接受 `{ "through": "<cursor>" }`，只确认已应用的事件游标，不接受 `serverUuids`，也不触发网站 UUID 回写。Bridge 在本地持久化待确认游标，网络恢复后重试，保证确认幂等。

## 3. 身份迁移控制任务

`IDENTITY_MODE_MIGRATION` 不使用普通事件的名称推导。cc-web 创建任务时为每名玩家冻结 `fromUuid` 和 `toUuid`，并将完整映射写入任务 payload。Bridge 必须：

1. 确认服务器无人在线并进入维护锁；
2. 校验每个账户 ID、用户名、`fromUuid` 和 `toUuid`，拒绝重复目标 UUID；
3. 将冻结值原样迁移到 Core、AuthMe 和本地访问状态；
4. 全部成功后提交控制任务，失败则报告失败并保留任务状态供重试。

任务重试必须复用原映射，不能按改名后的用户名重新计算。网站只有收到成功确认后才切换自己的 `SERVER_UUID`/`CUSTOM_UUID` 模式。

## 4. AuthProxy 边界

AuthProxy 仅用于 cc-web 管理的 `PROFILE_UUID`/统一 UUID 登录部署。它从 `/login-profile/<username>` 获取当前有效 UUID，并在预登录阶段拒绝未绑定、撤销或封禁账户；缺少 UUID 时拒绝登录，不生成离线 UUID。皮肤和签名档案仍由 cc-web 独立查询 Mojang，不参与 AuthBridge UUID 解析。

## 5. 测试和验收

必须覆盖：离线算法与 Vanilla 一致、`UUID` 缺失/非法、Mojang 404 不回退、Mojang 名称不匹配、确认重试幂等、迁移使用冻结映射，以及同一账户在 AuthProxy、Yggdrasil、Paper `Player#getUniqueId()` 中的 UUID 一致性。
