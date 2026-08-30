# 玩家 UUID 边界与演进方案

本文定义 ChampionshipsCore、ChampionshipsAuthBridge、ChampionshipsAuthProxy 与 cc-web 之间的 UUID 契约。它是后续实现、配置变更、数据库迁移和运维验收的唯一依据。

## 1. 目标与术语

目标是让 Core 可以脱离 cc-web 独立运行，同时让统一 UUID 部署中的离线队员录入、玩家登录、AuthMe 同步和网站皮肤服务始终指向同一个玩家身份。任何组件都不得按名称在不同 UUID 策略间猜测或静默回退。

| 术语 | 含义 |
| --- | --- |
| `OFFLINE` | Minecraft 离线模式 UUID：`UUID.nameUUIDFromBytes("OfflinePlayer:<name>")`。名称的大小写规范必须与现有 Core 规则保持一致。 |
| `PROFILE_UUID` | 登录档案系统分配的 UUID。其来源可以是 Mojang、cc-web Yggdrasil 或其他兼容的档案目录；不是“网站 UUID 模式”的同义词。 |
| profile directory | 可按玩家名查询 `PROFILE_UUID` 的标准 HTTP 服务，路径为 `/users/profiles/minecraft/<name>`。 |
| current effective UUID | cc-web 为已绑定玩家当前生效的 UUID。cc-web 内部的历史迁移状态可能影响它，但这些状态不能泄漏为 Core 的配置模式。 |
| server original identity | cc-web 统一账户所关联的服务器原始身份。它只有离线身份与 Mojang 正版身份两类，用于明确旧服务器资料的 UUID 语义，不等同于 current effective UUID。 |

Core 的 `identity.mode` 只有 `OFFLINE` 与 `PROFILE_UUID`。`ONLINE`、`PROFILE_API`、`SERVER_UUID`、`CUSTOM_UUID` 都不是有效的 Core 模式。旧配置中的 `ONLINE` 和 `PROFILE_API` 仅作为迁移输入，最终迁移为 `PROFILE_UUID`。

## 2. ChampionshipsCore 的身份规则

Core 的身份策略只解决一件事：当管理员只有玩家名、玩家当前不在线时，应持久化哪个 UUID。它不负责网站账户制度、密码、皮肤或 UUID 迁移策略。

| 场景 | `OFFLINE` | `PROFILE_UUID` |
| --- | --- | --- |
| 玩家在线 | 使用 `Player#getUniqueId()`；校验其等于按离线算法计算出的 UUID，冲突即拒绝，不重写。 | 直接使用 `Player#getUniqueId()`；不再发起 profile HTTP 查询，也不重写。 |
| 玩家不在线，按名称加队/建档 | 本地计算 `OfflinePlayer:<name>` UUID。 | 查询配置的 profile directory，使用响应中 UUID。 |
| 查询失败/找不到玩家 | 不适用。 | 明确失败并告知管理员；绝不退回离线 UUID。 |

### 2.1 配置含义

```yaml
identity:
  mode: OFFLINE # 或 PROFILE_UUID
  profile-api-base-url: "https://api.mojang.com"
```

`identity.profile-api-base-url` 只在 `PROFILE_UUID` 下使用。它是通用的 profile directory 地址，不是 cc-web 专用配置，也不意味着 Core 必须依赖 cc-web：

| 部署形态 | `mode` | `profile-api-base-url` | 登录 UUID 的来源 |
| --- | --- | --- | --- |
| 独立离线服 | `OFFLINE` | 忽略 | Paper/Bukkit 离线身份 |
| 独立正版服 | `PROFILE_UUID` | `https://api.mojang.com` | 正版登录档案 |
| 自建统一 UUID 服 | `PROFILE_UUID` | cc-web Yggdrasil 的 profile API | 代理/Injector 提供的相同档案 UUID |
| 其他 Yggdrasil 服务 | `PROFILE_UUID` | 对应服务的 profile API | 该服务注入的 UUID |

`Bukkit.getOfflinePlayer(name).getUniqueId()` 不能作为 `PROFILE_UUID` 下从未登录玩家的权威查询方式。authlib-injector 会影响登录和档案路径，却不保证 Bukkit 能将未见过的名字解析为远端档案 UUID，因此保留上述 HTTP 查询是必要的。

### 2.2 一致性与冲突处理

1. `PROFILE_UUID` 的 profile directory 返回值必须与玩家实际登录时 Bukkit 暴露的 UUID 相同。该要求由部署验收保证，不应在每次在线操作中二次查询。
2. profile 响应为 204/404、超时、非 2xx、非法 JSON、缺失/非法 UUID、名称不匹配时，离线录入操作失败。禁止生成离线 UUID、使用网站账户 ID 或使用缓存的其他名字结果代替。
3. 同名已存在 Core 身份记录、但查得 UUID 与记录 UUID 不同，必须拒绝普通加队、自动建档和隐式合并。只能通过显式 UUID 迁移处理。
4. `OFFLINE` 不得调用 profile directory；`PROFILE_UUID` 不得用离线 UUID 作为故障回退。两条路径在代码中保持彼此可审计的独立分支。
5. Core 的队伍成员、积分、比赛记录等都以 UUID 为主键。显示名称是属性，不得成为自动迁移或关联数据的键。

## 3. 登录链路与单一改写点

`PROFILE_UUID` 的在线 UUID 已由登录档案链路提供，Core 不再自行注入或转换。需要确保每个登录环节返回同一个 UUID，且一个登录会话中只有一个身份改写职责。

```text
profile directory --(按名查询)--> AuthProxy / authlib-injector --(登录档案 UUID)--> Paper/Bukkit --(Player#getUniqueId)--> Core
       |
       +--(仅玩家离线时)-----------------------------------------------------> Core 管理命令
```

### 3.1 ChampionshipsAuthProxy

AuthProxy 是可选组件，并且只面向 cc-web 管理的 `PROFILE_UUID` 部署：

1. 启动同步从 cc-web 获取全部当前获准玩家及 current effective UUID；正常预登录仍实时查询最新准入结果，并把权威 UUID 写入代理连接。
2. 每次成功快照、增量或登录查询都会原子更新持久缓存。仅连接失败、DNS 故障、超时、HTTP 429 或 5xx 可回退到已同步的同名档案；401/403、其他 4xx、非法 JSON、未知状态与缺失/非法 UUID 均失败关闭。
3. 离线回退不得生成离线 UUID、使用账户 ID 顶替或跨名称猜测。未知玩家、从未完成全量快照且未单独成功查询的玩家仍拒绝登录；已缓存的撤销、有效封禁和维护锁继续拒绝。
4. AuthProxy 不支持也不尝试服务 `OFFLINE` 部署；此类服务器无需安装它。
5. authlib-injector 负责 Yggdrasil/profile/皮肤兼容，AuthProxy 负责预登录注入。两者可以同时部署，但必须配置为返回同一 UUID，不能在后续阶段再次独立改写登录身份。

### 3.2 cc-web 的统一身份、Yggdrasil 与皮肤

cc-web 管理网站统一账户及其与服务器原始身份的关联。每个可同步的服务器原始身份只有以下两种语义：

| 原始身份类型 | 身份依据 | 对应 UUID |
| --- | --- | --- |
| `OFFLINE` | 服务器玩家名 | `OfflinePlayer:<name>` 算法的 UUID。 |
| `MOJANG` | Mojang 正版玩家档案 | Mojang 官方 profile 返回的 UUID。 |

这两个类型描述“该账户迁入网站统一身份前，服务器资料原来以什么 UUID 运作”，而不是选择 Core 的 `identity.mode`，也不表示统一 UUID 登录时应向哪个服务查询。cc-web 为统一登录维护的 current effective UUID 是另一项明确数据；它可以与旧的离线或 Mojang UUID 不同。

cc-web 的 Yggdrasil 名称和 UUID 查询接口只返回已绑定玩家的 current effective UUID。未绑定、未知或无有效 UUID 的玩家返回未找到，不能为了“兼容”回查 Mojang 并把官方 UUID 当作该服身份。

官方皮肤是独立职责：对于已绑定玩家，cc-web 可以向 Mojang 查询并透传 `textures` profile property。Mojang 的皮肤查询不得决定、替换或回填当前登录 UUID。

cc-web 内部的 `SERVER_UUID`、`CUSTOM_UUID` 等只可表示历史数据或网站迁移阶段。它们不能成为 Core `identity.mode`，也不能要求 Core 了解 cc-web 的迁移算法。

### 3.3 AuthProxy 与 AuthBridge 的准入职责

AuthProxy 安装在 Bungee 上即表示代理准入模式（`PROXY`），不提供额外的模式开关。AuthBridge 安装在 Paper/Core 上时，通过 `access.admission-owner` 选择是否重复承担玩家准入：

| 能力 | AuthProxy（固定 PROXY） | AuthBridge `PROXY` | AuthBridge `BRIDGE` |
| --- | --- | --- | --- |
| 预登录拒绝未绑定、撤销、封禁 | 负责 | 不负责，仅信任代理结果 | 负责，在 Paper 侧再次拒绝 |
| 维护状态拒绝 | 负责 | 负责后端维护锁/迁移保护 | 负责，并向玩家提示 |
| cc-web 不可用时的玩家准入 | 使用 `state.properties` 缓存按策略回退 | 不因 Web 不可用重复拒绝已由代理放行的玩家 | 按 `fail-closed-before-first-sync` 决定；首次同步前可拒绝 |
| AuthMe 密码/账户同步 | 不接收密码或 writer ACK | 负责 | 负责 |
| UUID 解析与 Bukkit UUID 校验 | 注入登录档案 UUID | 负责校验实际 Bukkit UUID，发现不一致阻断后端 | 同上 |
| 撤销/封禁后的在线玩家 | 代理侧处理 | 不主动踢出，避免与代理重复 | Bridge 同步后主动踢出 |
| 本地封禁持久化 | `state.properties` | 同步到 `state.yml` 供模式切换，但不作为当前准入依据 | 同步到 `state.yml`，用于离线期间拒绝 |

因此，生产 Bungee 部署通常使用 AuthProxy + AuthBridge `PROXY`；没有 AuthProxy 或需要 Paper 独立阻断访问时才选择 AuthBridge `BRIDGE`。两者都不会生成离线 UUID，也不会改变 Core 的 `identity.mode`。

## 4. ChampionshipsAuthBridge 的最小边界

AuthBridge 是可选的密码与认证资料同步组件。它不读取或推断 Core 的 `identity.mode`，也不调用 Core 或其 profile directory。对于需要 UUID 的网站下发事件，cc-web 必须明确声明目标 UUID 的来源；Bridge 只按该声明执行，不能自行猜测当前服务器采用离线、正版还是统一 UUID 登录。

| 协议类型 | 必需字段 | UUID 规则 |
| --- | --- | --- |
| 全量快照/账户下发 | `username`、密码哈希、修订号、`uuidSource`，以及该来源所需字段 | 根据 `uuidSource` 得到 UUID 后写入 AuthMe/本地访问状态；字段缺失、解析失败或非法即拒绝。 |
| 密码更新 | `username`、密码哈希、修订号 | 不需要 UUID；不得借机按名称重新计算。 |
| 改名 | `oldName`、`newName`、当前 UUID | 以显式 UUID 锁定同一身份，再更新名称。 |
| UUID 迁移 | `fromUuid`、`toUuid`、迁移 ID/修订号 | 使用冻结值执行，绝不从名字重算任一端 UUID。 |

`uuidSource` 是 Bridge 协议枚举，取值固定如下：

| `uuidSource` | 额外字段 | Bridge 的处理 | 适用身份 |
| --- | --- | --- | --- |
| `UUID` | `minecraftUuid` | 校验并直接使用。 | cc-web 已有确定的 current effective UUID，例如统一 UUID 或已完成的迁移。它不是一种 server original identity。 |
| `OFFLINE` | 无 | 使用事件的 `username` 按 `OfflinePlayer:<name>` 算法生成 UUID。 | 要写入原本以离线 UUID 运作的服务器资料。对应 cc-web 的 `OFFLINE` 原始身份。 |
| `ONLINE` | 无 | 用事件的 `username` 查询 Mojang 官方 `/users/profiles/minecraft/<name>`，使用响应 UUID。 | 要写入原本以 Mojang UUID 运作的服务器资料。对应 cc-web 的 `MOJANG` 原始身份。 |

`OFFLINE` 的名称规范必须与服务器离线登录及 Core 的既有规则一致。`ONLINE` 只允许 Mojang 官方档案查询，不能改查 cc-web 或其他 Yggdrasil 服务；统一 UUID 登录必须由 cc-web 下发 `UUID`。`ONLINE` 查询未找到、网络失败、响应 UUID 非法或名称不匹配时，Bridge 拒绝该 UUID 相关事件，不得回退至离线 UUID。`UUID` 缺失或非法同样拒绝。

因此，Bridge 可以按事件的明确来源执行离线计算或 Mojang 查询，但它不拥有“本服务器该用哪种 UUID”的策略：cc-web 根据同步目标及该账户的 server original identity 写入事件。Bridge 不得：读取并解释 Core 身份模式、调用 Core 推导网站 UUID、把服务器本地推导出的 UUID 回写给 cc-web，或在 UUID 缺失时用账户 ID 顶替。

普通 outbox 确认只推进事件游标，不能携带 `serverUuids` 或触发网站 UUID 回写。身份迁移必须使用专用控制任务，而不是复用普通密码同步事件。

## 5. UUID 迁移规范

当离线 UUID、旧网站 UUID 或其他旧身份需要切换到 `PROFILE_UUID` 时，迁移不是名称修复，而是有审计记录的数据迁移。

1. **准备**：停止新比赛、禁止相关玩家登录；备份 Core、AuthMe 和 cc-web 数据库；生成并人工审核 `old name -> fromUuid -> toUuid` 映射清单。
2. **冻结**：cc-web 创建带唯一 ID 的 `IDENTITY_MODE_MIGRATION` 控制任务，逐项携带固定的 `fromUuid` 与 `toUuid`。任务创建后不得按名称重新解析。
3. **执行**：确认服务器无人在线后，Bridge 对 AuthMe 与本地访问状态按冻结 UUID 迁移；Core 的队伍、积分、比赛与身份记录由专用 Core 迁移工具/管理流程在事务中迁移。
4. **校验**：检查每个 `toUuid` 唯一、所有外键记录数量符合预期、旧 UUID 无残留引用、玩家登录 UUID 与 profile directory 一致。
5. **提交或回滚**：所有组件成功后才把 cc-web 状态切换为新 UUID。任何一步失败均不确认控制任务，按备份和已记录的执行状态回滚；禁止部分成功后以新名字重新计算补救。

常规的“添加离线队员”不能触发此流程。发现同名 UUID 冲突时，应停止操作并要求管理员执行有映射清单的迁移。

## 6. 实施安排

### 阶段 A：收敛 Core 身份源

1. 保留并测试 `OFFLINE` 的本地离线算法分支。
2. 将 `PROFILE_UUID` 定义为通用 HTTP profile directory 查询，而非 cc-web API 特例。
3. 在线玩家始终信任 `Player#getUniqueId()`；离线名称只按当前模式进入对应分支。
4. 将“无回退、响应校验、同名冲突拒绝”写为单元与集成测试。

### 阶段 B：收敛认证组件

1. AuthBridge 删除 Core UUID 策略推导、Core resolver 调用和 UUID 回写确认字段；全量账户事件仅按 `UUID`、`OFFLINE` 或 `ONLINE` 的显式 `uuidSource` 执行。
2. AuthProxy 限定为 `PROFILE_UUID`/cc-web 部署，缺 UUID 失败关闭。
3. 对 authlib-injector 与 AuthProxy 增加部署检查：同一测试账户的代理预登录 UUID、Yggdrasil 查询 UUID、Paper `Player#getUniqueId()` 必须一致。

### 阶段 C：迁移与上线

1. 为 Core 提供显式 UUID 映射迁移入口、预检和可审计日志；不在普通玩家管理命令中隐式迁移。
2. 更新默认配置注释、管理员文档和运维清单，明确 `profile-api-base-url` 的通用语义。
3. 先在测试服完成离线录入、首次登录、改名、UUID 冲突和服务不可用五类验收，再按第 5 节执行生产迁移。

## 7. 验收清单

| 验收项 | 期望结果 |
| --- | --- |
| `OFFLINE` 下添加未登录玩家 | 写入本地离线算法 UUID，登录后 Bukkit UUID 一致。 |
| `PROFILE_UUID` 下添加未登录的已注册玩家 | 查询 profile directory，写入其返回 UUID；首次登录后队伍仍被识别。 |
| `PROFILE_UUID` 下查询未知名/目录故障 | 命令明确失败，数据库不产生离线 UUID 或半成品身份。 |
| `PROFILE_UUID` 玩家在线操作 | 只使用 Bukkit UUID，不额外调用 profile API。 |
| 同名 UUID 冲突 | 普通操作拒绝，提示使用显式迁移流程。 |
| cc-web 皮肤查询 | 可取得 Mojang `textures`，但不会改变登录 UUID。 |
| AuthProxy 缺少 UUID | 实时与缓存均无有效 UUID 时拒绝登录，不生成离线 UUID。 |
| cc-web 临时不可达（AuthProxy） | 已完成权威同步且未撤销/封禁的玩家沿用同名缓存 UUID 登录；未知玩家拒绝。 |
| cc-web 临时不可达（AuthBridge `PROXY`） | 已由代理放行的玩家继续完成 AuthMe/后端登录；Bridge 仅保留维护锁和 UUID 不一致保护。 |
| cc-web 临时不可达（AuthBridge `BRIDGE`） | 按 `fail-closed-before-first-sync` 处理；已有本地身份和封禁缓存可继续执行本地准入。 |
| AuthBridge `UUID` 账户下发 | 只使用合法的显式 `minecraftUuid`，不回写 UUID。 |
| AuthBridge `OFFLINE` 账户下发 | 使用事件用户名计算离线 UUID，结果与离线登录 UUID 一致。 |
| AuthBridge `ONLINE` 账户下发 | 只查询 Mojang 官方档案；查询失败时拒绝事件，不回退离线 UUID。 |
| AuthBridge 密码同步与迁移 | 密码更新不计算 UUID；迁移仅使用冻结映射。 |

## 8. 文档职责

本文维护跨组件不变量。后续拆分或更新文档时，至少应保留以下边界：

| 文档 | 内容 |
| --- | --- |
| `player-uuid-contract.md`（本文） | 全局术语、边界和不可违反的规则。 |
| `core-identity-mode.md` | Core 配置、离线名称查询、冲突报错与管理员操作。 |
| `auth-bridge-protocol.md` | Bridge 事件/控制任务字段、幂等、确认与迁移状态。 |
| `auth-proxy-deployment.md` | AuthProxy 与 authlib-injector 的安装顺序、同 UUID 校验、故障策略。 |
| `identity-migration-runbook.md` | 生产前备份、映射审核、执行、回滚和验收记录模板。 |

在这些专门文档落地前，本文即为实施与运维的完整方案；任何实现与本文不一致时，应先更新设计并评审，而不是在单个组件中加入兼容性回退。
