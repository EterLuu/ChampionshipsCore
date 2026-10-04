# Championships Bingo Worker

Bingo Worker 在独立 Folia 服务端执行 ChampionshipsCore 的远程 Bingo：管理世界、玩家状态、任务观察和界面，并向 Core 回传事件。Core 冻结比赛 manifest，负责赛程、数据库和最终积分。

## 要求

- Java 25、Folia 26.2 与 PacketEvents 2.13.0。
- 配套版本的 Core、双方可访问的 Redis。
- BungeeCord，或启用 BungeeCord 兼容 channel 的 Velocity。
- 已加载的主世界、下界和末地，三个维度位于同一主世界目录下。
- 能处理世界重置标记的外部服务管理流程，见下方说明。

PlaceholderAPI 可选；FastBoard、Redis 客户端和内部共享模块已包含在 Worker JAR 中。服务端与插件版本以当前 POM 和插件描述为准。

## 构建与安装

从仓库根目录执行：

```bash
mvn -B -ntp -pl championships-bingo-worker -am clean package
```

将 `championships-bingo-worker/target/championships-bingo-worker-1.3-SNAPSHOT.jar` 与 PacketEvents 安装到独立 Folia 的 `plugins/`。首次启动生成配置时默认 `enabled: false`；停止服务器后填写配置、准备世界和代理路由，再启用。

## 配置

配置文件为 `plugins/ChampionshipsBingoWorker/config.yml`。以下为部署示例，主机名、代理服务名和世界名需与实际基础设施一致；合并到生成文件中：

```yaml
enabled: true
worker-id: bingo-1
redis:
  uri: redis://redis-host:6379/0
  namespace: championships
  consumer-group: bingo-workers
proxy:
  channel: BungeeCord
  return-server: core
worlds:
  overworld: bingo
  nether: bingo_nether
  the-end: bingo_the_end
  allow-reuse-without-reset: false
```

| 配置 | 含义 |
| --- | --- |
| `worker-id` | 与 Core `bingo.worker-id` 一致；不同实例使用不同 ID |
| `redis.uri` / `redis.namespace` | 与 Core 指向同一 Redis 和命名空间 |
| `redis.stream-max-length` | Streams 近似保留上限 |
| `redis.block-timeout-ms` | 阻塞读取等待时间 |
| `redis.reclaim-idle-ms` / `redis.max-deliveries` | pending 接管与 DLQ 投递阈值 |
| `proxy.return-server` | 代理注册的 Core 服务名，需替换生成模板中的默认值 |
| `worlds.allow-reuse-without-reset` | 正式比赛保持 `false`；`true` 仅供允许复用世界的开发场景 |
| `worlds.seed-filter.*` | 内嵌 SeedLab 群系筛选，不需要外部程序或网络服务 |

SeedLab 默认半径 2000 格、采样步长 32 格、最多 128 个候选、时间窗 14000 毫秒；实际完成数量取决于资源预算。筛选仅在插件发现需要创建新世界时运行，不会改写已由 Folia 启动加载的世界 seed；Folia 部署应在外部世界准备流程中明确选择 seed。在该创建路径中预测器不可用时，`required: false` 可回退随机 seed，`required: true` 拒绝启动。许可见 [第三方声明](THIRD-PARTY-NOTICES.md)。

任务、计分、倒计时、PvP、效果、语言和 `scoreboards.yml` 模板由 Core 冻结到 manifest。修改 Core 配置影响新比赛，当前比赛继续使用已有快照。

## 世界重置接入

Worker 每个进程只承载一个活动比赛。`worlds.allow-reuse-without-reset: false` 时，比赛结束后先将玩家送回 Core；全部玩家离开后写入世界容器目录下的 `.championships-bingo-reset`，随后关闭 Folia。

标记采用 UTF-8 三行格式：

```text
1
<主世界目录名>
<主世界目录名>.cc-reset-<UUID>
```

部署方的监督进程应在 Java 完全退出、世界锁释放后读取标记，校验目录均为世界容器内的直接子目录，再把旧主世界目录移到第三行指定的退役目录，处理标记并重新启动 Folia。新 Worker 会异步清理同名 `.cc-reset-` 退役目录。三维度不在同一主世界根目录时，重置协调器会拒绝该布局。

仓库不提供监督脚本。普通自动重启若不处理标记，会重复加载旧世界；仅安装 Worker JAR 不构成完整的世界重置部署。新世界的创建、seed 筛选与预生成应纳入外部启动流程，并在接受下一场比赛前完成。

## 首次联调

1. 在代理注册 Core 和 Worker 服务名，禁止玩家直接选择 Worker，设置连接失败回退 Core。
2. 核对 Redis、世界名和重置流程，确认启动日志无错误。
3. Core 先保持 `LOCAL`；空闲时按 [跨服架构](../docs/bingo-remote-architecture.md) 配置 `REMOTE`。
4. 用测试队伍走通准备、转服、倒计时、任务、结算和返回。
5. 核对卡片、积分、队伍颜色、完整 Tab、旁观、侧栏、普通聊天和 `/teammsg`。
6. 确认停服、移走旧世界、重启和第二局均成功，再进行 [容量验证](../docs/bingo-64-player-performance-report.md)。

## 排障

| 现象 | 检查项 |
| --- | --- |
| Core 等不到 `READY` | `enabled`、worker ID、Redis 命名空间、pending/DLQ、世界加载 |
| 玩家未转服或比赛未开始 | 代理服务名、channel、manifest 名册、`PLAYER_ARRIVED` 和到达超时 |
| 任务不计数或重复 | objective 日志、event/completion 序列、Worker outbox 与 Core inbox |
| 两端展示不一致 | 构建版本、重启状态、manifest、共享平台层 |
| 跨服聊天缺失 | Core `instance-id`、Worker `worker-id`、Redis consumer group |
| 结束后无法返回 | `proxy.return-server`、Core 可用性、代理 channel |
| TPS 降低 | 各 Folia region 的 MSPT、实体和区块分布，磁盘与新区块生成 |
| 第二局拒绝或复用旧世界 | 重置标记、监督进程、世界布局与脏世界保护 |

Redis 故障时保留本地 outbox，恢复后按序重投。公共聊天只保留实时窗口。共享实现、线程约束和升级要求见 [开发指南](../docs/development.md) 与 [旁观契约](../docs/spectator-visibility-contract.md)。替换 JAR 后重启；共享代码变化时 Core 与 Worker 成对升级。
