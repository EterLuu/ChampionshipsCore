# Championships Bingo Folia Worker

该插件是 ChampionshipsCore 远程 Bingo 的执行面。Core 冻结比赛 manifest、掌握赛程与正式积分；Worker 只负责 Folia 世界、玩家状态、任务观察、界面和事件回传。Worker 不直接连接赛事数据库，也不应单独接受玩家直连。

完整协议、状态机和故障边界见 [跨服拆分架构](../docs/bingo-remote-architecture.md)，64 人容量与配置依据见 [性能指南](../docs/bingo-64-player-performance-report.md)。

## 要求与构建

- Java 25 和与项目 API 匹配的 Folia 26.2；
- 可由 Core 与 Worker 同时访问的 Redis；
- BungeeCord，或启用了 BungeeCord 兼容 channel 的 Velocity；
- 与 Worker 使用同一版本/协议的 ChampionshipsCore；
- PlaceholderAPI 可选，FastBoard 和内部模块已打进 Worker JAR。

从仓库根目录构建：

```bash
mvn -pl championships-bingo-worker -am clean package
```

产物为 `championships-bingo-worker/target/championships-bingo-worker-1.3-SNAPSHOT.jar`。替换运行 JAR 后必须重启 Bingo 服务；热重载不能替换已经加载的类。涉及共享协议或平台层时，Core 与 Worker 必须成对构建和重启。

## 配置

首次启动会生成 `plugins/ChampionshipsBingoWorker/config.yml`：

| 配置 | 含义 |
| --- | --- |
| `enabled` | Worker 总开关；首次核对 Redis、代理和世界后再启用 |
| `worker-id` | 物理 Worker 标识，必须与 Core 的 `bingo.worker-id` 一致 |
| `redis.uri` / `namespace` | Redis 地址和隔离命名空间；只允许 Core/Worker 网络访问 |
| `redis.consumer-group` | Worker 命令流 consumer group |
| `stream-max-length` | Streams 近似保留上限，不能小到覆盖未确认事件 |
| `block-timeout-ms` | 阻塞读取等待时间 |
| `reclaim-idle-ms` / `max-deliveries` | pending 接管阈值和进入 DLQ 前最大投递次数 |
| `proxy.channel` | BungeeCord 使用 `BungeeCord`；Velocity 可按代理设置改为兼容 channel |
| `proxy.return-server` | 比赛结束、拒绝直连或失去 ownership 时返回的 Core 服务名 |
| `worlds.*` | 一个比赛 slot 的主世界、下界和末地名称 |
| `allow-reuse-without-reset` | 生产必须为 `false`；结算后全员回到 Core，Worker 会请求外层监督脚本移走旧存档并重启 Folia，旧存档由新进程后台清理；只允许本地开发跳过该流程 |

倒计时、散布、任务、计分、PvP、常驻效果、语言和 Sidebar 不在 Worker 重复配置。它们由 Core 在开局时冻结进 manifest，因此改动 Core 的 Bingo 配置只影响之后新建的比赛。

## 部署与运行约束

1. 把 `scripts/bingo-reset-loop.sh` 部署到服务端根目录，并让容器/面板用它包装原有 Java 启动命令，例如 `./bingo-reset-loop.sh -- java <原有 JVM 参数> -jar folia.jar --nogui`。脚本必须作为 Java 的父进程持续运行。
2. 启动 Redis、Core、代理和 Worker，确认 Worker 已启用且没有命名空间/consumer group 错误。
3. 代理中禁止玩家手动选择 Bingo 服务，并配置连接失败回退 Core。
4. 先以一支测试队伍走通 `PREPARING → READY → ROUTING → COUNTDOWN → RUNNING → FINISHED`。
5. 一局结束后确认玩家已返回 Core；Worker 会写入重置交接标记并关闭 Folia，监督脚本在 Java 完全退出后移走旧世界、重新启动 Folia，新进程随后在后台删除旧世界。

不要在运行中的比赛热切换 Core 的 `LOCAL/REMOTE`，不要把多个并发比赛指向同一组三维度，也不要把 `allow-reuse-without-reset` 当作生产轮换方案。

## Folia 与性能原则

- 所有实体和区域操作必须继续经 entity/region scheduler；全局生命周期才使用 global scheduler。
- 64 人按四人队应视为 16 支队伍，并尽早把队内玩家也散开。Folia 只有在热点分成多个 region 后才能利用多 tick 线程。
- 生产世界优先预生成；首次生成新区块的尾延迟不会被增加 Folia 线程自动消除。
- 先采用性能指南中的 view/simulation distance、4 个 tick thread 和实体保护线，再根据 spark/Folia region 数据逐项 A/B 调整。
- 观察任务采用事件触发并按玩家每 tick 合并；Sidebar 与 BossBar 只在数据变化时更新。新增事件入口必须复用这些合并路径。

## 排障检查表

| 现象 | 优先检查 |
| --- | --- |
| Core 一直等 `READY` | Worker `enabled`、`worker-id`、Redis URI/namespace、命令流 pending/DLQ、三世界是否成功加载 |
| 玩家没有转服 | 代理服务名、Plugin Message channel、玩家是否属于 manifest、Worker 是否已 READY |
| 玩家到达后比赛不开始 | `requiredAtStart` 到达状态、`PLAYER_ARRIVED` 序列、Core arrival timeout |
| 任务不计数或重复 | Worker objective 日志、事件序列/completion sequence、outbox、Core inbox；不要直接修正式积分表 |
| 比赛结束无法返回 | `proxy.return-server`、代理 channel、Core 服务是否可用 |
| TPS 低但总 CPU 不高 | 查看每个 Folia region 的 TPS/MSPT、chunk/entity 数，通常是单热点而非整机 CPU 不足 |
| 下一局被拒绝 | 这是脏世界保护；恢复快照或重建 Worker，不要在生产打开复用开关 |

Worker 事件先进入本地 outbox，再发往 Redis。Redis 故障时不要删除 outbox 或强行重置 consumer group；保留日志和数据以便按事件序列恢复、对账。
