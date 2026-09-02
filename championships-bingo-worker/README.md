# Championships Bingo Folia Worker

Bingo Folia Worker 是 ChampionshipsCore 远程 Bingo 的执行面。Core 冻结比赛 manifest、掌握赛程与正式积分；Worker 在独立 Folia 服务端执行世界、玩家状态、任务观察、界面和事件回传。Core 保持权威数据与赛程控制，Worker 专注低延迟玩法执行，两者共同完成一场可迁移、可扩展的 Bingo。

完整协议、状态机和故障边界见 [跨服拆分架构](../docs/bingo-remote-architecture.md)，64 人容量与配置依据见 [性能指南](../docs/bingo-64-player-performance-report.md)。

## 要求与构建

- Java 25 和与项目 API 匹配的 Folia 26.2
- 可由 Core 与 Worker 同时访问的 Redis
- BungeeCord，或启用了 BungeeCord 兼容 channel 的 Velocity
- 与 Worker 使用同一版本/协议的 ChampionshipsCore
- PlaceholderAPI 可选；FastBoard 和内部模块已打进 Worker JAR

从仓库根目录构建：

```bash
mvn -pl championships-bingo-worker -am clean package
```

产物为 `championships-bingo-worker/target/championships-bingo-worker-1.3-SNAPSHOT.jar`。替换运行 JAR 后重启 Bingo 服务。共享协议、Bingo engine、Bukkit 平台层、Redis transport 或跨服展示变化时，Core 与 Worker 成对构建、部署和重启。

## 与 Core 的共享运行时

Worker 与本地 Bingo 共用 ChampionshipsCore 的共享模块。以下行为只需在一个模块中修改，随后验证 `LOCAL` 与 `REMOTE` 两条路径：

- 物品、进度、统计和事件型任务的判定及比赛内进度；
- 世界 gamerule、昼夜/天气、难度、PvP、死亡保留和流浪商人策略；
- 玩家生命、饱食、经验、效果、飞行与危险状态清理；
- 队伍颜色、玩家身份、聊天行、加入/退出消息和原生计分板队伍投影；
- 规则介绍时间线、`mm:ss` 计时文本、Sidebar 排名窗口及纯 Java 计分结果。

Core 是 manifest、赛程、正式积分与数据库的唯一 owner。Worker 使用 manifest 中冻结的名册、任务、规则和展示文案，比赛规则在开局瞬间固定下来，整局保持一致。

## 配置

首次启动会生成 `plugins/ChampionshipsBingoWorker/config.yml`：

| 配置 | 含义 |
| --- | --- |
| `enabled` | Worker 总开关；首次核对 Redis、代理和世界后启用 |
| `worker-id` | 物理 Worker 标识，与 Core 的 `bingo.worker-id` 一致 |
| `redis.uri` / `namespace` | Redis 地址和隔离命名空间；只允许 Core/Worker 网络访问 |
| `redis.consumer-group` | Worker 命令流 consumer group |
| `stream-max-length` | Streams 近似保留上限，覆盖所有未确认事件 |
| `block-timeout-ms` | 阻塞读取等待时间 |
| `reclaim-idle-ms` / `max-deliveries` | pending 接管阈值和进入 DLQ 前最大投递次数 |
| `proxy.channel` | BungeeCord 使用 `BungeeCord`；Velocity 可按代理设置改为兼容 channel |
| `proxy.return-server` | 比赛结束、拒绝直连或失去 ownership 时返回的 Core 服务名 |
| `worlds.*` | 一个比赛 slot 的主世界、下界和末地名称 |
| `allow-reuse-without-reset` | 生产保持 `false`；本地开发可跳过世界重置流程 |

倒计时、散布、任务、计分、PvP、常驻效果、语言、Sidebar 和队伍展示由 Core 在开局时冻结进 manifest。改动 Core 的 Bingo 配置后，新比赛立即使用新规则，正在运行的比赛继续使用开局时的 manifest。`worker-id` 参与聊天 consumer group 命名，多个 Worker 使用不同 ID，让每个实例都收到完整公共聊天。

## 部署与运行流程

1. 把 `scripts/bingo-reset-loop.sh` 部署到服务端根目录，并让容器/面板用它包装原有 Java 启动命令，例如 `./bingo-reset-loop.sh -- java <原有 JVM 参数> -jar folia.jar --nogui`。脚本作为 Java 的父进程持续运行。
2. 启动 Redis、Core、代理和 Worker，确认 Worker 已启用且命名空间/consumer group 正常。
3. 代理禁止玩家手动选择 Bingo 服务，并配置连接失败回退 Core。
4. 先以一支测试队伍走通 `PREPARING → READY → ROUTING → COUNTDOWN → RUNNING → FINISHED`。
5. 同时核对 Core 与 Worker 的任务完成、队伍颜色、TAB/Sidebar、普通跨服聊天和 `/teammsg`；这些属于共享玩家可见契约。
6. 一局结束后确认玩家已返回 Core；Worker 写入重置交接标记并关闭 Folia，监督脚本在 Java 完全退出后移走旧世界、重新启动 Folia，新进程随后在后台删除旧世界。

`LOCAL/REMOTE` 切换安排在空闲窗口；每组 Bingo 三维度同一时间服务一个比赛 slot；生产环境保持 `allow-reuse-without-reset: false`，由重置循环清理旧世界。

## Folia 与性能原则

- 所有实体和区域操作继续经 entity/region scheduler；全局生命周期使用 global scheduler。
- 64 人按四人队构成 16 支队伍，尽早把队内玩家也散开，让热点分布在多个 region 中。
- 生产世界优先预生成，把首次生成新区块的尾延迟移出正式比赛。
- 先采用性能指南中的 view/simulation distance、4 个 tick thread 和实体保护线，再根据 spark/Folia region 数据逐项 A/B 调整。
- 观察任务采用事件触发并按玩家每 tick 合并；Sidebar 与 BossBar 只在数据变化时更新。新增事件入口复用这些合并路径。

## 排障检查表

| 现象 | 优先检查 |
| --- | --- |
| Core 一直等 `READY` | Worker `enabled`、`worker-id`、Redis URI/namespace、命令流 pending/DLQ、三世界是否成功加载 |
| 玩家没有转服 | 代理服务名、Plugin Message channel、玩家是否属于 manifest、Worker 是否已 READY |
| 玩家到达后比赛不开始 | `requiredAtStart` 到达状态、`PLAYER_ARRIVED` 序列、Core arrival timeout |
| 任务不计数或重复 | Worker objective 日志、事件序列/completion sequence、outbox、Core inbox |
| 两端任务判定或展示不同 | Core/Worker 构建来源、重启状态、manifest 协议版本；Worker 只执行 manifest 规则 |
| Worker 看不到 Core 聊天 | Redis URI/namespace、Core `instance-id`、Worker `worker-id`、聊天 consumer group 和 Redis 连接告警 |
| 队伍颜色或 `/teammsg` 异常 | manifest 队伍快照、原生 scoreboard team 是否可变；平台拒绝变更时确认已进入插件侧命令降级 |
| 比赛结束无法返回 | `proxy.return-server`、代理 channel、Core 服务是否可用 |
| TPS 低但总 CPU 不高 | 每个 Folia region 的 TPS/MSPT、chunk 和 entity 数；热点通常位于单个 region |
| 下一局被拒绝 | 脏世界保护生效；恢复快照或重建 Worker |

Worker 事件先进入本地 outbox，再发往 Redis。Redis 故障期间保留 outbox、日志和 consumer group 数据，恢复后按事件序列继续投递比赛事件。公共聊天按 30 秒即时投递语义处理；故障窗口结束后，公共聊天从恢复时刻进入新的实时窗口。
