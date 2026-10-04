# 玩家 UUID 契约

本文定义 ChampionshipsCore 的玩家身份模型，以及它在接入可选身份平台时的 UUID 边界。Core 使用 `OFFLINE` 支持独立离线服务端，使用 `PROFILE_UUID` 支持正版档案或自建统一 UUID 服务；任何组件都按明确来源解析身份，不按名称在不同 UUID 策略间猜测或静默回退。

## 1. 目标与术语

核心目标是让离线队员录入、玩家登录、AuthMe 同步和档案服务在统一 UUID 部署中始终指向同一个玩家身份。Core 可独立运行；身份平台只负责账号、准入、统一 UUID 或外部展示等扩展能力。

| 术语 | 含义 |
| --- | --- |
| `OFFLINE` | Minecraft 离线模式 UUID：`UUID.nameUUIDFromBytes("OfflinePlayer:<name>")`。名称的大小写规范必须与 Core 的既有规则一致。 |
| `PROFILE_UUID` | 登录档案系统分配的 UUID。其来源可以是 Mojang、Yggdrasil 服务或其他兼容的档案目录。 |
| profile directory | 可按玩家名查询 `PROFILE_UUID` 的标准 HTTP 服务，路径为 `/users/profiles/minecraft/<name>`。 |
| current effective UUID | 身份平台为已绑定玩家当前生效的 UUID。平台内部的历史迁移状态不会成为 Core 的配置模式。 |
| server original identity | 身份平台统一账户关联的服务器原始身份，只有离线身份与 Mojang 正版身份两类，用于明确旧服务器资料的 UUID 语义。 |

Core 的 `identity.mode` 只有 `OFFLINE` 与 `PROFILE_UUID`。`ONLINE`、`PROFILE_API`、`SERVER_UUID`、`CUSTOM_UUID` 都不是有效的 Core 模式。旧配置中的 `ONLINE` 和 `PROFILE_API` 必须显式改为 `PROFILE_UUID` 并配置档案服务；运行代码不识别这些旧值，也不自动改写已有玩家数据。

## 2. ChampionshipsCore 的身份规则

Core 的身份策略负责在管理员只有玩家名、玩家当前不在线时确定应持久化的 UUID。Core 与认证组件共用 `championships-common` 的 `AuthIdentity` 校验用户名、解析 UUID 和计算离线身份。账户制度、密码、皮肤和平台内 UUID 迁移由外部身份平台管理。

| 场景 | `OFFLINE` | `PROFILE_UUID` |
| --- | --- | --- |
| 玩家在线 | 使用 `Player#getUniqueId()`；校验其等于按离线算法计算出的 UUID，冲突即拒绝，不重写。 | 直接使用 `Player#getUniqueId()`；身份链路已保证在线与档案 UUID 一致。 |
| 玩家不在线，按名称加队/建档 | 本地计算 `OfflinePlayer:<name>` UUID。 | 查询配置的 profile directory，使用响应中的 UUID。 |
| 查询失败/找不到玩家 | 不适用。 | 操作失败并告知管理员；Core 不回退到离线 UUID。 |

### 2.1 配置含义

```yaml
identity:
  mode: OFFLINE # 或 PROFILE_UUID
  profile-api-base-url: "https://api.mojang.com"
```

`identity.profile-api-base-url` 只在 `PROFILE_UUID` 下使用。它是通用的 profile directory 地址：

| 部署形态 | `mode` | `profile-api-base-url` | 登录 UUID 的来源 |
| --- | --- | --- | --- |
| 独立离线服 | `OFFLINE` | 忽略 | Paper/Bukkit 离线身份 |
| 独立正版服 | `PROFILE_UUID` | `https://api.mojang.com` | 正版登录档案 |
| 自建统一 UUID 服 | `PROFILE_UUID` | 身份平台提供的 profile API | 代理/Injector 提供的相同档案 UUID |
| 其他 Yggdrasil 服务 | `PROFILE_UUID` | 对应服务的 profile API | 该服务注入的 UUID |

`PROFILE_UUID` 下从未登录玩家的权威查询方式是 HTTP profile directory 查询；`Bukkit.getOfflinePlayer(name).getUniqueId()` 的结果依赖 authlib-injector 对已知名字的缓存，对未见过的名字不具备确定语义。

### 2.2 一致性与冲突处理

1. `PROFILE_UUID` 的 profile directory 返回值必须与玩家实际登录时 Bukkit 暴露的 UUID 相同。该要求由部署验收保证，在线操作使用 Bukkit UUID，不再二次查询。
2. profile 响应为 204/404、超时、非 2xx、非法 JSON、缺失/非法 UUID 或名称不匹配时，离线录入操作失败。Core 不生成离线 UUID，不使用平台账户 ID，也不使用缓存的其他名字结果。
3. 同名 Core 身份记录与查得 UUID 不同时，普通加队、自动建档和隐式合并都拒绝；处理方式是显式 UUID 迁移。
4. `OFFLINE` 只使用本地离线算法；`PROFILE_UUID` 只使用档案目录或登录链路提供的 UUID。两条路径保持独立、可审计。
5. Core 的队伍成员、积分、比赛记录等都以 UUID 为主键。显示名称是展示属性，不用作自动迁移或数据关联的键。

## 3. 登录链路与单一改写点

`PROFILE_UUID` 的在线 UUID 由登录档案链路提供。每个登录环节返回同一个 UUID，一个登录会话只有一个身份改写职责。

```text
profile directory --(按名查询)--> AuthProxy / authlib-injector --(登录档案 UUID)--> Paper/Bukkit --(Player#getUniqueId)--> Core
       |
       +--(仅玩家离线时)-----------------------------------------------------> Core 管理命令
```

### 3.1 ChampionshipsAuthProxy

AuthProxy 是统一 UUID 部署中的可选代理组件：

1. 启动同步获取全部当前获准玩家及 current effective UUID；正常预登录实时查询最新准入结果，并把权威 UUID 写入代理连接。
2. 每次成功快照、增量或登录查询都会原子更新持久缓存。连接失败、DNS 故障、超时、HTTP 429 或 5xx 可回退到已同步的同名档案；401/403、其他 4xx、非法 JSON、未知状态与缺失/非法 UUID 均失败关闭。
3. 离线回退使用缓存中的同一身份。未知玩家、从未完成全量快照且未单独成功查询的玩家拒绝登录；已缓存的撤销、有效封禁和维护锁继续拒绝。
4. AuthProxy 服务启用统一 UUID 的 Bungee 登录链路；`OFFLINE` 部署无需安装它。
5. authlib-injector 负责 Yggdrasil/profile/皮肤兼容，AuthProxy 负责预登录注入。两者必须配置为返回同一 UUID，后续环节不再独立改写登录身份。

### 3.2 身份平台的统一身份、Yggdrasil 与皮肤

身份平台管理统一账户及其与服务器原始身份的关联。每个可同步的服务器原始身份有两种语义：

| 原始身份类型 | 身份依据 | 对应 UUID |
| --- | --- | --- |
| `OFFLINE` | 服务器玩家名 | `OfflinePlayer:<name>` 算法生成的 UUID。 |
| `MOJANG` | Mojang 正版玩家档案 | Mojang 官方 profile 返回的 UUID。 |

这两个类型描述统一账户迁入前服务器资料的 UUID 来源；它们选择 Bridge 的 `uuidSource`，不改变 Core 的 `identity.mode`，也不决定统一登录查询服务。身份平台为统一登录维护的 current effective UUID 是一项独立数据，可以与旧的离线或 Mojang UUID 不同。

身份平台的 Yggdrasil 名称和 UUID 查询接口只返回已绑定玩家的 current effective UUID。未绑定、未知或无有效 UUID 的玩家返回未找到；统一登录查询不会回查 Mojang 并把官方 UUID 当作该服身份。

官方皮肤是独立职责。对已绑定玩家，身份平台可以向 Mojang 查询并透传 `textures` profile property；皮肤查询的结果不参与当前登录 UUID 的生成、替换或回填。

平台内部的 `SERVER_UUID`、`CUSTOM_UUID` 等只表示历史数据或迁移阶段。Core 只识别 `OFFLINE` 和 `PROFILE_UUID`。

### 3.3 AuthProxy 与 AuthBridge 的准入职责

AuthProxy 安装在 Bungee 上即表示代理准入模式（`PROXY`），不提供额外的模式开关。AuthBridge 安装在 Paper/Core 上时，通过 `access.admission-owner` 选择准入职责：

| 能力 | AuthProxy（固定 PROXY） | AuthBridge `PROXY` | AuthBridge `BRIDGE` |
| --- | --- | --- | --- |
| 预登录拒绝未绑定、撤销、封禁 | 负责 | 信任代理结果 | 在 Paper 侧再次拒绝 |
| 维护状态拒绝 | 负责 | 负责后端维护锁/迁移保护 | 负责并向玩家提示 |
| 平台不可用时的玩家准入 | 使用 `state.properties` 缓存按策略回退 | 已由代理放行的玩家继续进入后端 | 按 `fail-closed-before-first-sync` 决定；首次同步前可拒绝 |
| AuthMe 密码/账户同步 | 不接收密码或 writer ACK | 负责 | 负责 |
| UUID 解析与 Bukkit UUID 校验 | 注入登录档案 UUID | 校验实际 Bukkit UUID，发现不一致阻断后端 | 同上 |
| 撤销/封禁后的在线玩家 | 代理侧处理 | 交给代理侧处理 | Bridge 同步后主动踢出 |
| 本地封禁持久化 | `state.properties` | 同步到 `state.yml` 供模式切换 | 同步到 `state.yml`，用于离线期间拒绝 |

生产 Bungee 部署通常使用 AuthProxy + AuthBridge `PROXY`；没有 AuthProxy 或需要 Paper 独立阻断访问时选择 AuthBridge `BRIDGE`。两种组合都使用登录链路给定的 UUID，并保持 Core 的 `identity.mode` 不变。

## 4. ChampionshipsAuthBridge 的最小边界

AuthBridge 是可选的密码与认证资料同步组件。它只按协议事件的显式来源执行；当前服务器的身份模式由 Core 配置和部署验收确定。

| 协议类型 | 必需字段 | UUID 规则 |
| --- | --- | --- |
| 全量快照/账户下发 | `username`、密码哈希、修订号、`uuidSource`，以及该来源所需字段 | 根据 `uuidSource` 得到 UUID 后写入 AuthMe/本地访问状态；字段缺失、解析失败或非法即拒绝。 |
| 密码更新 | `username`、密码哈希、修订号 | 只更新密码，不重新计算 UUID。 |
| 改名 | `oldName`、`newName`、当前 UUID | 以显式 UUID 锁定同一身份，再更新名称。 |
| UUID 迁移 | `fromUuid`、`toUuid`、迁移 ID/修订号 | 使用冻结值执行，不从名字重算任一端 UUID。 |

`uuidSource` 是 Bridge 协议枚举，取值固定如下：

| `uuidSource` | 额外字段 | Bridge 的处理 | 适用身份 |
| --- | --- | --- | --- |
| `UUID` | `minecraftUuid` | 校验并直接使用。 | 身份平台已有确定的 current effective UUID，例如统一 UUID 或已完成的迁移。 |
| `OFFLINE` | 无 | 使用事件的 `username` 按 `OfflinePlayer:<name>` 算法生成 UUID。 | 要写入原本以离线 UUID 运作的服务器资料。 |
| `ONLINE` | 无 | 用事件的 `username` 查询 Mojang 官方 `/users/profiles/minecraft/<name>`，使用响应 UUID。 | 要写入原本以 Mojang UUID 运作的服务器资料。 |

`OFFLINE` 的名称规范必须与服务器离线登录及 Core 的既有规则一致。`ONLINE` 只允许 Mojang 官方档案查询；统一 UUID 登录必须由身份平台下发 `UUID`。`ONLINE` 查询未找到、网络失败、响应 UUID 非法或名称不匹配时，Bridge 拒绝该 UUID 相关事件。`UUID` 缺失或非法同样拒绝。

Bridge 根据事件来源执行离线计算或 Mojang 查询，并把 UUID 策略保留在 Core 配置和平台事件中。它不读取或解释 Core 身份模式、不调用 Core 推导平台 UUID、不把本地推导出的 UUID 回写给平台，也不在 UUID 缺失时用账户 ID 顶替。

普通 outbox 确认只推进事件游标，不携带 `serverUuids` 或触发平台 UUID 回写。身份迁移使用专用控制任务。

## 5. UUID 迁移规范

当离线 UUID、旧平台 UUID 或其他旧身份需要切换到 `PROFILE_UUID` 时，迁移流程生成完整的审计记录。

1. **准备**：停止新比赛、禁止相关玩家登录；备份 Core、AuthMe 和平台数据库；生成并人工审核 `old name -> fromUuid -> toUuid` 映射清单。
2. **冻结**：身份平台创建带唯一 ID 的 `IDENTITY_MODE_MIGRATION` 控制任务，逐项携带固定的 `fromUuid` 与 `toUuid`。任务创建后按冻结映射执行。
3. **执行**：确认服务器无人在线后，Bridge 通过 `PlayerIdentityMigrationEvent` 把冻结映射交给 Core，并等待数据库事务完成，再处理 AuthMe 和本地访问状态。Core 未就绪、事务失败或等待超时均使控制任务失败，不继续提交。
4. **校验**：检查每个 `toUuid` 唯一、所有外键记录数量符合预期、旧 UUID 无残留引用、玩家登录 UUID 与 profile directory 一致。
5. **提交或回滚**：所有组件成功后，身份平台把状态切换为新 UUID。任一步失败均不确认控制任务，按备份和已记录的执行状态回滚；迁移使用同一冻结映射重试。

常规“添加离线队员”使用普通身份规则；同名 UUID 冲突进入显式迁移流程。

## 6. 配置和上线

1. 根据登录链路选择 Core 模式：独立离线服使用 `OFFLINE`；正版或统一 UUID 部署使用 `PROFILE_UUID` 并填写对应的 profile directory。
2. 使用 Bungee/AuthProxy 时，AuthBridge 设置 `access.admission-owner: PROXY`；没有代理准入时才选择 `BRIDGE`。两种模式都保留维护锁与实际 Bukkit UUID 检查。
3. 先用一个测试账户核对代理预登录 UUID、档案查询 UUID、Paper `Player#getUniqueId()`、Core 队伍和 AuthMe 账号资料，确认所有环节使用同一身份。
4. 已有 UUID 需要变化时，按上一节的冻结映射执行维护任务；仅修改 `identity.mode` 不会迁移数据库。任务通过后更新登录链路与 Core 配置，再恢复登录。
5. 在测试服确认离线录入、首次登录、改名、UUID 冲突和档案服务不可用的结果，再应用到正式服务。

插件配置、客户端鉴权、事件 ACK 和控制任务字段见 [AuthBridge / AuthProxy 协议](auth-bridge-protocol.md)。代码接入和数据库职责见 [开发指南](development.md)。

## 7. 验收清单

| 验收项 | 期望结果 |
| --- | --- |
| `OFFLINE` 下添加未登录玩家 | 写入本地离线算法 UUID，登录后 Bukkit UUID 一致。 |
| `PROFILE_UUID` 下添加未登录的已注册玩家 | 查询 profile directory，写入其返回 UUID；首次登录后队伍仍被识别。 |
| `PROFILE_UUID` 下查询未知名/目录故障 | 命令失败，数据库不产生离线 UUID 或半成品身份。 |
| `PROFILE_UUID` 玩家在线操作 | 使用 Bukkit UUID，不额外调用 profile API。 |
| 同名 UUID 冲突 | 普通操作拒绝，提示使用显式迁移流程。 |
| 平台皮肤查询 | 可取得 Mojang `textures`，登录 UUID 保持档案 UUID。 |
| AuthProxy 缺少 UUID | 实时与缓存均无有效 UUID 时拒绝登录，不生成离线 UUID。 |
| 平台临时不可达（AuthProxy） | 已完成权威同步且未撤销/封禁的玩家沿用同名缓存 UUID 登录；未知玩家拒绝。 |
| 平台临时不可达（AuthBridge `PROXY`） | 已由代理放行的玩家继续完成 AuthMe/后端登录；Bridge 保留维护锁和 UUID 不一致保护。 |
| 平台临时不可达（AuthBridge `BRIDGE`） | 按 `fail-closed-before-first-sync` 处理；已有本地身份和封禁缓存可继续执行本地准入。 |
| AuthBridge `UUID` 账户下发 | 只使用合法的显式 `minecraftUuid`，不回写 UUID。 |
| AuthBridge `OFFLINE` 账户下发 | 使用事件用户名计算离线 UUID，结果与离线登录 UUID 一致。 |
| AuthBridge `ONLINE` 账户下发 | 只查询 Mojang 官方档案；查询失败时拒绝事件，不回退离线 UUID。 |
| AuthBridge 密码同步与迁移 | 密码更新不计算 UUID；迁移仅使用冻结映射。 |

## 文档入口

本文定义身份模型与 UUID 不变量；认证连接器的安装和配置见 [认证部署指南](auth-deployment.md)，接口字段、幂等和控制任务见 [同步协议](auth-bridge-protocol.md)。身份平台是外部系统，本仓库不提供其服务端实现。
