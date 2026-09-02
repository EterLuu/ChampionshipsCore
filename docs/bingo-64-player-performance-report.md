# Bingo 64 人 Folia 性能指南

本文面向部署和运维 ChampionshipsCore 远程 Bingo 的管理员，提供 64 名参赛者的参考压测结果、推荐配置和验收方法。文中的数值基于参考测试环境，为不同硬件和地图组合提供容量规划起点。

与本文配套的文档：

- [Bingo 跨服拆分架构](./bingo-remote-architecture.md)
- [Bingo Worker 部署与排障](../championships-bingo-worker/README.md)
- [Bingo LoadTest 使用说明](../championships-bingo-loadtest/README.md)

## 1. 结论摘要

参考测试完成了 64 个逻辑加载者、32 blocks/s 飞行、约 10,000 个世界实体目标的 12 分钟压力测试。实体数在布局切换期间最高到达 21,425，测试期间未发生 Watchdog、区块加载失败或实体生成失败。

参考测试验证了测试服务器在该模型下的基础生存性，并揭示了以下容量边界：

- 区块异步加载 P95/P99 约为 30 秒，高速移动时的玩家体验需要进一步优化；
- 布局切换使已加载实体数在约 2,000–21,425 之间振荡，10,000 个实体的稳态行为需要通过真实客户端验证；
- 主要负载集中于一个 Folia region，局部 TPS 在总 CPU 仍有空闲时可能先失速；
- 逻辑加载者的模型覆盖区块与实体负载，真实玩家的网络、实体追踪、背包、UI、Redis 和积分重放开销需通过端到端验收覆盖；
- 参考压测使用 8 个宏观锚点，64 人生产模型使用 16 队布局。

参考测试结果分为两部分：

- **基础生存性通过**：12 分钟无崩溃、无加载/生成失败，分批清理可在数秒内完成；
- **生产验收待执行**：在预生成的干净世界上，使用 16 队、64 个真实协议客户端执行完整比赛与故障演练。

## 2. 参考测试条件

性能数据必须与软件版本和线程预算一起解读。参考测试使用了以下基线：

| 项目 | 参考值 |
| --- | --- |
| Java | OpenJDK 25 |
| Minecraft / Folia | 26.1.2 / `26.1.2-8` |
| ChampionshipsCore / Worker / LoadTest | `1.3-SNAPSHOT` |
| 服务器容量 | `max-players=80` |
| 队伍上限 | 4 人，生产模型为 16 队 × 4 人 |
| Netty I/O 线程 | 2 |
| Chunk worker / I/O 线程 | 4 / 2 |
| Folia tick 线程 | 4 |
| Folia scheduler / grid exponent | `EDF` / `4` |

复现或比较结果时，还应记录：

- JVM 可见 CPU 数量与 cgroup CPU quota；
- `-Xms` / `-Xmx`、GC 算法和 direct memory 上限；
- 磁盘类型、随机读写延迟和世界是否预生成；
- 同机其他服务、代理、Redis 和数据库的资源竞争；
- 插件列表、配置版本和测试地图快照标识。

容量对比应记录并控制上述条件。

### 2.1 Paper/Folia 基线

`config/paper-global.yml`：

```yaml
chunk-loading-advanced:
  auto-config-send-distance: true
  player-max-concurrent-chunk-generates: 0
  player-max-concurrent-chunk-loads: 0
chunk-loading-basic:
  player-max-chunk-generate-rate: 20.0
  player-max-chunk-load-rate: 60.0
  player-max-chunk-send-rate: 45.0
chunk-system:
  io-threads: 2
  worker-threads: 4
threaded-regions:
  grid-exponent: 4
  scheduler: EDF
  threads: 4
watchdog:
  early-warning-delay: 10000
  early-warning-every: 5000
```

`server.properties`：

```properties
max-players=80
view-distance=10
simulation-distance=10
use-native-transport=true
sync-chunk-writes=true
```

`spigot.yml`：

```yaml
settings:
  bungeecord: true
  netty-threads: 2
world-settings:
  default:
    mob-spawn-range: 8
    entity-activation-range:
      animals: 32
      monsters: 32
      raiders: 64
      misc: 16
      water: 16
      villagers: 32
      flying-monsters: 32
    entity-tracking-range:
      players: 128
      animals: 96
      monsters: 96
      misc: 96
      display: 128
      other: 64
```

`config/paper-world-defaults.yml`：

```yaml
chunks:
  delay-chunk-unloads-by: 5s
  max-auto-save-chunks-per-tick: 16
  prevent-moving-into-unloaded-chunks: true
entities:
  spawning:
    per-player-mob-spawns: true
    count-all-mobs-for-spawning: false
```

Paper 世界配置中的 `spawn-limits: -1` 和 `ticks-per-spawn: -1` 表示继承 `bukkit.yml` 的最终生效值。调整任何生成上限前，应先确认继承结果。

## 3. 压测模型

参考 12 分钟测试的 LoadTest 配置如下：

```yaml
view-distance: 10
team-anchor-radius-blocks: 1536
stationary-player-separation-blocks: 384
entity-minimum-spawn-distance-blocks: 24
entity-maximum-spawn-distance-blocks: 128
layout-switch-interval-seconds: 60
stationary-dispersal-speed-blocks-per-second: 7.0
stage-walkers: [64]
stage-duration-seconds: [720]
stage-modes: [mixed]
stage-speed-blocks-per-second: [32.0]
stage-target-world-entities: [10000]
max-concurrent-loads: 384
max-submissions-per-tick: 96
entity-spawns-per-tick: 20
```

行为模型：

- 32 个逻辑加载者以 32 blocks/s 持续飞行；
- 32 个逻辑加载者每 60 秒在集中和分散布局间切换；
- 分散时以 7 blocks/s 移向各自目标；
- 实体按面积均匀分布在停留者 24–128 格的距离带内；
- 实体使用 `NATURAL` 生成原因、开启 AI，每 80 个槽位包含 70 个敌对生物和 10 个被动生物；
- 敌对生物包含僵尸、骷髅、蜘蛛和苦力怕，被动生物包含牛、羊、猪、鸡、兔和山羊；
- 不使用村民，避免将测试退化为单一村民 AI/POI 压力；
- 停止后分批移除插件管理的实体和 chunk ticket。

### 3.1 与真实比赛的差异

| 模型差异 | 对结果的影响 |
| --- | --- |
| 没有真实玩家实体和客户端 | 未覆盖网络发包、实体追踪、皮肤、玩家列表、碰撞和协议编码成本 |
| 不执行完整 Bingo 逻辑 | 未覆盖背包/统计观察、任务菜单、地图卡、Redis 事件和积分重放成本 |
| 按 8 个宏观锚点组织 | 与 16 队 × 4 人的 region 分布不一致 |
| 使用插件异步区块 API | 不完全经过 Paper 的 per-player 加载与发送限流 |
| 重复使用压力世界 | 已生成区块和残留实体会随布局重新加载，放大实体数振荡 |
| 飞行者持续直线移动 | 区块压力比多数真实玩家更极端，但未模拟转弯、传送、跨维度和死亡重生 |

LoadTest 适合发现区块与实体上限，不得单独作为正式比赛验收工具。

## 4. 参考测试结果

LoadTest 默认将结果写入服务器目录下的 `plugins/ChampionshipsBingoLoadTest/results.jsonl`，同时在标准服务器日志中输出 `CHUNK_STRESS` 记录。以下表格是开发期间的参考迭代结果：

| 编号 | 主要目标/布局 | 时长 | 结果 | 实体峰值 | 最低调度 TPS | 关键观察 |
| ---: | --- | ---: | --- | ---: | ---: | --- |
| 1 | 初始多阶段区块加载 | 383 s | 队列保护停止 | — | — | 早期请求回收不足，有效队列达到保护线 |
| 2 | 停留/实体生成 | 480 s | 完成 | 2,302 | — | 实体未达目标，加载 P95 约 30 s |
| 3 | 改进实体生成 | 481 s | 完成 | 10,565 | — | 达到约 10k 实体，最大加载延迟 125 s |
| 4 | 8/32/64 混合阶段，32 b/s | 180 s | TPS 保护停止 | 9,044 | 2.96 | 早期布局形成热点 |
| 5 | 64 加载者，集中布局，目标 16k | 300 s | 完成 | 16,312 | 12.70 | 集中 16k 可生存 5 分钟 |
| 6 | 64 加载者，集中布局，目标 32k | 94 s | TPS 保护停止 | 19,917 | 2.45 | 集中约 20k 实体不可持续 |
| 7 | 64 加载者，分散布局，目标 20k | 75 s | TPS 保护停止 | 16,935 | 1.23 | 新区块与实体压力叠加时更早失速 |
| 8 | 64 加载者，每分钟切换，目标 10k | 720 s | 完成 | 21,425 | 11.11 | 加载和实体生成均无失败，但未维持 10k 稳态 |

### 4.1 12 分钟测试详细指标

| 指标 | 结果 | 评价 |
| --- | ---: | --- |
| 运行时间 | 720.305 s | 完整完成 |
| 布局切换 | 11 次 | 覆盖 6 个集中窗口和 6 个分散窗口 |
| 完成区块加载 | 90,564 | 高强度区块流量 |
| 区块加载失败 | 0 | 通过 |
| 实体生成失败 | 0 | 通过 |
| 过期请求 | 881,347 | 高速移动下大量窗口请求在执行前已失效 |
| 有效队列峰值 | 19,246 | 未触发 60,000 保护线，但仍偏高 |
| 原始队列峰值 | 44,132 | 请求产生速度显著高于消费速度 |
| 最大并发加载 | 384 | 长时间满载 |
| 加载平均延迟 | 2.922 s | 偏高 |
| 加载 P95/P99 | 30 s / 30 s | 不符合玩家体验要求 |
| 最大加载延迟 | 41.6 s | 不符合玩家体验要求 |
| 最低调度 TPS | 11.11 | 可存活，但未维持满 TPS |
| 最大调度 MSPT | 90.0 ms | 存在明显卡顿窗口 |
| 最大 tick 间隔 | 1.144 s | 存在秒级尖峰 |
| 世界实体峰值 | 21,425 | 超过 10k 目标 114% |
| 清理 | 约 5 s 后实体和票据归零 | 分批清理未触发 Watchdog |

集中窗口通常能在 20–30 秒内回到约 10,000 个实体。分散窗口会重新加载含实体的区块，将世界实体数推高到 17,000–21,425。当世界实体超过目标后，LoadTest 不再补充实体；后续增长来自区块加载和卸载生命周期。

## 5. Folia region 分析

两个代表性健康报告显示了负载集中问题：

```text
Total regions: 1
Utilisation: 100.9% / 400.0%
Lowest/Median/Highest Region TPS: 3.51 / 3.51 / 3.51
Hot region: 6,226 chunks, 11,370 entities, 281.65 MSPT
```

```text
Total regions: 5
Utilisation: 105.2% / 400.0%
Lowest/Median/Highest Region TPS: 5.77 / 19.07 / 19.07
Hot region: 5,000 chunks, 16,696 entities, 171.97 MSPT
```

4 个 tick 线程提供 400% 理论利用率，但绝大多数工作可能集中在一个 region。当该 region 的单线程已经满载时，其他 tick 线程无法并行处理同一热点。相邻的加载区还会使 region 合并，因此单纯增加 `threaded-regions.threads` 不会修复这类瓶颈。

参考测试曾观察到约 30% 的进程 CPU 峰值，但没有连续 CPU 时间序列。该数值只能辅助说明 region 并行度，不能当作精确容量指标。排障时应同时查看每个 region 的 TPS、MSPT、chunk 和 entity 数与整个 JVM 的 CPU 平均值。

## 6. 64 人推荐配置

以下配置是保守起点。每次只调整一类参数，并在同一地图快照上对比 region 和区块指标。

### 6.1 世界预生成

64 人以鞨翅速度探索时，应先消除比赛过程中的新区块生成，再调整 Folia 线程。

可选的世界策略：

1. **覆盖 32 b/s × 10 分钟直线移动**：主世界预生成半径至少 19,500–20,000 blocks，磁盘和预处理成本较高；
2. **限制可活动范围**：世界边界半径设为 12,000，预生成至少 12,256，并在规则中明确边界限制。

同时建议：

- 下界按主世界边界的 1/8 比例预生成，另加至少 256 blocks 安全边缘；
- 末地只需覆盖任务池和比赛规则允许的活动范围；
- 确保运行用户能在结算后使用当前 Java 命令启动替代进程，并有足够 I/O 余量后台删除旧世界；
- `worlds.allow-reuse-without-reset` 在生产环境保持 `false`；
- 预生成后随机抽样加载区块，再允许 Core 创建比赛 manifest；
- 不得在正式比赛进行时运行世界预生成工具。

### 6.2 视距与区块加载

推荐起点：

```properties
max-players=80
view-distance=8
simulation-distance=8
```

将 10/10 调整为 8/8 后，单个互不重叠玩家的方形加载窗口从 441 chunks 降到 289 chunks，理论降幅约 34.5%。如果视觉需求必须使用 `view-distance=10`，可先保留 `simulation-distance=8`，再验证实体激活和 Bingo 任务体验。

推荐的 Paper 起点：

```yaml
chunk-loading-advanced:
  auto-config-send-distance: true
  player-max-concurrent-chunk-generates: 0
  player-max-concurrent-chunk-loads: 0
chunk-loading-basic:
  player-max-chunk-generate-rate: 8.0
  player-max-chunk-load-rate: 60.0
  player-max-chunk-send-rate: 45.0
chunk-system:
  io-threads: 2
  worker-threads: 4
```

注意：

- `0` 表示交由 Paper 自动决定每名玩家的并发数，不表示无限；
- 只有在世界已预生成时，才建议将 generate rate 作为越界保护限制为 8；
- send rate 过低会使 32 b/s 飞行者持续看到空洞；
- 若预生成范围内的加载 P95 仍超过 1 秒，应先检查磁盘延迟、区块覆盖和 region 合并，不要盲目提高并发。

### 6.3 Folia 线程

可以从以下预算开始：

```yaml
chunk-system:
  io-threads: 2
  worker-threads: 4
threaded-regions:
  grid-exponent: 4
  scheduler: EDF
  threads: 4
```

调整原则：

- 除非有独立回归测试，保持 `grid-exponent=4` 和 `scheduler=EDF`；
- 世界已预生成时，可 A/B 测试将 chunk workers 从 4 降到 2，把 CPU 预算留给 tick 和 GC；
- 只有在健康报告显示多个 region 同时高利用率时，才考虑增加 tick threads；
- 若始终只有一个 region 满载，增加 tick threads 无效；
- 线程预算必须使用 JVM 可用 CPU，并为 GC、网络、区块工作和插件异步线程留出余量。

### 6.4 16 队空间布局

64 人应按 **16 队 × 4 人** 设计散布与压测：

- 每队可使用 2 人停留/采集、2 人 32 b/s 飞行的压力模型；
- 队伍锚点之间建议相隔 768–1,024 blocks；
- 环形布局可从约 3,072 blocks 的锚点半径开始测试；
- 同队 4 名成员的独立目标可相隔 256–384 blocks；
- 队友传送会短暂合并热点，传送后的移动模型应让成员再次分开；
- 飞行路径应避免统一穿过世界中心或构成连续加载走廊；
- 区块卸载延迟可从 5 秒开始，避免移动轨迹长时间桥接多个锚点。

小半径随机散布会让 64 人开局时形成单一热点。推荐使用 16 个确定性队伍锚点，并在每个锚点内生成 4 个几何对称的成员位置。这比单纯增大 `scatter-radius` 更容易保证队伍间距离和资源公平性。

### 6.5 实体与保护线

参考结果显示：

- 集中 16k 实体可运行 5 分钟，最低调度 TPS 为 12.7；
- 集中约 20k 实体时 TPS 降至 2.45；
- 分散布局与新区块压力叠加时，约 16.9k 实体已降至 1.23 TPS；
- 10k 目标测试在瞬时 21.4k 下完成，但不能据此认为 20k 可持续运行。

可使用以下初始告警线：

| 级别 | 世界实体数 | 建议动作 |
| --- | ---: | --- |
| 正常 | ≤ 10,000 | 记录各 region 实体分布 |
| 预警 | 12,000 | 检查单 region 实体数、掉落物、投射物和异常区块票据 |
| 严重 | 14,000 | 停止额外压力生成，检查刷怪源和实体卸载 |
| 临界 | 16,000 | 准备中止测试或比赛；避免全世界同步实体遍历 |

世界总实体数不能替代 region 指标。一个 region 中的 10,000 个 AI 实体可能比 8 个 region 各 2,000 个实体更危险。正式世界应使用正常的 per-player mob cap，不应安装 LoadTest 或人为补足到固定实体数。

除非 profiler 明确显示路径寻找是热点，不建议盲目降低怪物与动物的激活范围，以免破坏追击、刷怪和击杀类 Bingo 任务。

### 6.6 Core 与 Worker

以下是通用示例，`worker-id`、代理服务名和 Redis 参数必须按部署环境替换。

Core：

```yaml
team:
  max-members: 4
redis:
  enabled: true
  instance-id: auto
  uri: redis://redis-host:6379/0
  namespace: championships
  consumer-group-prefix: championships-core
  stream-max-length: 100000
  block-timeout-ms: 2000
  reclaim-idle-ms: 15000
  max-deliveries: 8
  reconciliation-seconds: 30
bingo:
  execution-mode: REMOTE
  worker-id: bingo-1
  worker-server: bingo-worker
  proxy-channel: BungeeCord
  ready-timeout-seconds: 30
  arrival-timeout-seconds: 45
  heartbeat-timeout-seconds: 20
```

Worker：

```yaml
enabled: true
worker-id: bingo-1
proxy:
  return-server: core
worlds:
  overworld: bingo
  nether: bingo_nether
  the-end: bingo_the_end
  allow-reuse-without-reset: false
```

64 人时应验证：

- 准备时间内所有安全散布区块均已加载；
- Redis 心跳在 chunk I/O 满载时仍低于超时阈值；
- 64 人同时完成目标时，completion 序列连续且不产生重复积分；
- 玩家列表与 PlaceholderAPI 不会对全部玩家每 tick 执行重型 placeholder；
- BossBar 只首次显示，后续在原对象上更新；
- Sidebar 只在开局、完成事件或合并后的刷新请求中更新；
- 同一玩家在一个 tick 内的拾取、合成和背包点击只触发一次合并观察。

## 7. 实现与排障要点

与 64 人容量相关的关键实现如下：

| 组件 | 实现要点 | 目的 |
| --- | --- | --- |
| Worker 玩家观察 | 事件与周期扫描共用 `requestObserve`，按玩家和 tick 合并 | 避免背包事件风暴 |
| Worker 任务索引 | 进度 key 建立格子索引，轮询跳过事件型与已完成目标 | 缩小每次观察的目标集合 |
| Worker UI | Sidebar 刷新合并，BossBar 仅首次 show | 减少跨 region UI 调度 |
| 安全散布 | 安全点查找使用有界并发，玩家列表使用快照 | 缩短多人准备阶段且符合 Folia ownership |
| 共享展示 | Core 与 Worker 共用颜色解析和积分格式化 | 防止两端展示语义漂移 |
| LoadTest | 限制加载并发、每 tick 提交、生成速率与分批清理 | 让失败可控并留下可用指标 |

排障时应保存以下可重建信息：

1. Core、Worker 和协议模块的同一版本号或 Git commit；
2. Folia、Java 和代理版本；
3. 经脱敏的 Core、Worker、Paper、Spigot 和 JVM 配置快照；
4. 世界模板版本、预生成范围和世界边界；
5. LoadTest 的 `results.jsonl`、服务器日志、Folia health report 和 profiler 报告；
6. CPU quota、heap、direct memory、磁盘和网络资源数据。

Core 或 Worker JAR 替换后必须重启对应服务器，热重载不能激活已替换的类。Core 与 Worker 共用协议或平台层变更时，应成对发布。

## 8. 生产验收标准

生产验收使用以下标准：

| 类别 | 建议通过标准 |
| --- | --- |
| 时长 | 完整 10 分钟比赛 + 2 分钟结算/回收 |
| 玩家 | 64 个真实协议客户端，16 队 × 4 人 |
| Region TPS | 活跃玩家所在 region 持续 ≥18 TPS，不得连续两个采样 <15 TPS |
| 全局/调度 TPS | 最低不低于 15，绝大多数采样 ≥18 |
| 区块延迟 | 预生成范围内 P95 <1 s、P99 <3 s |
| 区块队列 | 有效队列不持续增长，峰值建议 <2,000 |
| 实体 | 稳态窗口 ±10%，切换超调不超过 15 秒，单 region 无异常集中 |
| 错误 | 区块失败、实体失败、Folia ownership 异常和 Watchdog 均为 0 |
| Redis | heartbeat <5 s，无不可恢复 pending、DLQ 或 completion 序列空洞 |
| UI/玩法 | 卡片、菜单、队友传送、任务完成、BossBar、Sidebar 和重生全部正常 |
| 清理 | 结算后 10 秒内临时实体/票据归零，世界 slot 被标记为不可复用 |
| 资源余量 | 峰值时 CPU、heap、direct memory 和磁盘 I/O 均保留至少 20% 余量 |

参考 12 分钟测试覆盖时长、基础生存性、零加载/生成失败和清理标准。区块延迟、严格实体窗口和真实 16 队客户端覆盖需通过生产验收确认。

## 9. 推荐验收顺序

1. 恢复干净世界，按选定边界完成三维度预生成。
2. 使用 16 队 × 4 人布局，先执行 12 分钟、10k 目标的逻辑加载测试。
3. 同时采集 profiler、Folia health report、LoadTest 结果和系统资源时间序列。
4. 逻辑压测通过后，使用 64 个真实协议客户端运行完整 Worker 流程。
5. 验证规则介绍、散布、任务、队友传送、重生、结算和返回 Core。
6. 分别演练 Redis 中断、Worker 重启、Core 重启、玩家掉线重连和代理目标不可达。
7. 恢复世界快照后再运行一局，验证世界 slot 轮换。
8. 验收通过后移除 LoadTest JAR，冻结 Core/Worker 版本与正式配置。

性能分析至少应关联以下指标：

- Folia：region 数、最低/中位 TPS、单 region chunks/entities/utilisation；
- Chunk：load/gen rate、P95/P99、有效队列和过期请求；
- Entity：世界总量、类型分布、各 region 数量和激活实体；
- Worker：观察请求合并率、Sidebar 刷新、Redis heartbeat/pending/DLQ；
- System：CPU quota 利用率、GC pause、heap/direct memory、磁盘延迟和网络吞吐。

## 10. 参考资料

- [PaperMC Folia FAQ](https://docs.papermc.io/folia/faq/)
- [PaperMC Folia region overview](https://docs.papermc.io/folia/reference/overview/)
- [Paper global configuration reference](https://docs.papermc.io/paper/reference/global-configuration/)
- [Paper world configuration reference](https://docs.papermc.io/paper/reference/world-configuration/)
- [Paper Spigot configuration reference](https://docs.papermc.io/paper/reference/spigot-configuration/)
- [Paper profiling and spark](https://docs.papermc.io/paper/profiling/)
