# Bingo 跨服拆分架构

最后核对：2026-08-30（源码基线 `d703939`，协议 v5）

## 模式与启用原则

远程 Bingo 由 ChampionshipsCore 控制面、Folia Worker、Redis Streams 和代理转服链路组成。`LOCAL` 适用于单服执行，`REMOTE` 适用于将 Bingo 世界与玩法负载隔离到独立 Folia 服务器的部署。新部署应先以 `LOCAL` 验证基础玩法，再在维护窗口切换到 `REMOTE`；不得在比赛运行中热切换执行器。

协议、计分、Redis、outbox、Worker 展示和目标观察均有自动化测试覆盖。项目还执行过 64 个逻辑玩家、移动区块加载和约 1 万至 2 万实体的 Folia 压力测试；结果与推荐参数见 [Bingo 64 人 Folia 性能指南](bingo-64-player-performance-report.md)。该测试不包含 64 个真实 Minecraft 连接，也不覆盖代理转服、Redis/MariaDB 故障恢复和完整比赛结算。生产部署前必须按本文的上线顺序完成端到端验收。

## Maven 模块

| 模块 | 职责 |
| --- | --- |
| `championships-common` | 无 Bukkit 依赖的协议 v5、manifest、命令/事件、生命周期、路由契约、确定性 ID 和共享展示策略 |
| `championships-bingo-engine` | 无 Bukkit 依赖的任务完成排序、格子/连线计分和结果哈希 |
| `championships-platform-bukkit` | Paper/Folia Scheduler、任务事件判定、世界规则、玩家状态、队伍投影、聊天展示、散布、初始装备和常驻效果共享实现 |
| `championships-redis` | Redis Streams 发布、consumer group、pending reclaim、DLQ、比赛 transport 和跨服公共聊天 |
| `championships-bingo-worker` | 独立 Folia 执行插件，拥有世界、实体、背包观察、任务菜单和实时玩法 |
| `championships-bingo-loadtest` | 仅供可丢弃世界使用的一次性 Folia 区块/实体压测插件；不参与正式玩法 |
| `championships-core` | ChampionshipsCore 主插件；负责赛程 ownership、事件重放、积分和数据库持久化 |

根目录 `mvn clean package` 生成：

```text
target/ChampionshipsCore-1.3-SNAPSHOT.jar
championships-bingo-worker/target/championships-bingo-worker-1.3-SNAPSHOT.jar
championships-bingo-loadtest/target/championships-bingo-loadtest-1.3-SNAPSHOT.jar
```

Core 的历史制品路径保持不变；Worker JAR 已包含 Redis 客户端及所有内部模块，不需要把 common/engine/platform/redis 作为独立插件安装。LoadTest JAR 只应在维护期临时安装，测试结束后移出 `plugins/`；具体保护线和已知模型偏差见 [LoadTest README](../championships-bingo-loadtest/README.md)。

## 权威边界

ChampionshipsCore 是唯一控制面和正式积分权威。它负责：

- 生成卡片并冻结完整 `MatchManifest`；
- 冻结队伍、名册、开局在线状态、计分规则、倒计时、散布、PvP 保护和常驻效果；
- 冻结当前 `message.yml`、Bingo 语言文本以及任务名/描述的 Adventure 富文本展示快照；
- 保留 `teamStatus`、`playerStatus`、观战和赛程结束事件语义；
- 按严格事件序列独立重放 Worker 的完成观察；
- 校验最终结果哈希后，以确定性事务 ID 写入积分。

Worker 只拥有执行面：世界、实体、背包/进度/统计观察、玩家 UI 和本地 tick。它不能直接访问 ChampionshipsCore 数据库，也不能决定正式积分。

任务事件判定、世界规则、玩家状态清理、初始装备、常驻效果、旁观状态、安全散布、原生队伍与聊天展示位于共享 Bukkit 平台模块；规则介绍时间线、计时格式和排名窗口位于 common，本地 Bingo 与 Worker 使用同一实现。计分、排名和胜者判定位于纯 Java engine，Core 与 Worker 各运行一份，避免复制玩法规则。Worker 不携带第二份 Bingo 玩法或语言配置：开局时 Core 将当前场地配置、`message.yml`、Bingo 语言文本与任务富文本冻结到 manifest。

## 生命周期与路由

```text
Core: freeze manifest -> PREPARE ----------------------+
                                                      |
Worker:                PREPARING -> READY ------------+
                                           Core routes players
Worker:                PLAYER_ARRIVED ... ------------+
                                           Core START_COMMIT
Worker:                COUNTDOWN -> RUNNING -> FINISHED
                              events + heartbeat |
Core:                        replay + verify ----+-> score -> schedule event
Worker:                                                -> route everyone to Core
```

合法状态主路径为：

```text
CREATED -> PREPARING -> READY -> ROUTING -> COUNTDOWN
        -> RUNNING -> SETTLING -> FINISHED
```

所有非终态可进入 `ABORTED`。`matchId + epoch` 是 fencing token，旧 epoch 的命令和事件不能改变当前比赛。

创建 manifest 时在线的选手标记为 `requiredAtStart`，只有他们会阻塞到达屏障；完整离线名册仍保留用于团队奖励，并可在比赛中上线后按 Core ownership 路由进入 Worker。无任何在线选手时 Core 拒绝开局。

代理路由使用标准 BungeeCord `Connect` Plugin Message。BungeeCord 使用 `BungeeCord` channel；Velocity 需启用其 BungeeCord 兼容 channel，也可配置 `bungeecord:main`。Plugin Message 只提出转服请求，真正的到达确认来自 Worker 的 `PLAYER_ARRIVED` 事件。

断线/直连恢复规则是：

| 玩家连入位置 | 比赛状态 | 处理 |
| --- | --- | --- |
| Core | Worker 尚在 `PREPARING` | 留在 Core，等 Worker `READY` |
| Core | `READY` / `ROUTING` / `COUNTDOWN` / `RUNNING` | 按 manifest ownership 送往 Worker |
| Worker | 比赛非终态且拥有该玩家 | 恢复玩家或旁观状态；倒计时/开局后使用 Worker 记录的断线位置，保留背包和任务基线，不重新散布 |
| Worker | 无活动 ownership 或比赛已结束 | 直接返回 Core 服务器 |

动态旁观先由 Worker 确认 `SPECTATOR_ADDED` 后 Core 才转服，避免 Redis 命令与代理连接的竞态。

## 本地/远程玩法对齐

规则介绍与开局流程与 Local 相同：

```text
规则介绍（Adventure/Spectator，无卡片、无开局装备）
-> 场地准备（Adventure，清理状态）
-> 生成卡片 + 发放装备 + Survival + 安全散布
-> 最后 5 秒（冻结位移）
-> RUNNING
```

因此规则介绍期间不会提前刷新 Bingo Card，也不会提前切换为生存模式。介绍模式、介绍/旁观坐标、准备时间和最后倒计时都是 manifest 的不可变快照。

Worker 自己维护一个 Adventure Component 记分板，展示剩余时间、前四名队伍分数和完成格数；标题与行模板来自 Core 当前 Bingo 语言文件。旁观者可持有每支队伍的卡片并切换查看，使用共享旁观状态（飞行、无碰撞、无敌、无限夜视）。Local 和 Worker 的三个 Bingo 维度均设置 `IMMEDIATE_RESPAWN=true`。

## Redis 与持久化保证

键约定：

```text
<namespace>:bingo:commands:<workerId>
<namespace>:bingo:events
<namespace>:bingo:manifest:<matchId>:<epoch>
<namespace>:core:data-sync
<namespace>:chat:global
```

`<namespace>:chat:global` 承载 Core 与 Worker 的普通公共聊天。每个实例使用基于稳定实例 ID 的独立 consumer group，因此在线实例都能收到每条新消息；实例 ID 重复会导致同组竞争消费。聊天消息包含发送者 UUID、名称、展示标签、队伍颜色、活动选手状态和 Adventure JSON，接收端必须按共享 `CrossServerChatText` 渲染。发送实例通过本服聊天事件显示原消息并忽略流内回声；超过 30 秒的消息会直接确认丢弃，不做离线补发。队伍私聊不写入该公共流。

消费语义为至少一次：

- consumer group 顺序处理单场消息；
- 处理成功后才 `XACK`；
- `XAUTOCLAIM` 接管失联消费者的 pending 消息；
- 格式错误或超过最大投递次数的消息进入 DLQ；
- Worker 事件先写入磁盘 outbox，Redis 恢复后按序重放；
- Core 的 inbox 与 match 状态在同一 MariaDB 事务中提交；
- Core 只接受连续 `eventSeq`，任务观察另有连续 `completionSeq`；
- 最终 `resultHash` 一致后才提交正式积分。

Redis 连接现在由 Core 顶层统一管理，远程 Bingo 不再拥有单独的一份连接配置。每台 Core
使用持久且唯一的 `instance-id` 建立自己的消费组，因此队伍、成员、玩家身份、积分和轮次变更会
广播给所有 Core，而不是被多个服务器分摊消费。同步消息只携带版本化失效域，接收端始终从共享
MariaDB 重载权威数据；每 30 秒的完整对账用于弥补提交后发布失败或 Redis 暂时中断。远程 Bingo
对局也记录所属 Core 实例，某台 Core 重启时只回收自己的孤儿对局。

积分事务 ID 为：

```text
UUIDv5(matchId + epoch + completionSeq + playerUuid + awardKind)
```

因此 Redis 重投不会重复计分。Worker 每 5 秒发心跳；Core 默认 20 秒未收到连续事件或心跳即终止比赛、清理 ownership 并尝试向 Worker 发布 ABORT。

## Worker 玩法能力

Worker 提供以下能力：

- Folia global/region/entity/async Scheduler 分工；
- 三维度加载、玩家安全散布、倒计时和 PvP 保护期；
- 与本地 Bingo 相同的队伍色装备、附魔、鞘翅、食物、工具和武器；
- 常驻效果自愈、死亡不掉落和重生恢复；
- 物品、药水、物品集合、进度和统计任务观察；
- 同队伤害取消；
- 与本地 Bingo 共用图像资源和颜色匹配器的动态地图任务卡；
- 地图或指南针右键打开详细只读任务菜单，指南针左键选择在线队友跨 region 传送；
- 由 Core 冻结并随 manifest 下发的规则介绍、菜单、聊天、Title、BossBar 和任务富文本；
- 最后倒计时的移动/交互保护，以及观众全程的交互/伤害保护；
- 无外部记分板插件时的自管理侧边栏；
- 中途加入/重连选手、动态观众、无限夜视和结算后全员返回 Core 服务器。

Worker 的自然世界属于一次性比赛槽。生产配置会在结算后先把全员送回 Core，确认后端无人后停止 Folia；`session.lock` 释放后旧存档会被原子移出，Worker 使用当前 Java 命令启动替代进程，旧存档由新进程后台删除。开发环境只有明确启用 `allow-reuse-without-reset` 才会跳过此流程。

## 配置

以下配置使用示例服务名和 Redis 主机名。部署时必须将它们替换为代理与基础设施中的实际值。

Core `plugins/ChampionshipsCore/config.yml`：

```yaml
redis:
  enabled: true
  # 每台 Core 必须唯一；auto 会在各自插件数据目录持久化生成。
  instance-id: "auto"
  uri: "redis://redis-host:6379/0"
  namespace: "championships"
  consumer-group-prefix: "championships-core"
  stream-max-length: 100000
  block-timeout-ms: 2000
  reclaim-idle-ms: 15000
  max-deliveries: 8
  reconciliation-seconds: 30

bingo:
  execution-mode: "REMOTE"      # 远程执行示例；首次联调前保持 LOCAL
  worker-id: "bingo-1"          # 必须与 Worker 一致
  worker-server: "bingo-worker" # 代理配置中的 Worker 服务名
  proxy-channel: "BungeeCord"
  ready-timeout-seconds: 30
  arrival-timeout-seconds: 45
  heartbeat-timeout-seconds: 20
```

Worker `plugins/ChampionshipsBingoWorker/config.yml`：

```yaml
enabled: true
worker-id: "bingo-1"
redis:
  uri: "redis://redis-host:6379/0"
  namespace: "championships"
  consumer-group: "bingo-workers"
  stream-max-length: 100000
  block-timeout-ms: 2000
  reclaim-idle-ms: 15000
  max-deliveries: 8
proxy:
  channel: "BungeeCord"
  return-server: "core"         # 示例：代理中的 Core 服务名
worlds:
  overworld: "bingo"
  nether: "bingo_nether"
  the-end: "bingo_the_end"
  # 生产环境保持 false；下一场前必须恢复干净世界或重建实例
  allow-reuse-without-reset: false
```

倒计时、散布、PvP、常驻效果、阶段坐标、介绍模式和展示文本不在 Worker 配置中重复出现，它们由 Core 的 Bingo 场地配置、`message.yml` 与 Bingo 语言文件冻结后随 manifest 下发。快照属于协议 v5，因此 Core 与 Worker 必须成对升级，并通过新建比赛生成新的 manifest。

## 世界与容量模型

每个 Worker 进程只接受一个同时运行的比赛，三个世界共同组成一个物理 slot。每局结束后 Worker 使用原进程的 Java 命令启动替代进程并自动重建该 slot。不要启用生产世界复用，否则已采集资源和玩家修改会污染下一局。

Folia 解决的是相互远离 region 的 tick 并行，不会消除首次生成新区块的成本。参考压测表明：即使总 CPU 仍有余量，玩家和实体集中在单个 hot region 时也可能先将该 region 压到低 TPS。64 人生产世界必须预生成目标活动半径、设置合理 world border，并同时观察 region TPS/MSPT、区块加载延迟和实体分布，不能只看进程 CPU 或全服平均 TPS。

## 上线顺序

1. 建立 Redis，并限制只允许 Core 与 Worker 网络访问。
2. 创建独立 Folia 实例，建议 `level-name=bingo`，准备 `bingo`、`bingo_nether`、`bingo_the_end` 三维度及预生成范围。
3. 在代理中注册 Core 和 Bingo Worker 服务；将 Worker 设为不可直接选择，连接失败回退到 Core。
4. 安装 Worker JAR，保持 `enabled: false` 启动一次生成配置；核对后启用。
5. 先保持 Core `execution-mode: LOCAL`，验证 Worker、Redis 和代理日志均健康。
6. 在维护窗口切到 `REMOTE`，先用 1 支测试队伍验证 READY、转服、任务、结算和返回。
7. 依次演练 Worker 崩溃、Core 崩溃、Redis 中断、玩家中途掉线、重复事件和代理目标不可达。
8. 先按性能指南复现逻辑玩家压力，再用 64 个协议客户端或真人压测 CPU、region MSPT、区块生成、Redis pending、数据库写入和转服到达时间。
9. 全部通过后才用于正式赛事；任一阶段异常可切回 `LOCAL`，本地 Bingo 路径仍完整保留。

## 生产验收清单

- Folia 下跨维度传送、指南针跨 region 传送和三维度 Portal 行为；
- Redis 断线、pending reclaim、磁盘 outbox 重放和 DLQ 告警；
- BungeeCord 与 Velocity 两套代理的 Connect channel、掉线回退与重连；
- MariaDB inbox、确定性积分事务和 Core 重启孤儿 fencing；
- 16 支四人队伍、64 个真实连接的完整一局，以及自动停服、删档、拉起后再开一局；
- 代理、Redis、MariaDB 与 Worker 在压力下的组合故障和恢复。
