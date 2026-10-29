# ChampionshipsCore 无人游戏测试调研

日期：2026-10-29

## 结论

当前代码可以复用“开局、队伍、地图、旁观、清理”的大部分生命周期，但没有可以直接把 64 个虚拟对象当成玩家的接口。要验证完整游戏，测试端必须让 64 个测试身份作为真实协议连接进入服务器；服务器内的 NPC、ArmorStand、逻辑 Walker 或只写 UUID 的测试夹具都不能覆盖 Bukkit `Player`、背包、移动包、重生、Tab、发包和登录生命周期。

推荐实现为“Core 测试会话 + 外部真实协议 Bot Harness”：Core 负责生成一次性 16 队×4 测试席位，在认证完成后绑定服务端观察到的实际 UUID，调用既有 GameManager 开局、把开发者加入/旁观、权限与清理；独立 Bot Harness 负责 64 个真实客户端连接及按游戏执行动作。Core 首版同时支持单局和 Test Event；第二阶段再把 Bingo 的 TEST 模式扩展到 common/Worker。

## 现有代码调查

### 已有的可复用入口

* `/cc game start` 经过 `GameStartArguments`、`GameStartRules`、`GameStartService`，然后按游戏拓扑调用 `GameManager`。入口在 `championships-core/src/main/java/ink/ziip/championshipscore/command/game/start/GameStartMainCommand.java` 和 `.../api/game/start/GameStartService.java`。
* `GameManager.joinMultiTeamInstanceForTeams`、`joinPairedInstances`、`joinTeamArea` 已经统一处理地图状态、玩家占用、旁观移除、预热、实例注册、可见性和失败回滚。
* `TeamManager.createTransientTeam` 已经能在主线程创建不写数据库的计分板队伍，并通过普通 `getTeamByPlayer(UUID)` 暴露给游戏逻辑；`removeTransientTeam(s)` 可恢复原有队伍。它只解决队伍层隔离，不能自动阻止登录时的 Player 身份写入。
* `BaseMultiTeamGameInstance` 的运行名册是 UUID 列表，很多游戏动作再通过 `Bukkit.getPlayer(UUID)` 取得真实在线玩家。`BaseGameInstance.isGameplayParticipant` 还会经过统一旁观身份判断。
* 旁观生命周期已经集中到 `SpectatorManager`/`GameManager`，所以开发者不加入队伍时可以直接走既有 `/cc spectate` 逻辑；不应为测试另建一套隐身或模式状态。

### 当前测试能力的边界

`championships-bingo-loadtest` 是逻辑区块加载和实体压力插件。它创建 `VirtualWalker`、chunk ticket 和实体，README 明确将它与“真实协议客户端或真人的端到端比赛”分开；它不能产生 Bukkit `Player`，也不能运行完整任务、背包、重生和网络路径。

现有手动测试文档（`docs/commands.md`）要求参与队伍成员已加入数据库并上线。这正是无人测试的缺口：当前的 `GameStartService` 只解析正式 `TeamManager` 队伍，默认使用 `GameRunMode.GAME`，没有一次性名册、Bot 连接和会话清理。

### 64 人规模的现有硬约束

* `team.max-members` 当前为 4，满足每队四人；但它只约束正式队伍修改，测试会话仍应显式验证每队恰好四名成员。
* `GameStartRules` 把 BattleBox、ParkourTag、LaserBox 归为双队副本，16 队需要 8 个空闲副本；Dodgebolt、DragonEggCarnival、SulfurSoccer 是恰好两队的决赛拓扑，不能把 16 队强行塞进一个实例。
* 其他游戏使用多队实例或内部物理分区，实际能否容纳 16 队必须由地图实例和游戏自己的 `tryStartGame`/配置预检决定，不能用统一 ALL 绕过限制。
* 运行 Core 配置目前 `max-players: 200`，默认资源为 100；64 个 Bot 加一名开发者至少需要 65 个在线席位，代理、AuthBridge/身份白名单、Paper/Folia 和 Worker 也必须允许这些连接。

## 为什么不建议在插件内伪造玩家

1. `Player` 是服务端连接、实体、网络包、权限、背包和事件的组合；Bukkit API 没有“创建一个可运行的 fake Player”入口。
2. 直接构造 NMS `ServerPlayer` 并注入空连接需要随 Paper 版本维护内部类、连接管线、线程所有权和登录状态，无法可靠覆盖当前 Paper/Folia 旁观与 Tab 契约。
3. Citizens/NPC/ArmorStand 没有玩家登录、`PlayerMoveEvent`、Inventory、Statistic、Advancement、重生和客户端包语义，测到的是实体展示而不是游戏。
4. 只把 64 个 UUID 放进 `ChampionshipTeam` 也不行：`ChampionshipTeam` 的批量动作、`ChampionshipPlayer` 消息、游戏 Handler 和 `isGameplayParticipant` 最终都按在线 `Bukkit.getPlayer` 工作。

因此，插件内可以有“会话编排器”，但玩家本身应由真实协议客户端产生。

## 推荐产品形态

### Core 侧测试会话

新增独立的 `TestSessionManager`（由 `ChampionshipsCore` 持有；`GameManager` 只提供实例 ownership/start surface）和测试入口，例如：

```text
/cc game test single start <游戏> <地图> [--profile idle|smoke|scenario] [--arena ...]
/cc game test event start <event-profile> [--game <游戏>] [--map <地图>] [--rounds <n>]
/cc game test join <1-16|spectator>
/cc game test leave
/cc game test status
/cc game test stop --confirm
```

`single` 和 `event` 是唯一的会话模式选择；`join`、`leave`、`status`、`stop` 对两种模式共用。`<游戏>` 使用 `GameTypeEnum.commandName()`，`<event-profile>` 是测试事件计划标识。唯一有效的启动路径是 `single start` 或 `event start`，不再同时保留 `/cc game test start`、`/cc test event` 等别名。入口应使用单独的 `cc.test` 权限（管理员可继承），只允许一场会话，默认要求测试服/测试配置开关。`game test` 不应伪装成普通 `game start`，也不应修改正式队伍、数据库、赛事状态或生产地图。
这组命令是本方案的唯一规范，当前尚未实现；代码落地后再同步到 `docs/commands.md`，避免把规划命令误认为现网能力。

一次会话至少保存：session UUID/generation、目标游戏/地图/副本、16 个 transient team、每队 4 个 Bot UUID、开发者占用的槽位、测试旁观者、启动阶段、外部 Bot 连接状态、已启动的实例和清理状态。所有延迟回调都要带 generation；stop、断线、reload、插件卸载和新会话都会使旧回调失效。

Bot 队伍应使用 `TeamManager.createTransientTeam`，不写 formal team DAO；结束时先让 `GameManager` 正常释放实例，再删除 transient teams、断开 Bot、恢复开发者的原始模式/队伍/大厅位置。不能把测试 Bot 写入正式队伍、排名、赛事导出或 Redis 正式状态。

### 开发者角色

* `join spectator`：不占 64 人名册，调用统一 `SpectatorManager`，保持真实 `GameMode.SPECTATOR` 和完整 Tab。
* `join <1-16>`：仅在 `WAITING`/测试准备阶段把该队一个 Bot 槽位替换成开发者 UUID，保证每队仍为 4 人；真实开赛后不允许改变 roster，避免游戏内部状态和旁观/可见性失配。
* `leave` 或掉线：若尚未开赛则补回 Bot；已开赛则按普通玩家退出/旁观契约处理，不自动把新身份插入旧回合。
* 会话创建者默认不自动加入队伍；需要参与或旁观时，使用统一的 `join` 命令。

### Bot Harness

单独的 Java/Node 进程连接测试服务器，使用服务器实际支持的 Minecraft 协议和身份链路，至少提供：

* 64 个稳定、可回收的测试身份（唯一测试名称；UUID 由认证链路提供并在登录后观察），连接/重连/断线统计；
* spawn/teleport 后确认、背包与模式观察、死亡/重生、跨维度和退出；
* `idle`：只完成连接、准备和基础状态验证；
* `smoke`：按游戏通用动作移动、跳跃、攻击、交互、丢弃和重生；
* `scenario`：按游戏定义动作脚本，例如 BuildMart 采集/摆放/提交、Riptide 节奏选择、LaserBox 射击、Bingo 任务动作。动作脚本在 Harness 中维护，Core 不保存第二套游戏规则。

Harness 应有 session token/测试 API，只接受 Core 发出的本次会话 roster；Bot 使用测试专用名称，不使用正式玩家名或生产身份。Harness 可以保存认证服务的期望 UUID用于比对，但不能把期望 UUID提交给 Core 作为权威值。生产服禁止安装或允许这些身份。

## RunMode 与计分隔离

不要复用 `DAILY`。现有 DAILY 在多个游戏中改变人数、散布、UI、统计和玩法分支；把它拿来表示测试会把公共试玩语义和测试清理混在一起。

建议新增 `GameRunMode.TEST`，并给 `BaseGameInstance` 一个明确的测试/结算策略：

* `isEventRun()` 仍只对 EVENT 为真；TEST 不进入正式赛程、不写 rank/points、不触发正式事件消费者；Test Event 由独立协调器推进；
* 测试结束可显示本局结果供开发者观察，但通过统一 settlement guard 阻止数据库提交和正式事件；
* `GameManager` 的所有 start、stop、reload、spectator focus、release 路径都识别 TEST；
* 每个游戏逐一审查 `GameRunMode.DAILY`/`EVENT` 分支，尤其 AceRace、ParkourWarrior、LaserShowdown、Bingo、Frostbite、Riptide，保证 TEST 沿用正常 GAME 玩法而不误走 DAILY 特殊逻辑；
* `abortAndReset` 仍用于测试中止，正常完成走游戏自身 `endGame()`，这样能测到真实结束/结算/清理路径。

如果第二阶段支持远程 Bingo，common 的 `MatchRunMode` 也需新增 TEST，并增加独立的 flow/settlement 字段或等价能力位，Core manifest、RemoteBingo、Worker registry/session、展示和结算全部同步。当前 `BinaryProtocolCodec` 写入的是 `runMode.name()` 字符串，不是枚举序号，因此不存在“必须追加在末尾”的序号约束；但新增字段仍必须提升协议版本、更新能力协商、编解码和 manifest hash。否则第一阶段应明确拒绝 `REMOTE Bingo`，只允许 LOCAL 测试，避免 Core/Worker 只改一端。

## 启动预检与游戏矩阵

测试开局前必须返回可读的预检结果，而不是部分启动后失败：

1. 游戏已启用、地图已发布且 `PrepareSessionManager.canStart` 为真；
2. 目标游戏拓扑和地图实例足够：双队副本需要 8 个等待副本，决赛类拒绝 16 队模式，多队/内部区按实际容量检查；
3. 64 个 Bot 身份均已通过认证、完成 AuthMe 登录并观察到服务端 UUID；名称与认证服务清单一致，开发者替换槽位已完成；
4. 服务器在线上限、代理、权限、身份模式、跨服/Worker 连接和世界资源满足要求；
5. 所有 transient team ID 唯一、成员无重复且各为 4 人；玩家/队伍/实例没有既有占用；
6. 预检失败全部回滚已创建的 transient team 和 Bot 连接。

## 分阶段落地

### Phase 1：Core 单局与 Test Event

新增 `TestSessionManager`、TEST run mode、transient roster、上述统一命令和外部 Bot Harness；单局和 Event 共用同一会话、认证和清理模型。先覆盖 BattleBox、ParkourTag、LaserBox（8 副本）、TGTTOS、TNTRun、BuildMart、SkyWars、Riptide 等 Core 本地路径，同时验证 Event 的第一轮、轮间、下一轮和整场中止。

### Phase 2：远程 Bingo

把 TEST 加入 common protocol 和 Bingo Worker，冻结 16 队×4 roster 到 manifest，Worker 只认 TEST 会话并禁用正式积分/Redis 结果写入；验证 Core↔Worker、代理转服、真实 64 连接、任务、重生、旁观、回收和第二局。

### Phase 3：动作脚本与可观测性

为每个游戏提供脚本 profile、超时和失败快照；记录 session、游戏阶段、64 个连接状态、玩家模式、实例 stage、错误事件、清理耗时和资源泄漏。脚本应能固定随机种子或复现同一动作序列，便于定位回归。

## 必须验证的验收场景

* 64 Bot 连接成功、16 队×4 成员展示正确，开发者可在任一队替换一个 Bot，或保持无队旁观；
* 所有在线玩家仍出现在 Tab，实体隐藏不删除 Tab；旁观者能看到隐身玩家和其他旁观者；
* 准备、倒计时、正式比赛、死亡/重生、断线重连、正常结束、`stop --confirm`、插件 reload/disable；
* 测试不产生正式队伍、排名积分、赛事导出、Redis 正式结果或数据库污染；
* 失败启动没有半场实例、残留 transient team、Bot 连接、chunk ticket、任务或旧回调；
* 第二场测试在同一地图/副本可再次启动，且不受第一场身份、旁观或可见性状态影响；
* 按 `GameTypeEnum` 逐一覆盖适用游戏，明确记录决赛类拒绝 16 队的原因；
* Core 本地和 Bingo Worker 的共享展示、模式包、Tab、队伍颜色和结果清理分别在支持的 Paper/Folia 上做真实连接验证。

## 本次调研结论

仓库已有的 `GameManager`、transient team 和统一 spectator 服务足够作为实现骨架；缺少的是同时覆盖单局/Event 的一次性测试会话、真实客户端来源、TEST 结算隔离、认证身份上下文和按游戏拓扑的预检。最小可行实现不是在 Core 内造 64 个假玩家，而是增加一个受权限保护的 Core 编排层，并配套可丢弃测试服上的 64 个协议 Bot。

## 补充：Bot 不能自行决定 UUID

当前认证链路明确规定，Bot 登录时使用的 UUID 由服务端身份链路决定：

```text
Bot username/password
  -> AuthProxy PreLogin 查询 profile
  -> Bungee setUniqueId(profile.uuid)
  -> Paper AsyncPlayerPreLoginEvent.getUniqueId()
  -> Core PlayerManager.prepareIdentity(name, actualUuid)
  -> Bukkit Player#getUniqueId()
```

AuthProxy 的 `onPreLogin` 在允许登录时执行 `setUniqueId(UUID.fromString(profile.uuid))`；它还可能因未绑定、撤销、封禁、维护、身份服务故障或缓存策略拒绝 Bot。Paper 侧 `PlayerListener` 会把实际登录 UUID 交给 `PlayerManager.prepareIdentity`，而 `identity.mode=OFFLINE` 会拒绝与 `OfflinePlayer:<name>` 算法不一致的登录 UUID。`PROFILE_UUID` 下则不能用离线算法替代 profile UUID。

这意味着 Bot Harness 的 roster 协议不能只发送 `name -> UUID` 静态文件，也不能由 Core 先用 `UUID.nameUUIDFromBytes` 生成 64 个 UUID。应采用以下流程：

1. 测试身份服务预先创建 64 个专用 Bot 名称和准入资料；在统一 UUID 部署下，每个名称必须有固定 current effective UUID。该 UUID 只作为认证服务和 Core 预检的期望值，最终权威仍是登录后观察到的 Bukkit UUID。测试环境应有独立的 profile/allowlist namespace，不使用生产账户。
2. Harness 连接代理，完成实际 Minecraft Login Success；它记录服务端下发的 UUID，并通过受保护的测试会话通道报告 `sessionId + username + observedUuid`。如果使用直接 Paper 的离线隔离服，Harness 才能按 `OfflinePlayer:<name>` 计算并在登录后再次比对。
3. Core 只接受当前连接 `Player#getUniqueId()` 作为权威值。待 64 个 Bot 都完成连接、AuthMe 登录和 UUID 校验后，Core 才创建 transient teams；team roster 使用观察到的 UUID，不能使用 Harness 预期值覆盖服务端值。
4. 若 `PROFILE_UUID`/AuthProxy 的 profile UUID 与 Paper Bukkit UUID 不一致，或同名重连得到不同 UUID，会话必须失败并清理，不得自动迁移或按名称归并。
5. 开发者替换 Bot 槽位时同样使用其当前 Bukkit UUID；Bot 断开后旧 UUID 不得重新绑定到另一名称。重连必须以 UUID 和当前连接 generation 重新确认。

### AuthMe/准入的实际前置条件

AuthProxy 只负责预登录准入和 UUID 注入，AuthMe 仍可能要求 `/login <password>`。因此 Harness 还需要：

* 为 64 个测试名称预置 AuthMe 账号/密码，或在独立测试服使用专用自动认证配置；不能在生产服自动 `/register` 或共享真实密码。
* AuthProxy `PROXY` 模式下，把 Bot 账号加入身份平台允许名单，并让 AuthBridge 的 AuthMe 快照/账号 UUID 与 profile UUID 一致；未知 Bot 会在代理层被拒绝，根本到不了 Core。
* AuthBridge `BRIDGE` 模式下，把 Bot 加入本地 `state.yml` 身份快照，并验证 `AccessListener` 的 UUID mismatch 检查；只改 Core 队伍表不足以放行。
* 连接应错峰，并观察代理 profile API、AuthMe、Paper pre-login、Core join、Bot `/login` 的每一步结果；AuthProxy/身份平台的速率限制和 AuthMe 防暴力/验证码配置都可能让 64 个并发连接失败。
* 测试会话不得向生产 AuthBridge 控制任务写入临时账号。优先使用独立测试身份服务、独立 AuthMe 数据库和独立代理；若只能共享服务，必须使用可审计的测试账户生命周期和 session allowlist，结束时撤销。

### Auth 验收矩阵

至少分别验证以下两条链路：

| 链路 | 预期 | 测试重点 |
| --- | --- | --- |
| 隔离 `OFFLINE` Paper | Bukkit UUID 等于离线算法，Core 接受 | 64 个名称、大小写、重连、替换 Bot、无 AuthProxy 旁路 |
| `PROFILE_UUID` + AuthProxy + AuthBridge `PROXY` | Proxy 注入 profile UUID，Paper/Core/队伍均使用同一 UUID | profile 查询、缓存回退、AuthMe 登录、Core pre-login mismatch、重连 |
| `PROFILE_UUID` + AuthBridge `BRIDGE` | Bridge 本地 allowlist 校验实际 UUID | 首次同步、未绑定、UUID mismatch、维护/封禁、AuthMe LoginEvent |
| profile 服务异常 | 仅允许既有合法缓存且符合策略的账户 | 未知 Bot 不得静默生成离线 UUID |
| UUID 变更 | 会话拒绝或进入显式迁移流程 | 测试 roster 不自动迁移生产数据 |

## 实现优先级

1. 先做独立测试身份/代理/AuthMe/Bot Harness 的真实登录闭环，确认 64 个 Bot 能提供服务端实际 UUID；没有这一步，Core 侧 transient team 设计无法验收。
2. 再做 Core `TestSessionManager`，首版同时实现 `single` 和 `event` 两种会话模式；Event 轮次协调不能延期到后续版本。
3. 最后把 Bingo Worker 纳入同一套 Test Event：Core manifest 使用观察到的 UUID，common/Worker 传递 TEST 能力，远程 Worker 的轮次、旁观、结算和 Auth/代理转服分别验证。

## Event 测试会话模型（Core 首版即包含）

Core 的测试会话模型从第一版就区分：

```text
TestSession
├── mode = SINGLE_GAME
└── mode = EVENT
    ├── TestEventPlan（游戏顺序、地图、轮次、队伍顺序、分区）
    ├── TestRoundCoordinator（倒计时、配对、实例完成、下一轮）
    └── TestEventSettlement（内存 standings/结果，不触碰 Rank/DB）
```

两种模式共用 Bot roster、认证观察、transient teams、SpectatorManager、GameManager ownership 和清理；Event 模式从第一天就使用 16 队×4 的完整名册。单局模式只是 Event 模式中只有一个游戏/一轮的简化计划，不能另外设计一套不支持跨轮状态的会话。

`TestEventPlan` 必须在 Bot/UUID 预检、地图预检和正式启动前一次冻结；轮间不得重新从正式队伍、数据库或当前在线玩家推导名册。任何旧回调都必须携带 session generation + round generation。

## 实现框架与项目位置

### 模块归属

功能按下面的边界拆分：

| 能力 | 项目位置 | 是否进入生产运行 JAR |
| --- | --- | --- |
| 测试会话、单局/Event 编排、transient roster、Core 权限命令 | `championships-core` | 是，进入 `ChampionshipsCore-1.3-SNAPSHOT.jar` |
| 跨 Core/Worker 的 TEST manifest、session/binding 不可变协议对象 | `championships-common` | 随 Core/Worker 各自 shade，只有远程 Bingo 需要时加入 |
| 代理侧测试准入/签名 binding 校验 | `championships-auth-proxy` | 是，进入 AuthProxy JAR；必须保持正常 profile UUID 注入路径 |
| 后端 AuthMe/实际 Bukkit UUID binding 校验 | `championships-auth-bridge` | 是，进入 AuthBridge JAR |
| 64 个真实协议 Bot、动作脚本、连接编排和 CLI | 仓库内独立模块 `championships-test-harness`（首期不加入根 reactor） | 否，独立 CLI/测试工具 JAR |
| 区块/实体压力 | 现有 `championships-bingo-loadtest` | 否，继续保持独立压力插件，不与游戏 Bot 合并 |

根 `pom.xml` 目前已经把 Core、common、Worker、AuthBridge、AuthProxy 和 loadtest 作为独立 Maven module。`championships-test-harness` 的目录可以先放在仓库根下，但第一阶段不要立即加入根 reactor：协议客户端库和认证控制面尚未选定，先以独立 CLI 模块构建，待协议版本、许可证、CI 和隔离代理验证完成后再加入 reactor。它不能依赖 Paper API、Bungee API、AuthMe 或 ProtocolLib，也不能被 shade 进 Core；它必须通过 Minecraft 协议连接测试服务器。

### Core 包结构

生产代码建议放在以下位置，保持当前 `api`、`command`、`configuration`、`listener` 的分层：

```text
championships-core/src/main/java/ink/ziip/championshipscore/
├── api/test/
│   ├── TestSessionManager.java          # 唯一会话 owner，load/unload、generation、生命周期
│   ├── TestSession.java                 # 不可变身份/队伍/连接/实例快照 + 当前阶段
│   ├── TestSessionMode.java             # SINGLE_GAME / EVENT
│   ├── TestRoster.java                  # 16 队、每队 4 席、Bot/开发者替换关系
│   ├── TestIdentityRegistry.java        # 认证后实际 Bukkit UUID 与 session binding
│   ├── TestEventPlan.java               # 游戏顺序、地图、轮数、分区、profile 版本
│   ├── TestEventOrchestrator.java       # 测试 Event 的倒计时、轮间和整场终态
│   ├── TestRoundCoordinator.java        # 一轮配对、实例完成、超时和下一轮
│   ├── TestSettlementSink.java          # 内存结果；禁止 Rank/DB/正式 Event consumer
│   ├── TestGameStarter.java             # 根据 GameTypeEnum 复用 GameManager start surface
│   └── auth/
│       ├── TestAuthBinding.java         # expected UUID、observed UUID、来源、过期与状态
│       └── TestAuthGateway.java         # 受保护的 roster/lease 控制面，不接受客户端 UUID
├── api/game/start/
│   ├── GameExecutionContext.java        # run mode、flow、settlement、owner session、generation
│   ├── GameFlow.java                    # STANDALONE / FORMAL_EVENT / TEST_EVENT
│   ├── SettlementPolicy.java            # NORMAL / FORMAL_RANK / DAILY / TEST_MEMORY
│   └── ...                              # 现有解析/拓扑算法保持在这里
├── api/game/instance/
│   └── BaseGameInstance.java             # 只增加 context/settlement 判断，不放 TestSession 状态
├── command/game/test/
│   ├── TestMainCommand.java
│   ├── TestSingleStartSubCommand.java
│   ├── TestEventStartSubCommand.java
│   ├── TestJoinSubCommand.java
│   ├── TestLeaveSubCommand.java
│   ├── TestStatusSubCommand.java
│   └── TestStopSubCommand.java
└── configuration/config/test/
    ├── TestConfig.java                  # enabled、auth requirement、timeouts、profile directory
    └── TestEventProfileLoader.java      # 只读并校验 test/events/*.yml
```

`TestSessionManager` 应由 `ChampionshipsCore` 构造（在 `GameManager` 构造后即可，但不得在登录监听器首次触发时懒创建），在 `finishManagerBootstrap()` 中先于 `listenerManager` 加载；其 `load()` 只恢复/清理 binding registry，不启动测试。`onDisable()` 中先卸载它并完成 stop/cleanup，再卸载 `gameManager`，最后释放 transient teams 和测试身份。命令层级应严格接入现有树：`GameMainCommand` 添加 `new TestMainCommand()`，而不是让 `CommandManager` 注册一个独立根命令；命令类不直接操作 `GameManager` 的内部 map，所有操作进入 `TestSessionManager`。

不要把测试状态放入 `DailyManager`、`ScheduleManager`、某个游戏 Handler、GUI holder 或 `TeamManager` 的新平行 registry。`TeamManager` 只继续拥有 transient team 的 scoreboard 生命周期；测试会话 owner 负责保存它们并在 dispose 时调用 `removeTransientTeams`。

### 游戏启动抽象

现有 `GameManager` 的方法把 `GameRunMode`、介绍开关、队伍和实例分散在多个 overload 中。为避免再增加一组 `join...ForTest` 平行方法，应引入不可变的 `GameExecutionContext`，但不要用一个裸 `eventStyle` 布尔值混合正式赛程和测试 Event：

```text
GameExecutionContext(
    GameRunMode runMode,             # GAME / EVENT / DAILY / TEST，兼容旧分支
    GameFlow flow,                   # STANDALONE / FORMAL_EVENT / TEST_EVENT
    SettlementPolicy settlement,     # NORMAL / FORMAL_RANK / DAILY / TEST_MEMORY
    UUID ownerSessionId,
    long generation)
```

`showIntroduction` 与 `ArenaSelection` 仍属于 start request，不放进身份/结算上下文。第一步为现有 `GameManager.join...` 保留兼容 overload，并把内部实现收敛到接收 `GameExecutionContext + start options`；第二步让正式 Event 和 TestSession 都通过该入口启动。`BaseGameInstance` 保存 context，并提供三个明确查询：`isFormalEventRun()` 只对正式 Event 为真，`isEventStyleRun()` 对 FORMAL_EVENT/TEST_EVENT 为真，旧 `isEventRun()` 在迁移完成前保持正式 Event 兼容语义。介绍、结果展示、跨轮参与者保持等通用流程改用 `isEventStyleRun()`；ScheduleManager、Rank、EventStateStore 和正式 handler 改用 `isFormalEventRun()`。这样 TEST Event 能获得真实 Event 式玩法，却不会进入正式赛程。

`GameSettlementPolicy` 负责结束回调、结果展示窗口、释放和中止：正式实现委托 `ScheduleManager`，测试实现写入 `TestSettlementSink` 的内存 standings，普通 GAME/DAILY 保持现有策略。`BaseGameInstance` 不再在通用完成路径直接调用 `ScheduleManager`；只有正式策略可以触发正式事件和 Rank 写入。测试 `abortAndReset` 仍然走统一抑制结算路径，正常完成则走游戏自身的结束事件和测试策略。

`BaseGameInstance` 继续拥有游戏任务、玩家名单、旁观、结束和 reset；它不保存 `TestSession` 引用。测试结果由 `TestSettlementSink` 通过受保护的结束回调读取快照，避免游戏 Handler 反向依赖测试编排器。

### Test Event 复用层

`TestEventOrchestrator` 不调用或复制正式 ScheduleManager 的状态机。现有正式 schedule 类把 `ScheduleManager.addRound()`、正式 handler 注册和 Rank 结算写在一起，因此不能直接复用。应先抽取可纯函数化的计划层，再由两个 owner 各自驱动：

* `FormalPairingScheduler`：16 队配对和 standings 重排；
* `StartTargets`、`SubArenaSupport`、`ArenaSelection`：地图副本和分区预检；
* `GameManager` 的 `joinPairedInstances`、`joinMultiTeamInstanceForTeams`、`joinTeamArea`：ownership、预热、可见性和回滚；
* 从当前 package-private `EventRoundSettlement` 提取 `RoundResultWindow`/`RoundResultSnapshot` 等纯结果窗口工具，正式与测试 owner 共同使用；测试不得直接依赖该 package-private 类，也不调用 `RankManager`。

新增 `TestGameRoundAdapter` 注册表按 `GameTypeEnum` 负责“计划 → GameManager 启动 → 完成回调”：普通多队、双队副本使用通用 adapter；Dodgebolt、DragonEggCarnival、SulfurSoccer 等决赛使用明确的两队 adapter；BattleBox、ParkourTag、LaserBox 等当前正式 schedule 自带配对/轮间状态的游戏，必须先把纯 RoundPlan/配对生成抽到 `api/schedule/plan/`，再由测试 adapter 驱动，不能把正式 schedule manager 当作测试 API。

它不注册 `BaseSingleGameSchedule`/正式 `ScheduleHandler`，不调用 `ScheduleManager.addRound()`、`settleEventRound()` 或 `releaseSchedule()`。`Dodgebolt`、`DragonEggCarnival`、`SulfurSoccer` 通过 `TestFinaleAdapter` 明确适配；不把决赛强行转换成普通 16 队 round-robin。

### Core/Auth 身份接入位置

测试身份不能只在 `TestSessionManager` 中保存一个 boolean。建议新增：

```text
championships-core/src/main/java/ink/ziip/championshipscore/api/test/auth/
├── TestAuthBinding.java
├── TestIdentityRegistry.java
└── TestAuthGateway.java
```

`PlayerListener.onPlayerPreJoin` 在调用 `PlayerManager.prepareIdentity(...)` 前，先按实际登录 UUID 查询 `TestIdentityRegistry`：

* 已有有效 binding：进入测试身份路径，禁止生产 `playerDao.synchronizeIdentity`、正式 Redis identity change 和正式 rank cache；
* 没有 binding：继续现有正常玩家路径；
* UUID、用户名、服务器标识、session generation 或过期时间不匹配：拒绝登录或立即踢出，不降级为 OFFLINE UUID。

这里需要修改现有 `championships-core/.../api/player/PlayerManager.java`，增加明确的 `prepareEphemeralTestIdentity`/`clearEphemeralTestIdentity` 边界；不能在 `PlayerListener` 里直接绕过 `PlayerManager`，也不能把测试 UUID 写入现有玩家 DAO。该边界必须保留 `PROFILE_UUID`/`OFFLINE` 的既有校验语义：PROFILE_UUID 只接受 AuthProxy/Paper 实际下发的 profile UUID，OFFLINE 仍校验离线算法，任何不一致都拒绝。

这要求 `TestIdentityRegistry` 在 Bot 连接前已得到服务端认证快照。Core 不能从客户端提交的 UUID 建立 binding。推荐由 `TestAuthGateway` 接收 AuthProxy/身份服务签名的 roster lease；如果当前部署没有这样的控制接口，就必须使用独立测试代理/身份域，并把测试账户 UUID 快照作为启动前受信配置导入 Core。不能声称现有 AuthProxy 已经支持 Core session binding。

首版的控制面握手固定为以下顺序，避免“Core 先猜 UUID、Bot 再覆盖”的反向绑定：

1. `TestSessionManager` 创建 session/generation，向独立测试身份服务申请 64 个一次性 lease。每行包含 `sessionId`、`username`、`serverId`、期望 profile UUID、过期时间和 nonce；由身份服务签名，Core 只保存签名快照。
2. Harness 取得自己的短时连接票据并向同一控制面登记；票据不是 Minecraft UUID，也不能代替 AuthMe 密码或 AuthProxy profile 查询。
3. AuthProxy 在 `PreLoginEvent` 中按 `username + serverId + lease` 查询并验证测试准入，仍执行封禁、维护、撤销、profile 查询，然后沿用现有 `setUniqueId(profile.uuid)`。没有有效 lease 的测试名称直接在代理拒绝。
4. AuthBridge 在 `AsyncPlayerPreLoginEvent` 和 AuthMe `LoginEvent` 校验同一签名 lease、用户名和实际 UUID；Core 的测试验证器也独立校验 session、generation、过期时间和服务器标识。
5. Paper 把登录结果提供的实际 UUID交给 Core。`PlayerListener` 命中测试 binding 后调用新的 `PlayerManager.prepareEphemeralTestIdentity`（只写本次连接缓存/审计，不调用 `playerDao.synchronizeIdentity`），普通玩家仍走原 `prepareIdentity`。AuthMe 成功后才把 binding 标记为 `AUTHENTICATED`，再由会话管理器把 UUID 放入 transient team。
6. 重连必须重新经过 lease 和 AuthMe，并生成新的连接 generation；旧 UUID/name 绑定、过期 lease、重复使用票据和开发者替换出的 Bot 都不能重新占用席位。session stop 时先撤销 Core binding，再通知控制面撤销 lease。

AuthProxy 的代码改动放在 `championships-auth-proxy/src/main/java/ink/ziip/championshipscore/authproxy/test/`，例如 `TestLeaseVerifier` 和 `TestAdmissionProvider`；它只验证测试 lease 后继续使用现有 `LoginProfile.uuid -> setUniqueId`。AuthBridge 的改动放在 `championships-auth-bridge/src/main/java/ink/ziip/championshipscore/authbridge/test/`，例如 `TestLeaseVerifier` 和 `TestAuthMeAdmission`，负责后端实际 UUID、lease 和 AuthMe LoginEvent 的一致性检查。两者都不能允许“测试模式绕过正常 profile、封禁、维护、AuthMe 或 UUID mismatch”。

### Test Harness 项目结构

新模块建议如下（目录为 `/data/ChampionshipsCore/championships-test-harness`，首期用自己的 `pom.xml` 构建，不加入根 `<modules>`）：

```text
championships-test-harness/
├── pom.xml
├── README.md
├── src/main/java/ink/ziip/championshipscore/testharness/
│   ├── TestHarnessMain.java             # CLI，不依赖 Bukkit/Paper
│   ├── auth/TestIdentityClient.java     # 只向身份服务申请/读取 lease，不伪造 UUID
│   ├── protocol/MinecraftBotClient.java # 握手、登录、压缩、KeepAlive、Play 包
│   ├── session/CoreTestSessionClient.java # 与 Core 的受保护控制面
│   ├── roster/BotRoster.java
│   ├── actions/CommonActionProfile.java
│   ├── actions/GameScenario.java
│   └── observe/ConnectionObservation.java
└── src/test/java/ink/ziip/championshipscore/testharness/
    ├── protocol/
    ├── roster/
    └── actions/
```

Harness 不应复用 `championships-bingo-loadtest` 的 `VirtualWalker`；后者继续作为 Folia chunk/entity 压力插件。Harness 的第一版可使用支持当前 Paper 协议的独立协议客户端库，但必须锁定版本并在隔离代理上验证 Login Success、服务端实际 UUID、AuthMe `/login`、Join/Respawn/Disconnect；不能用 MockBukkit 或只发几个位置包宣称完成客户端验证。

### 配置和资源位置

Core 默认资源建议增量加入：

```text
championships-core/src/main/resources/config.yml       # test.enabled、auth、timeout、harness endpoint
championships-core/src/main/resources/message.yml      # test 命令和失败原因
championships-core/src/main/resources/test/events/*.yml # 内置测试 Event profile
```

运行时对应位置：

```text
/home/minecraft/minecraft/cc-core/plugins/ChampionshipsCore/config.yml
/home/minecraft/minecraft/cc-core/plugins/ChampionshipsCore/message.yml
/home/minecraft/minecraft/cc-core/plugins/ChampionshipsCore/test/events/*.yml
```

测试 profile 只描述游戏顺序、地图、轮数、分区和动作 profile，不保存密码、Auth HMAC secret、生产 UUID 迁移或客户端 token。敏感值只来自独立测试身份服务/环境注入，不能进入 YAML、仓库或日志。

### 测试代码位置

Core 的纯模型、计划、生命周期和命令测试放在：

```text
championships-core/src/test/java/ink/ziip/championshipscore/api/test/
championships-core/src/test/java/ink/ziip/championshipscore/command/game/test/
championships-core/src/test/java/ink/ziip/championshipscore/api/game/start/
```

认证 binding 规则测试分别放在：

```text
championships-auth-bridge/src/test/java/ink/ziip/championshipscore/authbridge/test/
championships-auth-proxy/src/test/java/ink/ziip/championshipscore/authproxy/test/
championships-common/src/test/java/ink/ziip/championshipscore/protocol/test/
```

生产代码不得把 Harness client、测试夹具或 `@Test` 类放进 `src/main/java`。涉及真实网络、AuthMe、代理、Paper/Folia、Tab/旁观和跨轮的测试必须作为隔离原生验收脚本/报告保存于 `/tmp`，不把临时连接工具提交到生产插件模块。

## Auth 安全是测试功能的前置边界

测试功能不能提供以下任一“方便模式”：

* Core 命令直接把任意在线用户名加入 Bot roster；
* 客户端提交 UUID，Core 以客户端值覆盖 `Player#getUniqueId()`；
* 给 Bot 临时 `cc.admin`、绕过 AuthProxy/AuthBridge、关闭 AuthMe、关闭 UUID mismatch；
* 在生产 `whitelist` 中加入一个可无限复用的 Bot 通配身份；
* 把测试玩家写入生产玩家/队伍/积分数据库后再尝试删除。

安全的部署边界应是：**测试代码可以运行在 Core，但测试身份和认证数据必须属于独立的测试准入域**。推荐使用独立测试 Core/代理/AuthMe/身份数据库和独立世界；至少使用独立 profile namespace、独立 AuthMe schema 和最小权限 API key。这样 Core 的测试会话仍能覆盖真实认证链路，但不会让生产服务器获得一个永久的认证后门。

### 安全登录流程

1. TestSessionManager 先创建不可猜的 session ID、过期时间和 64 个预注册测试身份清单；清单包含用户名、期望的身份服务 UUID、允许的服务器标识和一次性 session binding，但不接受客户端上传 UUID。
2. Bot Harness 只能拿到自己的短时连接票据；票据用于协调 session，不是 Auth 凭证，也不能替代 AuthProxy 的 profile 查询。
3. Bot 通过真实代理进入。AuthProxy 仍执行 profile 查询、准入、封禁、撤销、维护和 UUID 注入；AuthBridge 仍执行实际 Bukkit UUID 校验和 AuthMe LoginEvent 校验。
4. Core 在 `AsyncPlayerPreLoginEvent`/join 路径读取服务端提供的实际 UUID，和本次 session 预注册身份做一一匹配。未预注册、UUID 不符、名称复用、旧连接代替新连接或 session 过期都拒绝。
5. 只有认证成功后，Core 才把该 UUID 标记为 `AUTHENTICATED_TEST_MEMBER`，创建/更新 transient team。认证前的连接不能参与地图、旁观或 Bot roster。
6. TestSessionManager 结束、超时或中止时撤销 Core 侧 session binding；Core pre-login 必须拒绝后续旧票据。若还要求在代理边缘拒绝，则必须扩展测试身份服务/AuthProxy 的 profile 准入接口，或使用独立测试代理；当前 Core 不能单方面让现有 AuthProxy 感知 session。Bot 断线且不能重新绑定到其他姓名。生产身份服务只保留可审计的测试账号生命周期。

### Core 身份持久化边界

当前 `PlayerListener.onPlayerPreJoin` 会调用 `PlayerManager.prepareIdentity(name, actualUuid)`，而该路径可能执行 `playerDao.synchronizeIdentity` 并发布 Redis identity change。仅使用 transient team 并不能阻止测试 Bot 写入 Core 玩家表。

因此实现必须先增加一个显式的测试身份上下文，再进入 Core 登录处理：

* 测试身份走独立 test identity cache/store，不调用生产 `playerDao.synchronizeIdentity`、正式 Redis identity change 或正式 rank cache；
* Core 仍保存当前连接、实际 UUID、用户名、认证来源、session ID 和过期时间，用于本次会话和审计；
* `PlayerManager`、`PlayerListener`、AuthBridge 的 UUID 检查和退出清理必须共同识别这个上下文，不能只在 TestSessionManager 内另存一份“已认证”布尔值；
* 如果部署选择共享 Core 数据库，必须明确使用独立 schema/namespace；“结束后异步删除测试玩家记录”不作为安全隔离方案；
* transient team、game ownership、visibility session 和测试结果全部在 session dispose 时按 UUID/generation 清理。

AuthMe 也必须使用测试数据库或预置的测试账号记录。Bot 的 `/login` 密码只存在于 Harness 的密钥存储或一次性运行环境，不进入仓库、Core 配置、日志和命令回显。

### 生产安全验收

在允许开启测试会话前，必须验证：

| 场景 | 预期 |
| --- | --- |
| 未启动 TestSession 的 Bot | AuthProxy/测试准入域拒绝，不能进入 Core |
| 生产用户名冒用 Bot 名称 | profile UUID 或 AuthMe 身份不符，拒绝 |
| 客户端提交伪造 UUID | Core 忽略客户端值，只使用服务端 `Player#getUniqueId()` |
| session 过期/重复使用票据 | 代理或 Core 拒绝，不能加入队伍 |
| AuthProxy 不可用 | 按现有缓存策略处理，未知测试身份拒绝，不生成离线 UUID |
| AuthBridge UUID mismatch | pre-login/LoginEvent 拒绝并记录原因 |
| 测试结束后再次连接 | 没有新 session binding 时拒绝 |
| 测试 Bot 访问普通管理命令 | 没有管理权限；测试身份仅获得最小玩家权限 |
| 测试结束/中止 | Core 生产 players/team_members/player_points/Redis/rank 无新增记录 |
| 生产 Core 未配置测试域 | 测试命令不可用，不能靠运行时参数打开认证后门 |

## 本次方案验证结果（2026-10-29）

### 已由源码和自动化测试确认

* Core 源码扫描到 141 处 `GameRunMode.EVENT`/`isEventRun()`/正式 settlement 相关引用。正式 Event 确实不是普通开局的一个参数；Test Event 必须拥有独立的轮次协调和结束事件接收边界。
* `BingoManifestFactory` 当前只把 `GAME`、`EVENT`、`DAILY` 映射到 common `MatchRunMode`，远程 Bingo 增加 TEST 前必须同时修改 Core、common、Worker 和协议能力/版本处理。
* Core 的 `EventStartCommandTest` 23 项、`AllTeamsStartCommandTest` 9 项、`GameStartServiceTest` 32 项和 `GameStartArgumentsTest` 6 项顺序执行全部通过，共 70 项，无 failure/error/skipped。
* AuthBridge 的 `AccessListenerTest` 12 项、`BridgeUuidResolverTest` 4 项，AuthProxy 的 `ProxyLoginResolverTest` 3 项、`ProxyAccessStateTest` 4 项全部通过，共 23 项，无 failure/error/skipped。
* `python3 scripts/check-source-layout.py` 通过，检查 1132 份 Java 源码布局。
* 当前工作树根目录执行 `mvn -B -ntp -o test` 全 reactor 通过，共 1311 项测试，0 failure、0 error、0 skipped（Core 1067、Worker 74、AuthBridge 24、AuthProxy 13、common 48、platform 65、Redis 5、Bingo engine 6、loadtest 9）。日志中的个别 `SEVERE`/异常栈来自专门验证失败磁盘写入和配置缺失的测试场景，测试结果仍全部通过。
* AuthProxy 源码确认允许登录时由代理执行 `setUniqueId(profile.uuid)`；Paper/Core 继续读取实际 `AsyncPlayerPreLoginEvent.getUniqueId()`/`Player#getUniqueId()`。Core 登录路径确实会调用 `PlayerManager.prepareIdentity`，所以测试身份上下文必须在登录持久化前接入。
* `GameManager` 当前在配对启动、内部 arena 选择、spectator focus、round transition 和 remote Bingo 路径中直接判断 `runMode == EVENT` 或 `isEventRun()`；因此 `TEST` 不能只加枚举，也不能把 `isEventRun()` 粗暴改成包含 TEST。
* 当前命令树是 `CommandManager -> GameMainCommand -> GameStartMainCommand/GameStopSubCommand`；测试命令应挂在 `GameMainCommand` 下，原方案中“CommandManager 直接注册 TestMainCommand”的位置不正确。
* `EventRoundSettlement` 当前是 `api.schedule` package-private 类，且 `BinaryProtocolCodec` 以 `runMode.name()` 字符串编码；原方案中直接复用该类、以及“枚举序号必须追加”的表述均已修正为提取纯结果窗口工具和升级协议版本/能力协商。
* `finishManagerBootstrap()` 目前先加载 `listenerManager`、后加载 `gameManager`；测试身份 registry 必须先构造并在 listener 可接收登录前加载，`onDisable()` 则要先停止测试会话再卸载 GameManager。

### 尚未被本次验证覆盖

* 尚未新增 `GameRunMode.TEST`、TestSessionManager、TestEventOrchestrator 或测试身份上下文，因此本次测试不能证明目标功能已经实现。
* 尚未使用真实 64 个协议 Bot 通过 AuthProxy/AuthBridge/AuthMe 登录，也未观察代理下发 UUID、Core pre-login、重连和 session 过期拒绝。
* 尚未在 Paper/Folia 上验证真实 16 队×4、开发者换位、旁观互见、完整 Tab、Event 跨轮和第二局回收。
* 尚未验证远程 Bingo TEST manifest、Worker 侧 TEST 模式、Redis/数据库零写入和跨服转移。

### 根据验证修正后的方案边界

1. Core 首版同时实现 `single` 与 `event` 两种 TestSession；不能把 Event 推迟到后续版本。
2. Test Event 不复用正式 `ScheduleManager` 的状态、handler、Rank 或 `EventStateStore`，但必须复用已验证的配对、地图预检和 GameManager ownership 入口。
3. `GameRunMode.TEST` 需要 `GameExecutionContext(GameFlow + SettlementPolicy)`；通用 Event 式流程使用 `isEventStyleRun()`，正式 schedule/Rank 使用 `isFormalEventRun()`，不能只把所有 `isEventRun()` 改成包含 TEST。
4. Auth 安全实现分为两层：Core pre-login 的 session binding 是最低要求；若要求 Bot 在代理边缘即被拒绝，必须扩展测试身份服务/AuthProxy 或部署独立测试代理，Core 不能单方面实现。
5. 真实 Bot/Auth/Paper/Folia 验收是独立门槛。当前全 reactor 的 1311 项测试只能证明现有基础入口和身份规则未被破坏，不能替代目标功能的原生验收。
6. Harness 首期放在 `/data/ChampionshipsCore/championships-test-harness`，但不立即加入根 Maven reactor；协议客户端和测试准入控制面验证通过后再纳入统一构建。

## 现有代码与逻辑的破坏性影响审计

本次审计没有发现已经落入生产代码的测试功能改动：当前工作树除原有 Bingo 相关用户修改外，本方案只新增了本调研文档，没有修改 Core、Worker、AuthBridge、AuthProxy 的 Java、资源、JAR 或运行配置。因此当前服务器行为没有被本方案改变，也没有执行部署或重启。

对“以后加入 TEST”这一步，破坏性风险如下：

| 位置 | 现状 | 直接加 TEST 的影响 | 必须的兼容措施 |
| --- | --- | --- | --- |
| `GameRunMode` | Core 内部 enum，现有普通调用默认 `GAME` | `BingoManifestFactory` 的穷举 switch 会先编译失败；其他 `== EVENT` 分支会把 TEST 当普通局，导致测试 Event 不推进轮间 | 先加 context/strategy 和编译期穷举测试，再逐个迁移；旧 overload 继续映射为原语义 |
| `BaseGameInstance` | `isEventRun()` 同时参与介绍、旁观焦点、结果窗口、正式结束通知 | 若把 `isEventRun()` 改成包含 TEST，会把正式 Schedule/Rank/Event consumer 误接入；若不改，TEST Event 会缺少 Event 式流程 | 保留 `isFormalEventRun()`，新增 `isEventStyleRun()`；结束流程由 `SettlementPolicy` 接管 |
| `GameManager` | 多处直接判断 `runMode == EVENT`，包括 arena 选择、spectator focus、round hold、force-end 和远程 Bingo | TEST Event 可能不锁定 arena、不转移旁观、不保留轮间参与者，`forceEndEventAreas` 也不会停止 TEST | 新增 context overload；正式路径保持原判断，测试路径由 `TestSessionManager` 按 session/generation 停止和释放 |
| 正式 ScheduleManager/Handler | 直接调用 `ScheduleManager.addRound()`、`settleEventRound()`、`releaseSchedule()` | 复用正式 handler 会污染正式赛事状态或产生 Rank/Redis 写入 | 只复用纯计划/结果算法；Test adapter 不注册正式 handler |
| Bingo Core | `BingoStartRequest` 只在 `runMode == EVENT` 时保留介绍；manifest switch 只覆盖 GAME/EVENT/DAILY | TEST Event 会丢失介绍和轮间展示；加入 enum 后 manifest 工厂先无法编译 | Bingo 使用 `GameExecutionContext`；远程 TEST 在 Core/common/Worker 同步支持前明确拒绝 |
| common/Worker 协议 | `MatchRunMode` 只有 GAME/EVENT/DAILY；codec 按名称编码，协议版本固定为 8 | 旧 Worker 不认识 TEST；若只改 Core，可能把 TEST 当 GAME 或拒绝 manifest | 提升协议版本、增加 flow/settlement 能力并做 capability gate；旧 Worker 继续只接受原三种模式 |
| `PlayerListener`/PlayerManager | 每个允许登录都会调用 `prepareIdentity`，可能同步 Core 玩家 DAO/Redis | 若只在 TestSession 内保存标记，64 个 Bot 仍会写入正式玩家身份；若放宽 UUID 检查会形成认证后门 | 在调用 `prepareIdentity` 前命中严格 binding，使用 ephemeral identity path；普通玩家路径字节级保持原逻辑 |
| AuthProxy/AuthBridge | AuthProxy 设置 profile UUID；Bridge 在 pre-login/LoginEvent 校验实际 UUID/AuthMe | 添加测试白名单或接受客户端 UUID会绕过现有安全边界 | 使用签名 lease/独立测试身份域；继续执行 profile、封禁、维护、AuthMe 和 UUID mismatch 检查 |
| `TeamManager` transient team | 已有 transient team 只隔离 scoreboard/正式 DAO，不隔离登录身份 | 直接拿它承载 Bot 会造成身份持久化和旧队伍冲突；成员替换也可能在开局后破坏名册 | 认证完成后才创建；会话 owner 固定 roster；只允许准备阶段替换席位，结束按 generation 清理 |
| 命令层 | 现有 `/cc game start`、`/cc event start` 逻辑稳定 | 新命令若挂错根节点或复用普通 start，会影响帮助、权限或正式队伍 | 只在 `GameMainCommand` 下新增 `test`，使用独立 `cc.test`，不改现有 start/event 命令 |

当前源码中特别需要先处理的编译/行为点包括：`BingoManifestFactory` 对 `GameRunMode` 的穷举 switch、`BingoStartRequest` 的 Event-only introduction 归一化、`GameManager` 中约 30 处直接 Event 判断、`BaseGameInstance` 的正式 ScheduleManager 结束回调，以及 Worker/Core 对 `MatchRunMode.EVENT` 的直接比较。它们是首轮实现的阻断清单，而不是可以靠默认分支放过的兼容细节。

兼容实现顺序应固定为：先新增纯模型和策略单元测试；再引入 context 但让所有旧入口产生与当前完全相同的 GAME/EVENT/DAILY context；随后只接入 Core 本地 TEST；通过无正式数据写入和生命周期回归后，才升级 common/Worker 协议并开启远程 Bingo。任何一步失败都必须让测试功能不可用，而不能回退到普通 GAME 或 OFFLINE UUID。
