# AuthBridge 与 AuthProxy 同步协议

本文定义 `cc-web`、`ChampionshipsAuthBridge` 与 `ChampionshipsAuthProxy` 的身份、密码、封禁、ACK 和控制任务契约。AuthBridge 是可选的密码、准入和迁移同步组件；它不读取 Core 的 `identity.mode`，也不替网站猜测 UUID。AuthProxy 只做预登录准入与 UUID 转发。

## 1. 客户端鉴权与角色

所有内部接口使用同一 HMAC-SHA256 规范字符串：

```text
METHOD
PATH
TIMESTAMP
REQUEST_ID
SHA256_BODY
```

请求必须携带 `X-CC-Key-Id`、`X-CC-Timestamp`、`X-CC-Request-Id` 和 `X-CC-Signature`。时间戳误差不超过 60 秒；请求 ID 在重放窗口内不得重复。PATH 只包含不含 query 的路由路径，query 参数由具体接口定义。

cc-web 通过 `BRIDGE_CLIENT_KEYS` 支持多个并发客户端：

```json
[
  {"keyId":"core-a","secret":"...","role":"AUTHBRIDGE_WRITER"},
  {"keyId":"core-b","secret":"...","role":"AUTHBRIDGE_WRITER"},
  {"keyId":"proxy-a","secret":"...","role":"AUTH_PROXY_READER"}
]
```

- `AUTHBRIDGE_WRITER`：Paper/AuthBridge 写入端，可访问 `/changes`、`/snapshot`、`/ack` 与控制任务。
- `AUTH_PROXY_READER`：Bungee/AuthProxy 读取端，可访问 `/login-profile`、`/proxy-ban-snapshot` 与 `/proxy-changes`。
- `LEGACY_FULL`：未配置 `BRIDGE_CLIENT_KEYS` 时由旧 `BRIDGE_KEY_ID` + `BRIDGE_HMAC_SECRET` 兜底，保留两类权限。

每个 `keyId` 必须唯一，secret 至少 32 字节。客户端从配置移除后，数据库记录只停用不删除。`BRIDGE_HMAC_SECRET` 除旧兼容模式外，仍是 cc-web 多个配置加密用途的根密钥，不能因配置多客户端而删除。

## 2. 普通账户事件

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

## 3. 事件应用和确认

每个 writer 按同一 outbox 顺序应用事件。`PROVISION`、`USERNAME_UPDATED` 和 `WHITELISTED` 先完成 UUID 解析；`PASSWORD_UPDATED` 只按已有 AuthMe 玩家名更新 BCrypt 密码，不读取或推导 UUID，也不会创建缺失的 AuthMe 账号。`REVOKED`、封禁和解封事件不需要重新解析 UUID。任何字段缺失或解析失败都不推进本地游标。

`POST /api/internal/bridge/ack` 只接受 `{ "through": "<cursor>" }`，只确认当前 keyId 已应用的事件游标，不接受 `serverUuids`，也不触发网站 UUID 回写。Bridge 在本地持久化待确认游标，网络恢复后重试，保证确认幂等。cc-web 为每个 writer 保存独立服务端游标；`sync_outbox` 的全局 `ACKNOWLEDGED` 只推进到所有启用 writer 游标的最小值。

## 4. 身份迁移控制任务

`IDENTITY_MODE_MIGRATION` 不使用普通事件的名称推导。cc-web 创建任务时为每名玩家冻结 `fromUuid` 和 `toUuid`，将完整映射写入任务 payload，并为每个当前启用 writer 创建一个 target。每个 writer 只能领取分配给自己的 target，并且必须：

1. 确认本服务器无人在线并进入维护锁；
2. 校验每个账户 ID、用户名、`fromUuid` 和 `toUuid`，拒绝重复目标 UUID；
3. 将冻结值原样迁移到本实例 Core、AuthMe 和本地访问状态；
4. 全部成功后提交自己的 target，失败则报告失败并保留任务状态供重试。

任一 target 失败则父任务为 `FAILED`；全部 target 成功后 cc-web 才切换自己的 `SERVER_UUID`/`CUSTOM_UUID` 模式。任务重试复用原映射，并重置/补齐当前启用 writer 的 target，不能按改名后的用户名重新计算。

## 5. 未获准账号清理

`AUTHME_REMOVE_UNKNOWN` 的权威允许名单是 cc-web 当前所有未撤销绑定。cc-web 为每个当前启用 writer 下发相同的账户集合，并携带 `accountId`、`username`、`uuidSource` 和必要的 `minecraftUuid`。writer 必须先完整解析并校验集合：用户名、账户 ID 与有效 UUID 不得重复，任何 UUID 解析失败都不得修改数据。

每个 writer 随后独立执行：

1. 调用 Core 事件，把不在有效 UUID 名单中的玩家行在同一个数据库事务内删除；
2. 清理 AuthMe 中不在用户名名单中的登录账号；
3. 保留 AuthBridge 本地状态中仍在名单内的身份，其余删除；
4. 向自己的 target 返回 AuthMe 与 Core 的检查/删除明细。

Core 清理范围固定为 `players`、`team_members`、`player_points`、`daily_player_stats`、`daily_match_results`、`daily_player_records`、`daily_map_player_stats`、`daily_pkw_records`，只按 UUID 精确删除。Core 事务失败必须整体回滚；AuthMe 与本地状态是后续步骤，失败后任务可重试收敛。cc-web 网站账号、网站资料和审计记录不参与该清理。若已获准玩家的旧 UUID 历史数据未先迁移到当前有效 UUID，旧 UUID 会被视为未获准并删除。

## 6. AuthProxy 边界

AuthProxy 仅用于 cc-web 管理的 `PROFILE_UUID`/统一 UUID 登录部署。它从 `/login-profile/<username>` 获取当前有效 UUID，并在预登录阶段拒绝未绑定、撤销、封禁和维护中的账户；缺少 UUID 时拒绝登录，不生成离线 UUID。皮肤和签名档案仍由 cc-web 独立查询 Mojang，不参与 AuthBridge UUID 解析。

启动时使用 `/proxy-ban-snapshot` 获取有效封禁、显式 `maintenance` 状态和当前 outbox 游标；之后轮询 `/proxy-changes?after=<cursor>`，只接收 `BANNED`、`UNBANNED`、用户名、原因、到期时间与 `maintenance`。该流不包含密码哈希，proxy key 也不能调用 writer ACK。每个 proxy key 的服务端只读游标独立推进；插件本地仍保留自己的 `ban-state.properties` 游标，避免 Web 短暂故障时丢失已知封禁状态。

存在 pending 或 failed 的 `IDENTITY_MODE_MIGRATION` 时，预登录返回 `MAINTENANCE`，snapshot 与 proxy stream 也必须显式返回 `maintenance:true`。旧版缺少该字段的服务应被插件视为响应不完整。

## 7. 测试和验收

必须覆盖：离线算法与 Vanilla 一致、`UUID` 缺失/非法、Mojang 404 不回退、Mojang 名称不匹配、writer ACK 重试幂等、落后 writer 不推进全局确认、proxy key 不能读取密码或 ACK、多个 writer 分别领取和回报控制 target、迁移失败不切换 UUID 模式、未获准清理先校验完整允许名单并按 UUID 单事务删除 Core 数据、维护状态拒绝登录，以及同一账户在 AuthProxy、Yggdrasil、Paper `Player#getUniqueId()` 中的 UUID 一致性。

## 8. DAILY 排行榜快照

`POST /api/internal/bridge/leaderboards` 使用与第 1 节完全相同的 HMAC 头和规范字符串，只允许 `AUTHBRIDGE_WRITER` 与 `LEGACY_FULL`。Core 的 `leaderboard-sync.enabled` 默认关闭；启用后使用独立 `base-url`、`key-id`、`hmac-secret`、超时和周期配置，首次上传延迟 30 秒，实际周期最小为 60 秒。

请求体声明当前启用 DAILY 游戏、每个非空榜单和至多 100 名玩家：

```json
{
  "games": ["Bingo"],
  "boards": [{
    "game": "Bingo",
    "metric": "BINGO_MAX_TASKS",
    "map": null,
    "format": "COUNT",
    "lowerBetter": false,
    "entries": [{
      "uuid": "11111111-1111-4111-8111-111111111111",
      "username": "PlayerA",
      "value": 10,
      "tieDurationMs": -1
    }]
  }],
  "generatedAt": 1760000000000
}
```

Web 校验游戏、指标、格式与 `lowerBetter` 必须匹配注册表，并拒绝重复榜单、重复玩家和未来生成时间。成绩按当前 UUID 模式关联未撤销注册账号；未绑定账号被忽略。每个来源 keyId 的一次快照会替换自己在该游戏下的旧提交；公开榜单按账号合并多个来源，时间类取更短值，其他指标取更大值，composite 先取更大主值再取更短用时。Web 将缓存写入数据库，Core 离线时仍可展示最近结果。
