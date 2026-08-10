# Bingo 64 人 Folia 性能分析与配置建议

> 状态快照：2026-08-10 UTC
>
> 适用范围：ChampionshipsCore 远程 Bingo、`cc-bingo` Folia Worker、64 名参赛玩家
>
> 结论依据：`cc-bingo` 运行配置、Folia 健康报告、8 轮压测结果及当前源码工作区差异

## 1. 执行结论

当前服务器能够完成 64 个模拟加载者、32 b/s 飞行、约 1 万实体基线的 12 分钟测试，并且在实体数周期性冲高到 21,425 时仍未发生 Watchdog、区块加载异常或实体生成异常。最后一轮的全局调度最低值为 11.11 TPS，多数采样在 16–20 TPS，结束清理约 5 秒完成。

这不能直接判定为“64 人完全通过”，主要有四个原因：

1. 区块异步加载 P95/P99 均达到约 30 秒，玩家高速飞行时会明显看到区块跟不上；
2. 配置目标虽然是 10,000 个世界实体，但集中/分散切换使实际数量在约 2,000–21,425 之间振荡；
3. Folia 健康报告显示主要负载曾合并为一个热点 region，增加线程无法并行化该热点；
4. 正式队伍上限为 4 人，64 人对应 16 队，而当前压测布局实际按 8 个静止玩家锚点组织，不能完整复现 16 队的空间分布、真实网络包、任务观察、Redis 和 UI 成本。

因此，当前结论应表述为：

- **生存性通过**：12 分钟压力下服务器没有崩溃，清理也没有再次触发 Watchdog；
- **实体 tick 有余量**：集中布局的 16,000 实体测试可以完成；
- **新区块加载不通过生产验收**：30 秒级加载延迟不可接受；
- **严格 10,000 实体稳态未验证**：最后一轮实际包含约 21,000 的周期峰值；
- **正式 64 人仍需一次干净、预生成世界上的 16 队端到端验收**。

## 2. 当前环境快照

### 2.1 软件与运行拓扑

| 项目 | 当前值 |
| --- | --- |
| Java | Azul Zulu OpenJDK 25.0.3 LTS |
| Minecraft | 26.1.2 |
| Folia | `26.1.2-8`, commit `62dc0f2` |
| Bingo Worker | `ChampionshipsBingoWorker 1.3-SNAPSHOT` |
| 压测插件 | `ChampionshipsBingoLoadTest 1.3-SNAPSHOT` |
| Worker 世界 | `bingo`、`bingo_nether`、`bingo_the_end` |
| Core 执行模式 | `REMOTE` |
| Redis | Core 与 Worker 使用 `minecraft-redis:6379/0` |
| 正式人数上限 | `max-players=80`，为 64 名参赛者保留管理/观战余量 |
| 队伍结构 | `team.max-members=4`，64 人即 16 队 × 4 人 |

服务器启动日志确认了以下线程分配：

```text
Netty IO threads       = 2
Chunk worker threads  = 4
Chunk I/O threads     = 2
Folia tick threads    = 4
Folia scheduler       = EDF
```

容器 CPU 配额、内存上限、JVM `-Xms/-Xmx` 与 GC 参数不在共享文件系统中，当前无法从本报告所在环境确认。上线前必须从编排配置补录这些值，否则同一套 Paper/Folia 配置在不同 CPU quota 下没有可比性。

### 2.2 当前关键配置

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

`server.properties` 与 `spigot.yml`：

```properties
max-players=80
view-distance=10
simulation-distance=10
network-compression-threshold=-1
use-native-transport=true
sync-chunk-writes=true
```

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

Paper 世界配置中的 `spawn-limits: -1` 和 `ticks-per-spawn: -1` 表示继承 `bukkit.yml`，不是关闭自然刷怪。当前 Bukkit 基础上限为怪物 70、动物 10、水生动物 5、水中环境生物 20、地下水生物 5、蝾螈 5、环境生物 15。

## 3. 压测模型

最终 12 分钟测试使用：

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

行为模型为：

- 32 名加载者按 32 blocks/s 持续飞行；
- 32 名加载者在集中和分散布局间每 60 秒切换；
- 分散时以 7 blocks/s 跑向各自目标；
- 生物生成点按面积均匀分布在静止者 24–128 格环带内；
- 生成理由为 `NATURAL`，AI 开启，实体混合为每 80 个槽位 70 个敌对生物和 10 个被动物；
- 敌对生物为僵尸、骷髅、蜘蛛、苦力怕；被动物为牛、羊、猪、鸡、兔和山羊；
- 不使用村民，避免把测试退化成单一村民 AI/POI 压力；
- 停止后以异步小批次移除实体和插件区块票据。

### 3.1 模型与真实 64 人的差异

| 模型差异 | 对结果的影响 |
| --- | --- |
| 没有真实玩家实体和客户端 | 未覆盖网络发包、实体追踪、皮肤、TAB、玩家碰撞和协议编码成本 |
| 不执行 Bingo 完整任务逻辑 | 未覆盖背包/统计观察、任务菜单、地图卡、Redis 事件和积分重放成本 |
| 64 人按 8 个静止锚点组织 | 与正式 16 队 × 4 人的 region 分布不一致 |
| 使用插件异步区块 API | 不完全经过 Paper 的 per-player 加载/发送限流路径 |
| 在反复使用的压力世界上测试 | 已生成区块和残留实体会随布局重新加载，造成实体数振荡 |
| 飞行者持续直线飞行 | 比多数真实玩家更极端，但没有模拟转弯、传送、进维度和死亡重生 |

压测结果适合用于发现服务端区块/实体上限，不应单独作为正式赛事验收。

## 4. 历次测试结果

结果原始记录位于：

```text
/home/minecraft/minecraft/cc-bingo/plugins/ChampionshipsBingoLoadTest/results.jsonl
/home/minecraft/minecraft/cc-bingo/logs/latest.log
```

| UTC 结束时间 | 主要目标/布局 | 时长 | 结果 | 实体峰值 | 最低调度 TPS | 关键观察 |
| --- | --- | ---: | --- | ---: | ---: | --- |
| 03:11 | 初始多阶段区块加载 | 383 s | 队列保护停止 | 未记录 | 未记录 | 有效队列达到早期保护线，证明初版请求回收不足 |
| 03:32 | 早期停留/实体生成 | 480 s | 完成 | 2,302 | 未记录 | 实体没有达到预期；加载 P95 30 s |
| 03:47 | 改进实体生成 | 481 s | 完成 | 10,565 | 未记录 | 达到约 1 万实体，但最大加载延迟 125 s |
| 04:13 | 8/32/64 混合阶段，32 b/s | 180 s | TPS 保护停止 | 9,044 | 2.96 | 在第二阶段停止；说明早期布局产生热点 |
| 04:23 | 64 加载者、集中布局、目标 16k | 300 s | 完成 | 16,312 | 12.70 | 结束 TPS 19.57；集中 16k 可以存活 5 分钟 |
| 04:33 | 64 加载者、集中布局、目标 32k | 94 s | TPS 保护停止 | 19,917 | 2.45 | 约 20k 集中实体不可持续 |
| 04:46 | 64 加载者、分散布局、目标 20k | 75 s | TPS 保护停止 | 16,935 | 1.23 | 分散新区块与实体压力叠加，比集中布局更早失败 |
| 05:09 | 64 加载者、每分钟切换、目标 10k | 720 s | 完成 | 21,425 | 11.11 | 0 加载失败、0 实体失败；实体未能保持在 10k |

### 4.1 最终 12 分钟测试详细指标

| 指标 | 结果 | 评价 |
| --- | ---: | --- |
| 运行时间 | 720.305 s | 完整完成 |
| 布局切换 | 11 次 | 覆盖 6 个集中窗口和 6 个分散窗口 |
| 完成区块加载 | 90,564 | 高强度新区块流量 |
| 区块加载失败 | 0 | 通过 |
| 实体生成失败 | 0 | 通过 |
| 过期请求 | 881,347 | 高速移动下大量窗口请求在执行前已失效 |
| 有效队列峰值 | 19,246 | 未触发 60,000 保护线，但仍偏高 |
| 原始队列峰值 | 44,132 | 表明请求生产速度显著高于消费速度 |
| 最大并发加载 | 384 | 长时间满载 |
| 加载平均延迟 | 2.922 s | 偏高 |
| 加载 P95/P99 | 30 s / 30 s | 不符合正常玩家体验 |
| 最大加载延迟 | 41.6 s | 不符合正常玩家体验 |
| 最低调度 TPS | 11.11 | 可存活，但不是满 TPS |
| 最大调度 MSPT | 90.0 ms | 有明显卡顿窗口 |
| 最大 tick 间隔 | 1.144 s | 存在秒级尖峰 |
| 世界实体峰值 | 21,425 | 超过 10k 目标 114% |
| 清理 | 约 5 s 后实体 0、票据 0 | 新清理方案通过，无 Watchdog |

集中窗口通常能在 20–30 秒内恢复并稳定到约 10,000 实体。分散窗口会逐步重新加载含实体区块，常见峰值为 17,000–20,000，最终达到 21,425。这个现象不是生成器继续超调：在世界实体超过目标后，插件生成数保持不变，增长来自区块加载和卸载生命周期。

### 4.2 Folia region 证据

第一份健康报告：

```text
Total regions: 1
Utilisation: 100.9% / 400.0%
Lowest/Median/Highest Region TPS: 3.51 / 3.51 / 3.51
Hot region: 6,226 chunks, 11,370 entities, 281.65 MSPT
```

稍后报告：

```text
Total regions: 5
Utilisation: 105.2% / 400.0%
Lowest/Median/Highest Region TPS: 5.77 / 19.07 / 19.07
Hot region: 5,000 chunks, 16,696 entities, 171.97 MSPT
```

这解释了“CPU 峰值约 30%，但局部 TPS 很低”：4 个 tick 线程提供 400% 理论利用率，但绝大多数工作集中在一个 region，该 region 单线程已经 100%，其余线程没有可并行的独立 region。Folia 的相邻加载区必须合并，只有不相邻且能成功拆分的 region 才能并行。单纯把 `threaded-regions.threads` 从 4 调高不会修复这个瓶颈。

约 30% CPU 峰值来自测试期间的人工观测，本轮没有从容器采集到连续 CPU 时间序列，因此它只能作为解释 region 并行度的辅助证据，不能作为精确容量指标。

## 5. 64 人推荐生产配置

以下配置分为“已测试基线”和“建议变更”。建议变更应先在快照环境 A/B 验证，不应直接在正式比赛前临时修改。

### 5.1 世界准备：最高优先级

64 人以鞘翅速度探索时，优先解决新区块生成，而不是先增加 Folia 线程。

建议采用以下二选一方案：

1. **严格覆盖 32 b/s × 10 分钟**：主世界预生成半径至少 19,500–20,000 blocks；磁盘占用和准备时间很高；
2. **生产可控方案**：设置半径 12,000 的世界边界并预生成到 12,256，接受持续直线飞行者约 6 分钟后到达边界。

同时：

- 下界至少按主世界边界的 1/8 比例预生成，再增加 256 blocks 安全边缘；
- 当前卡池排除了末地目标，末地可只预生成中央岛及允许活动范围；
- 每场比赛前从只读模板恢复三个干净维度；
- `worlds.allow-reuse-without-reset` 保持 `false`；
- 预生成后做一次随机抽样加载校验，再创建比赛 manifest；
- 不要把 Chunky 预生成与 64 人正式比赛同时运行。

PaperMC 的 Folia FAQ 同样把世界预生成列为线程分配前的第一项建议。

### 5.2 视距与区块加载

推荐生产起点：

```properties
# server.properties
max-players=80
view-distance=8
simulation-distance=8
```

相较本次测试的 10/10，8/8 将单个互不重叠玩家的方形加载窗口从 441 chunks 降到 289 chunks，理论降幅约 34.5%，仍覆盖原版 128 blocks 量级的刷怪环带。若客户端视觉要求必须使用视距 10，应保留 `simulation-distance=8`，并在真实玩家测试中验证实体激活与任务玩法。

推荐的 Paper 起点：

```yaml
chunk-loading-advanced:
  auto-config-send-distance: true
  player-max-concurrent-chunk-generates: 0  # Paper 自动配置
  player-max-concurrent-chunk-loads: 0      # Paper 自动配置
chunk-loading-basic:
  # 预生成世界中生成应接近 0；较低上限用于保护意外越界。
  player-max-chunk-generate-rate: 8.0
  # 32 b/s 约为每秒跨 2 个 chunk，视距 8 的前沿可能需要约 34 chunk/s。
  player-max-chunk-load-rate: 60.0
  player-max-chunk-send-rate: 45.0
chunk-system:
  io-threads: 2
  worker-threads: 4
```

说明：

- 当前测试使用 generate/load/send = 20/60/45；只建议在世界预生成完成后把 generate 降到 8；
- 不能把 send rate 降得过低，否则 32 b/s 飞行会让客户端持续看到空洞；
- `0` 表示让 Paper 自动决定每名玩家的并发数，不表示无限；
- 压测插件使用异步区块 API，不完全受 per-player 限流，因此上述值仍需真实玩家验证；
- 如果 P95 加载仍超过 1 秒，先检查预生成覆盖、磁盘延迟和 region 合并，不要直接提高并发。

### 5.3 Folia 线程

当前 2 I/O + 4 chunk workers + 4 tick threads 可以作为已测试基线：

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

- 保持 `grid-exponent=4` 和 `scheduler=EDF`，不要在没有独立回归测试时修改 regionizer 粒度；
- 若世界已经预生成，可以 A/B 测试把 chunk workers 从 4 降到 2，把核心留给 tick/GC；
- 只有当健康报告显示多个 region 同时高利用率时，才考虑把 tick threads 从 4 调到 6；
- 如果仍是一个 region 100%，增加 tick threads 无效；
- Folia 官方建议总线程预算不要长期超过可用 CPU 核心的约 80%，并提醒 GC 和插件线程也要计入；
- 正式记录必须使用容器可用 CPU，而不是宿主机总 CPU。若容器只有 4 CPU quota，就不应再增加 tick threads。

### 5.4 16 队空间布局

正式模型应按 **16 队 × 4 人** 设计：

- 每队 2 人模拟停留/采集，2 人模拟 32 b/s 飞行；
- 16 个队伍锚点之间至少相隔 768–1,024 blocks；
- 若采用环形布局，建议锚点半径约 3,072 blocks，使相邻队伍距离约 1,200 blocks；
- 同队 4 名成员的独立目标间距建议 256–384 blocks；
- 同队成员通过菜单传送会短暂合并到一个 region，随后应继续分开；
- 飞行方向应避免所有路径穿过共同中心或彼此形成连续加载走廊；
- 区块卸载延迟维持 5 秒，避免移动轨迹长期把多个锚点桥接为同一 region。

当前场地配置为：

```yaml
prepare-time: 10
scatter-radius: 6
scatter-max-tries: 32
```

`scatter-radius: 6` 会让 64 人几乎同时出现在一个位置，开局阶段必然形成单一热点 region。短期可先改为：

```yaml
prepare-time: 30
scatter-radius: 3072
scatter-max-tries: 64
```

但随机圆盘不能保证最小间距和队伍公平性。更可靠的实现是把 `SafeScatterService` 扩展为 16 个确定性队伍锚点，并在每个锚点内生成 4 个旋转后的成员位置；所有队伍使用相同几何模板，只旋转角度，避免资源距离造成系统性优势。

这是下一轮性能测试和正式 64 人上线前的必要修改项。

### 5.5 实体配置与保护线

当前实体压力测试表明：

- 集中 16k 可以运行 5 分钟，最低调度 TPS 12.7；
- 集中约 20k 会降到 2.45 TPS；
- 分散且同时生成新区块时，约 16.9k 已降到 1.23 TPS；
- 10k 基线的布局切换测试在瞬时 21.4k 下存活，但不能据此认为 20k 可持续。

建议正式告警线：

| 级别 | 世界实体数 | 动作 |
| --- | ---: | --- |
| 正常 | ≤ 10,000 | 仅记录各 region 实体分布 |
| 预警 | 12,000 | 检查单 region 实体数、掉落物、投射物、村民和未卸载区块 |
| 严重 | 14,000 | 禁止测试插件补充实体，检查异常区块票据和刷怪源 |
| 临界 | 16,000 | 准备中止比赛/迁移观众；不做全世界同步实体遍历 |

不要用单一“总实体数”代替 region 指标。一个 region 的 10,000 个 AI 实体可能比 8 个 region 各 2,000 个实体更危险。

正式世界保持原版/Paper per-player mob cap，不要用压测插件补足到固定数量。生产服中应移除 `ChampionshipsBingoLoadTest` JAR，至少必须保持 `auto-start: false`。排查实体异常时优先按类型和 region 统计，不要假定所有实体是村民。

除非 spark 明确显示路径寻找是热点，否则暂不降低怪物/动物 32 blocks 的激活范围，以免破坏追击、刷怪和击杀类 Bingo 任务。若确认路径寻找占比过高，可先 A/B 测试：

```yaml
misc:
  update-pathfinding-on-block-update: false
```

该项能减少大量实体和方块更新场景中的寻路重算，但仍需验证村民、怪物追踪和任务体验。

### 5.6 Bingo 与 Worker 配置

Core 控制面推荐保持：

```yaml
team:
  max-members: 4
bingo:
  execution-mode: REMOTE
  worker-id: bingo-1
  worker-server: bingo
  proxy-channel: BungeeCord
  ready-timeout-seconds: 30
  arrival-timeout-seconds: 45
  heartbeat-timeout-seconds: 20
```

Worker 推荐保持：

```yaml
enabled: true
worker-id: bingo-1
worlds:
  overworld: bingo
  nether: bingo_nether
  the-end: bingo_the_end
  allow-reuse-without-reset: false
```

64 人时还应验证：

- `prepare-time=30` 内所有安全散布区块均已加载；
- Redis 心跳在 chunk I/O 满载时仍小于 20 秒超时；
- 64 人同时完成目标时，completion 序列连续且没有重复积分；
- TAB/PlaceholderAPI 刷新周期没有对所有 64 人每 tick 运行重型 placeholder；
- BossBar 只首次 show，后续原地更新；
- Sidebar 只在开局、完成事件或合并后的刷新请求中更新，不做每秒 64 次全量重建；
- 背包观察请求按玩家合并，一个 tick 内的拾取、合成和点击事件只触发一次观察。

## 6. 当前 Folia/Bingo 修改项与溯源

本节记录 2026-08-10 工作区和运行实例的已知修改。它不是 Git release manifest；当前工作区仍有未提交修改，应在正式部署前提交并打版本标签。

### 6.1 运行配置修改

| 文件 | 当前关键修改 | 排查意义 |
| --- | --- | --- |
| `cc-bingo/config/paper-global.yml` | chunk I/O=2、worker=4、Folia tick=4、EDF、grid=4；玩家 load/send=60/45 | 决定区块并发和 region 调度容量 |
| `cc-bingo/server.properties` | view/simulation=10、max players=80、native transport、compression=-1 | 决定加载面积、网络带宽和容量边界 |
| `cc-bingo/config/paper-world-defaults.yml` | chunk unload delay=5s、autosave chunks/tick=16、per-player mob spawns | 影响移动轨迹保留时间和实体分布 |
| `cc-bingo/spigot.yml` | Netty=2、激活范围 32/32/64、追踪范围 96、mob range=8 | 影响实体 tick 与客户端追踪 |
| Core `config.yml` | Bingo `REMOTE`、Worker `bingo-1`、20s heartbeat timeout | 影响跨服生命周期和故障判定 |
| Bingo area `bingo.yml` | timer=600、prepare=10、scatter radius=6、max tries=32 | 当前 64 人开局热点的直接来源 |
| Worker `config.yml` | 三世界单 slot，禁止脏世界复用 | 保证比赛间隔离 |
| LoadTest `config.yml` | 12 分钟配置已消费，`auto-start=false` | 当前不会再次自动破坏地图 |

注意：`network-compression-threshold=-1` 只适合 Worker 与代理之间处于可信低延迟网络且带宽充足的场景；它减少压缩 CPU，但增加网络流量。若代理跨主机或带宽受限，需要单独 A/B 测试。

### 6.2 源码修改

当前 `/data/ChampionshipsCore` 工作区包含以下未提交修改：

| 模块/文件 | 修改内容 | 性能或 Folia 原因 |
| --- | --- | --- |
| `BingoWorkerPlugin` | 周期玩家扫描改走 `requestObserve` | 与事件观察共用合并入口 |
| `WorkerListener` | 拾取、合成、背包点击不再各自直接调度 | 防止同一玩家一个 tick 重复观察 |
| `WorkerMatchRegistry` | 增加并发 `pendingObservations`，按 UUID 去重后延迟 1 tick | 降低 64 人背包事件风暴 |
| `WorkerMatchSession` | 完成格使用并发 `TeamCell` 集合；过滤已完成格；Sidebar 刷新合并；BossBar 只首次 show | 避免全量字符串扫描、重复 UI 调度和跨 region 访问 |
| `WorkerObjectives` | 进度目标建立 key→cell 索引；轮询跳过进度目标和已完成格；baseline 改并发映射 | 将每次观察的目标扫描量降到必要集合 |
| `SafeScatterService` | 安全点查找从全串行改为最多 4 路并发；快照玩家列表；统一异常回退 | 缩短 64 人散布准备时间并保持 Folia region 安全 |
| 根 `pom.xml` | 增加 `championships-bingo-loadtest` 模块 | 将压测器纳入 Reactor 构建 |
| `championships-bingo-loadtest` | 新增 Folia 压测插件、布局切换、自然实体混合、保护线、指标与异步分批清理 | 复现 64 人区块/实体压力 |
| `LegacyText`（platform-bukkit） | Core 与 Worker 共用十六进制/传统颜色解析和积分取整 | 避免两端展示语义漂移，减少重复实现 |
| `WorkerMenuService` | 复用 `WorkerTaskDisplay` 的数量、材质和进度解析；移除未使用的在线玩家查询 | 删除无效工作并维持旧 manifest 回退逻辑唯一来源 |

源码工作区同时新增了 Worker objective、共享文本和 LoadTest 测试。2026-08-10 05:39 UTC 执行根 Reactor `mvn clean package`，8 个模块共 47 项测试通过，0 失败、0 错误、0 跳过。

### 6.3 当前制品与已知限制

```text
Runtime Core JAR:
/home/minecraft/minecraft/cc-core/plugins/ChampionshipsCore-1.3-SNAPSHOT.jar
mtime: 2026-08-10 05:39:50 UTC

Runtime Worker JAR:
/home/minecraft/minecraft/cc-bingo/plugins/championships-bingo-worker-1.3-SNAPSHOT.jar
mtime: 2026-08-10 05:39:50 UTC

Runtime LoadTest JAR:
/home/minecraft/minecraft/cc-bingo/plugins/championships-bingo-loadtest-1.3-SNAPSHOT.jar
mtime: 2026-08-10 04:56:42 UTC
```

Core 与 Worker 的运行目录 JAR 已替换，但服务器重启前仍运行旧的已加载类；需要通过既有管理流程重启 `cc-core` 和 `cc-bingo`。LoadTest 本轮未替换，且正式赛事前仍须从插件目录移除。

因为源码改动尚未提交，当前无法仅凭 commit ID 完整重建运行状态。正式上线前应：

1. 提交或明确拆分上述源码变化；
2. 给 Core 与 Worker 使用同一 release 标识；
3. 在部署记录中保存 Git commit、构建时间、Folia 版本和配置快照；
4. 把容器 CPU/memory/JVM 参数补入记录；
5. 从生产插件目录移除 LoadTest JAR，而不是只依赖 `auto-start=false`。

### 6.4 已知非性能告警

最近启动日志中还有：

- 进程以 root 用户运行；
- 后端服务器为 offline mode，依赖 BungeeCord/网络隔离；
- AuthMe 缺少 GeoLite2 数据库和 MaxMind 凭据；
- AuthMe 因缺 PacketEvents 关闭 `protectInventory`；
- TAB 报告若干 placeholder 刷新周期与默认值重复。

这些告警没有被识别为本轮 TPS 瓶颈，但应在正式故障排查时与区块/实体问题区分。

## 7. 生产验收标准

建议使用以下标准判定 64 人通过，而不是只看进程是否存活：

| 类别 | 通过标准 |
| --- | --- |
| 时长 | 完整 10 分钟比赛 + 2 分钟结算/回收 |
| 玩家 | 64 个真实协议客户端，16 队 × 4 人 |
| Region TPS | 活跃玩家所在 region 持续 ≥18 TPS；不得连续两个采样 <15 TPS |
| 全局/调度 TPS | 最低不低于 15，绝大多数采样 ≥18 |
| 区块延迟 | 预生成范围内 P95 <1 s、P99 <3 s |
| 区块队列 | 有效队列不持续增长；峰值建议 <2,000 |
| 实体 | 目标窗口 ±10%；切换超调不超过 15 秒；单 region 无异常集中 |
| 错误 | 区块失败、实体失败、Folia ownership 异常、Watchdog 均为 0 |
| Redis | heartbeat <5 s；无不可恢复 pending、DLQ 或 completion 序列空洞 |
| UI/玩法 | 卡片、菜单、队友传送、任务完成、BossBar、Sidebar、重生全部正常 |
| 清理 | 结算后 10 秒内临时实体/票据归零；世界 slot 被标记为不可复用 |
| 资源余量 | 峰值时容器 CPU、heap、direct memory、磁盘 I/O 均保留至少 20% 余量 |

当前最后一轮只满足时长、生存性、零失败和清理标准；不满足区块延迟、严格实体窗口和真实 16 队客户端覆盖。

## 8. 下一轮测试顺序

1. 恢复一份干净世界，按选定边界完成三维度预生成；
2. 把压测器改为 16 队 × 4 人，每队 2 飞行 + 2 停留，锚点半径约 3,072；
3. 修正实体控制：布局切换时按 owner/region 统计，只清理由压测器创建且已经离开活动窗口的实体；
4. 使用 view/simulation=8/8，先做 12 分钟 10k 稳态测试；
5. 同时运行 10 分钟 spark profiler，并保存 Folia health report；
6. 若通过，再用 64 个真实协议客户端运行完整 Worker 流程；
7. 最后演练 Redis 中断、Worker 重启、玩家掉线重连和菜单跨 region 传送；
8. 全部通过后移除 LoadTest 插件，恢复正式世界快照并冻结 release。

推荐 spark 命令：

```text
/spark profiler start --timeout 600
```

排查时至少关联以下四组数据：

- Folia：region 数、最低/中位 TPS、单 region chunks/entities/utilisation；
- Chunk：load/gen rate、P95/P99、有效队列与过期请求；
- Entity：世界总量、各类型、各 region 数量、激活实体和村民/POI；
- Worker：观察请求合并率、Sidebar 刷新、Redis heartbeat/pending/DLQ。

## 9. 参考资料

- [PaperMC Folia FAQ](https://docs.papermc.io/folia/faq/)
- [PaperMC Folia region overview](https://docs.papermc.io/folia/reference/overview/)
- [Paper global configuration reference](https://docs.papermc.io/paper/reference/global-configuration/)
- [Paper world configuration reference](https://docs.papermc.io/paper/reference/world-configuration/)
- [Paper Spigot configuration reference](https://docs.papermc.io/paper/reference/spigot-configuration/)
- [Paper profiling and spark](https://docs.papermc.io/paper/profiling/)
- [Bingo 跨服拆分架构](./bingo-remote-architecture.md)
